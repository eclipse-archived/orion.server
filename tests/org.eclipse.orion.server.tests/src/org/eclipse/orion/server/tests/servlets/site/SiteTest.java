/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.site;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.hosting.SiteConfigurationConstants;
import org.eclipse.orion.internal.server.hosting.SiteInfo;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Tests for the site configurations API.
 * 
 * Basic tests:
 * - Create (POST)
 * - Retrieve (GET)
 * - Update (PUT)
 * - Delete (DELETE)
 * 
 * Security tests:
 * - User A tries to access user B's site
 */
public class SiteTest extends CoreSiteTest {

	private WebResponse workspaceResponse;
	private JSONObject workspaceObject;

	@BeforeClass
	public static void setUpWorkspace() {
		initializeWorkspaceLocation();
	}

	@Before
	/**
	 * Before each test, create a workspace and prepare fields for use by test methods.
	 */
	public void setUp() throws CoreException, SAXException, IOException, JSONException {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		workspaceResponse = basicCreateWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
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
	public void testRetrieveSite() throws SAXException, JSONException, IOException, URISyntaxException {
		// Create site
		final String name = "Bob's site";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		final JSONArray mappings = makeMappings(new String[][] { {"/foo", "/A"}, {"/bar", "/B"}});
		final String hostHint = "bobhost";
		WebResponse createResp = createSite(name, workspaceId, mappings, hostHint, null);
		JSONObject site = new JSONObject(createResp.getText());

		String location = site.getString(ProtocolConstants.HEADER_LOCATION);

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
	public void testUpdateSite() throws SAXException, JSONException, IOException, URISyntaxException {
		// Create site
		final String name = "A site to update";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		final JSONArray mappings = makeMappings(new String[] {"/", "http://www.google.com"});
		final String hostHint = "orion-is-awesome";
		WebResponse createResp = createSite(name, workspaceId, mappings, hostHint, null);
		JSONObject site = new JSONObject(createResp.getText());
		String location = site.getString(ProtocolConstants.HEADER_LOCATION);

		// Update site
		final String newName = "A site that was updated";
		final String newWorkspaceId = "" + Math.random(); // Doesn't matter since we won't start it
		final JSONArray newMappings = makeMappings(new String[] {"/some/path", "/XYZ/webRoot"});
		final String newHostHint = "orion-is-awesomer";

		WebRequest updateReq = getUpdateSiteRequest(location, newName, newWorkspaceId, newMappings, newHostHint, null);
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
	public void testDeleteSite() throws SAXException, JSONException, IOException, URISyntaxException, CoreException {
		// Create site
		final String name = "A site to delete";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		WebResponse createResp = createSite(name, workspaceId, null, null, null);
		JSONObject site = new JSONObject(createResp.getText());
		final String siteId = site.getString(ProtocolConstants.KEY_ID);
		String location = site.getString(ProtocolConstants.HEADER_LOCATION);

		UserInfo user = OrionConfiguration.getMetaStore().readUser(testUserId);
		SiteInfo siteInfo = SiteInfo.getSite(user, siteId);
		assertNotNull(siteInfo);

		// Delete site
		WebRequest deleteReq = getDeleteSiteRequest(location);
		WebResponse deleteResp = webConversation.getResponse(deleteReq);
		assertEquals(HttpURLConnection.HTTP_OK, deleteResp.getResponseCode());

		// GET should fail now
		WebRequest getReq = getRetrieveSiteRequest(location, null);
		WebResponse getResp = webConversation.getResponse(getReq);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, getResp.getResponseCode());

		user = OrionConfiguration.getMetaStore().readUser(testUserId);
		siteInfo = SiteInfo.getSite(user, siteId);
		assertNull(siteInfo);

		// GET all sites should not include the deleted site
		WebRequest getAllReq = getRetrieveAllSitesRequest(null);
		WebResponse getAllResp = webConversation.getResponse(getAllReq);
		JSONObject allSitesJson = new JSONObject(getAllResp.getText());
		JSONArray allSites = allSitesJson.getJSONArray(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS);
		for (int i = 0; i < allSites.length(); i++) {
			assertEquals(false, allSites.getJSONObject(i).getString(ProtocolConstants.KEY_ID).equals(siteId));
		}
	}

	@Test
	/**
	 * Try to access site created by another user, verify that we can't.
	 */
	public void testDisallowedAccess() throws SAXException, JSONException, IOException, URISyntaxException {
		createUser("alice", "alice");
		createUser("bob", "bob");

		// Alice: Create site
		final String name = "A site to delete";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		WebResponse createResp = createSite(name, workspaceId, null, null, "alice");
		JSONObject site = new JSONObject(createResp.getText());
		String location = site.getString(ProtocolConstants.HEADER_LOCATION);

		// Alice: Get site
		WebRequest getReq = getRetrieveSiteRequest(location, "alice");
		WebResponse getResp = webConversation.getResponse(getReq);
		assertEquals(HttpURLConnection.HTTP_OK, getResp.getResponseCode());

		// Bob: Attempt to get Alice's site
		getReq = getRetrieveSiteRequest(location, "bob");
		getResp = webConversation.getResponse(getReq);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, getResp.getResponseCode());
	}

}
