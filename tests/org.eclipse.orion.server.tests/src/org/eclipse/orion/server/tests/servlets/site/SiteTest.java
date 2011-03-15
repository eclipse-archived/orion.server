package org.eclipse.orion.server.tests.servlets.site;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;

import junit.framework.Assert;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.site.SiteConfigurationConstants;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Tests for the site configurations API.
 * 
 * Core test:
 * - Create (POST)
 * - Retrieve (GET)
 * - Update (PUT)
 * - Delete (DELETE)
 * 
 * Security test:
 * - User A tries to access user B's site
 */
public class SiteTest extends FileSystemTest {

	public static final String SITE_SERVLET_LOCATION = "/site" + '/';

	public static final String SERVER_LOCATION = ServerTestsActivator.getServerLocation();

	WebConversation webConversation;
	WebResponse workspaceResponse;
	JSONObject workspaceObject;

	@BeforeClass
	public static void setUpWorkspace() {
		initializeWorkspaceLocation();
	}

	@Before
	/**
	 * Before each test, create a workspace and prepare fields for use by test methods.
	 */
	public void setUp() throws CoreException, SAXException, IOException, JSONException {
		clearWorkspace();
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		workspaceResponse = createWorkspace(this.getClass().getName());
		workspaceObject = new JSONObject(workspaceResponse.getText());
	}

	@Test
	/**
	 * Create site via POST, check that the response has the parameters we expected.
	 */
	public void testCreateSite() throws IOException, SAXException, JSONException {
		final String siteName = "My great website";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		final String hostHint = "mySite";
		final String source = "/fizz";
		final String target = "/buzz";
		final JSONArray mappings = makeMappings(new String[][] {{source, target}});

		WebRequest request = getCreateSiteRequest(siteName, workspaceId, mappings, hostHint);
		WebResponse siteResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, siteResponse.getResponseCode());

		JSONObject respObject = new JSONObject(siteResponse.getText());
		JSONArray respMappings = respObject.getJSONArray(SiteConfigurationConstants.KEY_MAPPINGS);
		assertEquals(siteName, respObject.get(ProtocolConstants.KEY_NAME));
		assertEquals(workspaceId, respObject.get(SiteConfigurationConstants.KEY_WORKSPACE));
		assertEquals(hostHint, respObject.get(SiteConfigurationConstants.KEY_HOST_HINT));
		assertEquals(1, respMappings.length());
		assertEquals(source, respMappings.getJSONObject(0).get(SiteConfigurationConstants.KEY_SOURCE));
		assertEquals(target, respMappings.getJSONObject(0).get(SiteConfigurationConstants.KEY_TARGET));
	}

	@Test
	/**
	 * Attempt to create site with no name, expect 400 Bad Request
	 */
	public void testCreateSiteNoName() throws SAXException, IOException {
		final String siteName = "My great website";
		final String hostHint = "mySite";
		WebRequest request = getCreateSiteRequest(siteName, null, null, hostHint);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	/**
	 * Attempt to create site with no workspace, expect 400 Bad Request
	 */
	public void testCreateSiteNoWorkspace() throws SAXException, IOException {
		final String siteName = "My great website";
		final String hostHint = "mySite";
		WebRequest request = getCreateSiteRequest(siteName, null, null, hostHint);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	/**
	 * Create a site, then fetch its resource via a GET and check the result.
	 */
	public void testRetrieveSite() throws SAXException, JSONException, IOException {
		// Create site
		final String name = "Bob's site";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		final JSONArray mappings = makeMappings(new String[][] { {"/foo", "/A"}, {"/bar", "/B"}});
		final String hostHint = "bobhost";
		WebResponse createResp = createSite(name, workspaceId, mappings, hostHint, null);
		JSONObject site = new JSONObject(createResp.getText());
		final String location = site.getString(ProtocolConstants.HEADER_LOCATION);

		// Fetch site using its Location and ensure that what we find matches what was POSTed
		WebRequest fetchReq = getRetrieveSiteRequest(location, null);
		WebResponse fetchResp = webConversation.getResponse(fetchReq);
		assertEquals(HttpURLConnection.HTTP_OK, fetchResp.getResponseCode());
		JSONObject fetchedSite = new JSONObject(fetchResp.getText());
		assertEquals(name, fetchedSite.optString(ProtocolConstants.KEY_NAME));
		assertEquals(workspaceId, fetchedSite.optString(SiteConfigurationConstants.KEY_WORKSPACE));
		assertEquals(mappings.toString(), fetchedSite.getJSONArray(SiteConfigurationConstants.KEY_MAPPINGS).toString());
		assertEquals(hostHint, fetchedSite.optString(SiteConfigurationConstants.KEY_HOST_HINT));
	}

	@Test
	/**
	 * Create a site, then update it via PUT and check the result.
	 */
	public void testUpdateSite() throws SAXException, JSONException, IOException {
		// Create site
		final String name = "A site to update";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		final JSONArray mappings = makeMappings(new String[] {"/", "http://www.google.com"});
		final String hostHint = "orion-is-awesome";
		WebResponse createResp = createSite(name, workspaceId, mappings, hostHint, null);
		JSONObject site = new JSONObject(createResp.getText());
		final String location = site.getString(ProtocolConstants.HEADER_LOCATION);

		// Update site
		final String newName = "A site that was updated";
		final String newWorkspaceId = "" + Math.random(); // Doesn't matter since we won't start it
		final JSONArray newMappings = makeMappings(new String[] {"/some/path", "/XYZ/webRoot"});
		final String newHostHint = "orion-is-awesomer";

		WebRequest updateReq = getUpdateSiteRequest(location, newName, newWorkspaceId, newMappings, newHostHint);
		WebResponse updateResp = webConversation.getResponse(updateReq);
		assertEquals(HttpURLConnection.HTTP_OK, updateResp.getResponseCode());
		JSONObject updatedSite = new JSONObject(updateResp.getText());
		assertEquals(newName, updatedSite.optString(ProtocolConstants.KEY_NAME));
		assertEquals(newWorkspaceId, updatedSite.optString(SiteConfigurationConstants.KEY_WORKSPACE));
		assertEquals(newMappings.toString(), updatedSite.getJSONArray(SiteConfigurationConstants.KEY_MAPPINGS).toString());
		assertEquals(newHostHint, updatedSite.optString(SiteConfigurationConstants.KEY_HOST_HINT));
	}

	@Test
	/**
	 * Create a site, then delete it and make sure it's gone.
	 */
	public void testDeleteSite() throws SAXException, JSONException, IOException {
		// Create site
		final String name = "A site to delete";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		WebResponse createResp = createSite(name, workspaceId, null, null, null);
		JSONObject site = new JSONObject(createResp.getText());
		final String location = site.getString(ProtocolConstants.HEADER_LOCATION);

		// Delete site
		WebRequest deleteReq = getDeleteSiteRequest(location);
		WebResponse deleteResp = webConversation.getResponse(deleteReq);
		assertEquals(HttpURLConnection.HTTP_OK, deleteResp.getResponseCode());

		// GET should fail now
		WebRequest getReq = getRetrieveSiteRequest(location, null);
		WebResponse getResp = webConversation.getResponse(getReq);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, getResp.getResponseCode());
	}

	@Test
	/**
	 * Try to access site created by another user, verify that we can't.
	 */
	public void testDisallowedAccess() throws SAXException, JSONException, IOException {
		createUser("alice", "alice");
		createUser("bob", "bob");

		// Alice: Create site
		final String name = "A site to delete";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		WebResponse createResp = createSite(name, workspaceId, null, null, "alice");
		JSONObject site = new JSONObject(createResp.getText());
		final String location = site.getString(ProtocolConstants.HEADER_LOCATION);

		// Alice: Get site
		WebRequest getReq = getRetrieveSiteRequest(location, "alice");
		WebResponse getResp = webConversation.getResponse(getReq);
		assertEquals(HttpURLConnection.HTTP_OK, getResp.getResponseCode());

		// Bob: Attempt to get Alice's site
		getReq = getRetrieveSiteRequest(location, "bob");
		getResp = webConversation.getResponse(getReq);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, getResp.getResponseCode());
	}

	/**
	 * Creates workspace and asserts that it was created.
	 */
	WebResponse createWorkspace(String workspaceName) throws IOException, SAXException {
		WebRequest request = getCreateWorkspaceRequest(workspaceName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return response;
	}

	/**
	 * Creates a site and asserts that it was created.
	 * @param mappings Can be null
	 * @param hostHint Can be null
	 * @param user If nonnull, string to use as username and password for auth
	 */
	protected WebResponse createSite(String name, String workspaceId, JSONArray mappings, String hostHint, String user) throws IOException, SAXException {
		WebRequest request = getCreateSiteRequest(name, workspaceId, mappings, hostHint);
		if (user == null)
			setAuthentication(request);
		else
			setAuthentication(request, user, user);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		return response;
	}

	/**
	 * Turns a Java array-of-arrays {{"/foo","/A"},{"/bar","/B"}} into a mappings array 
	 * [{Source:'/foo', Target:'/A'}, {Source:'/bar', Target:'/B'}]
	 * @param mappings Array where each element is a String[] of the form {Source,Target}
	 */
	private static JSONArray makeMappings(String[]... mappings) throws JSONException {
		JSONArray result = new JSONArray();
		for (String[] mapping : mappings) {
			if (mapping.length != 2)
				throw new IllegalArgumentException("Not a valid mapping: " + mapping);

			JSONObject mappingObject = new JSONObject();
			mappingObject.put(SiteConfigurationConstants.KEY_SOURCE, mapping[0]);
			mappingObject.put(SiteConfigurationConstants.KEY_TARGET, mapping[1]);
			result.put(mappingObject);
		}
		return result;
	}

	/**
	 * Returns a request that can create a site.
	 * @param name
	 * @param workspaceId
	 * @param mappings Can be null
	 * @param hostHint Can be null
	 */
	protected WebRequest getCreateSiteRequest(String name, String workspaceId, JSONArray mappings, String hostHint) {
		try {
			String requestURI = SERVER_LOCATION + SITE_SERVLET_LOCATION;
			JSONObject json = new JSONObject();
			json.put(SiteConfigurationConstants.KEY_WORKSPACE, workspaceId);
			if (mappings != null)
				json.put(SiteConfigurationConstants.KEY_MAPPINGS, mappings);
			if (hostHint != null)
				json.put(SiteConfigurationConstants.KEY_HOST_HINT, hostHint);
			WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(json.toString()), "application/json");
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, name); // Put name in Slug
			setAuthentication(request);
			return request;
		} catch (UnsupportedEncodingException e) {
			Assert.fail(e.getMessage());
		} catch (JSONException e) {
			Assert.fail(e.getMessage());
		}
		return null;
	}

	/**
	 * @param locationUri
	 * @param user If nonnull, value to use as username and password for auth.
	 * @return Returns a request that will GET the site at the given URI.
	 */
	protected WebRequest getRetrieveSiteRequest(String locationUri, String user) {
		WebRequest request = new GetMethodWebRequest(locationUri);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		if (user == null)
			setAuthentication(request);
		else
			setAuthentication(request, user, user);
		return request;
	}

	/**
	 * Returns a request that can update a site.
	 * @param name
	 * @param workspaceId
	 * @param mappings Can be null
	 * @param hostHint Can be null
	 */
	protected WebRequest getUpdateSiteRequest(String locationUri, String name, String workspaceId, JSONArray mappings, String hostHint) {
		try {
			JSONObject json = new JSONObject();
			json.put(ProtocolConstants.KEY_NAME, name);
			json.put(SiteConfigurationConstants.KEY_WORKSPACE, workspaceId);
			if (mappings != null)
				json.put(SiteConfigurationConstants.KEY_MAPPINGS, mappings);
			if (hostHint != null)
				json.put(SiteConfigurationConstants.KEY_HOST_HINT, hostHint);
			WebRequest request = new PutMethodWebRequest(locationUri, getJsonAsStream(json.toString()), "application/json");
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			setAuthentication(request);
			return request;
		} catch (UnsupportedEncodingException e) {
			Assert.fail(e.getMessage());
		} catch (JSONException e) {
			Assert.fail(e.getMessage());
		}
		return null;
	}

	/**
	 * @param locationUri
	 * @return Returns a request that will DELETE the site at the given URI.
	 */
	protected WebRequest getDeleteSiteRequest(String locationUri) {
		WebRequest request = new DeleteMethodWebRequest(locationUri);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
