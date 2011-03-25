package org.eclipse.orion.server.tests.servlets.site;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;

import junit.framework.Assert;

import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.site.SiteConfigurationConstants;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Abstract base class for site and hosting tests.
 */
public abstract class CoreSiteTest extends FileSystemTest {

	public static final String SITE_SERVLET_LOCATION = "/site" + '/';
	public static final String SERVER_LOCATION = ServerTestsActivator.getServerLocation();

	WebConversation webConversation;

	/**
	 * Turns a Java array-of-arrays {{"/foo","/A"},{"/bar","/B"}} into a mappings array 
	 * [{Source:"/foo", Target:"/A"}, {Source:"/bar", Target:"/B"}]
	 * @param mappings Array where each element is a String[] of the form {Source,Target}
	 */
	protected static JSONArray makeMappings(String[]... mappings) throws JSONException {
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
	 * Creates workspace and asserts that it was created.
	 */
	WebResponse createWorkspace(String workspaceName) throws IOException, SAXException {
		WebRequest request = getCreateWorkspaceRequest(workspaceName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return response;
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
	 * @return Returns a request that will DELETE the site at the given URI.
	 */
	protected WebRequest getDeleteSiteRequest(String locationUri) {
		WebRequest request = new DeleteMethodWebRequest(locationUri);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
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
	 * @param hostingStatus Can be null
	 */
	protected WebRequest getUpdateSiteRequest(String locationUri, String name, String workspaceId, JSONArray mappings, String hostHint, JSONObject hostingStatus) {
		try {
			JSONObject json = new JSONObject();
			json.put(ProtocolConstants.KEY_NAME, name);
			json.put(SiteConfigurationConstants.KEY_WORKSPACE, workspaceId);
			json.putOpt(SiteConfigurationConstants.KEY_MAPPINGS, mappings);
			json.putOpt(SiteConfigurationConstants.KEY_HOST_HINT, hostHint);
			json.putOpt(SiteConfigurationConstants.KEY_HOSTING_STATUS, hostingStatus);
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
}
