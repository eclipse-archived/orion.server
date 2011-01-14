/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;

import org.eclipse.orion.server.core.users.OrionScope;

import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.internal.server.servlets.workspace.WorkspaceServlet;

import com.meterware.httpunit.*;
import java.io.*;
import java.net.HttpURLConnection;
import org.apache.xerces.util.URI;
import org.eclipse.core.runtime.CoreException;
import org.json.*;
import org.junit.*;
import org.junit.Test;
import org.osgi.service.prefs.BackingStoreException;
import org.xml.sax.SAXException;

/**
 * Tests for {@link WorkspaceServlet}.
 */
public class WorkspaceServiceTest extends FileSystemTest {
	WebConversation webConversation;

	protected WebRequest getCreateWorkspaceRequest(String workspaceName) {
		WebRequest request = new PostMethodWebRequest(SERVER_LOCATION + "/workspace");
		if (workspaceName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, workspaceName);
		request.setHeaderField("EclipseWeb-Version", "1");
		setAuthentication(request);
		return request;
	}

	protected WebRequest getCreateProjectRequest(URI workspaceLocation, String projectName) {
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString());
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField("EclipseWeb-Version", "1");
		setAuthentication(request);
		return request;
	}

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	WebResponse createWorkspace(String workspaceName) throws IOException, SAXException {
		WebRequest request = getCreateWorkspaceRequest(workspaceName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return response;

	}

	@Before
	public void setUp() throws CoreException, BackingStoreException {
		clearWorkspace();
		OrionScope prefs = new OrionScope();
		prefs.getNode("Users").removeNode();
		prefs.getNode("Workspaces").removeNode();
		prefs.getNode("Projects").removeNode();
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
	}

	@Test
	public void testCreateProject() throws IOException, SAXException, JSONException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCreateProject";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField("Location"));

		//create a project
		String projectName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString("Name"));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);

		//ensure project appears in the workspace metadata
		request = new GetMethodWebRequest(workspaceLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertNotNull(workspace);
		JSONArray projects = workspace.getJSONArray("Projects");
		assertEquals(1, projects.length());
		JSONObject createdProject = projects.getJSONObject(0);
		assertEquals(projectId, createdProject.get("Id"));
		assertNotNull(createdProject.optString("Location", null));

		//check for children element to conform to structure of file API
		JSONArray children = workspace.optJSONArray("Children");
		assertNotNull(children);
		assertEquals(1, children.length());
		JSONObject child = children.getJSONObject(0);
		assertEquals(projectName, child.optString("Name"));
		assertEquals("true", child.optString("Directory"));
		String contentLocation = child.optString("Location");
		assertNotNull(contentLocation);
	}

	/**
	 * Tests creating a project that is stored at a non-default location on the server.
	 */
	@Test
	public void testCreateProjectNonDefaultLocation() throws IOException, SAXException, JSONException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCreateProject";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField("Location"));

		String tmp = System.getProperty("java.io.tmpdir");
		File projectLocation = new File(new File(tmp), "EclipseWeb-testCreateProjectNonDefaultLocation");
		projectLocation.mkdir();

		//at first forbid all project locations
		ServletTestingSupport.allowedPrefixes = null;

		//create a project
		String projectName = "My Project";
		JSONObject body = new JSONObject();
		body.put("ContentLocation", projectLocation.toString());
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField("EclipseWeb-Version", "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		//now set the allowed prefixes and try again
		ServletTestingSupport.allowedPrefixes = projectLocation.toString();
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString("Name"));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);
	}

	@Test
	public void testCreateWorkspace() throws IOException, SAXException, JSONException {
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCreateWorkspace";
		WebResponse response = createWorkspace(workspaceName);

		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String location = response.getHeaderField("Location");
		assertNotNull(location);
		assertTrue(location.startsWith(SERVER_LOCATION));
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No workspace information in response", responseObject);
		assertNotNull(responseObject.optString("Id"));
		assertEquals(workspaceName, responseObject.optString("Name"));
	}

	@Test
	public void testGetWorkspaceMetadata() throws IOException, SAXException, JSONException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCreateProject";
		WebResponse response = createWorkspace(workspaceName);
		String locationString = response.getHeaderField("Location");
		URI workspaceLocation = new URI(locationString);
		JSONObject workspace = new JSONObject(response.getText());
		String workspaceId = workspace.getString("Id");

		//get workspace metadata and ensure it is correct
		WebRequest request = new GetMethodWebRequest(workspaceLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		workspace = new JSONObject(response.getText());
		assertNotNull(workspace);
		assertEquals(locationString, workspace.optString("Location"));
		assertEquals(workspaceName, workspace.optString("Name"));
		assertEquals(workspaceId, workspace.optString("Id"));
	}

	@Test
	public void testCreateWorkspaceNullName() throws IOException, SAXException, JSONException {
		//request with null workspace name is not allowed
		WebRequest request = getCreateWorkspaceRequest(null);
		WebResponse response = webConversation.getResponse(request);

		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		//expecting a status response
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No error response", responseObject);
		assertEquals("error", responseObject.optString("severity"));
		assertNotNull(responseObject.optString("message"));
	}

	@Test
	public void testGetWorkspaces() throws IOException, SAXException, JSONException {
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace/");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);

		//before creating an workspaces we should get an empty list
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No workspace information in response", responseObject);
		String userId = responseObject.optString("Id", null);
		assertNotNull(userId);
		assertEquals("test", responseObject.optString("UserName"));
		JSONArray workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(0, workspaces.length());

		//now create a workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testGetWorkspaces";
		response = createWorkspace(workspaceName);
		responseObject = new JSONObject(response.getText());
		String workspaceId = responseObject.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(workspaceId);

		//get the workspace list again
		request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace/");
		setAuthentication(request);
		response = webConversation.getResponse(request);

		//assert that the workspace we created is found by a subsequent GET
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		responseObject = new JSONObject(response.getText());
		assertNotNull("No workspace information in response", responseObject);
		assertEquals(userId, responseObject.optString("Id"));
		assertEquals("test", responseObject.optString("UserName"));
		workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(1, workspaces.length());
		JSONObject workspace = (JSONObject) workspaces.get(0);
		assertEquals(workspaceId, workspace.optString("Id"));
		assertNotNull(workspace.optString("Location", null));
	}
}
