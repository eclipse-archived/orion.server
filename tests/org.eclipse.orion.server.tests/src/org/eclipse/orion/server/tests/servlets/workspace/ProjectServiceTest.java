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

import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;

import org.eclipse.orion.server.core.users.EclipseWebScope;

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
public class ProjectServiceTest extends FileSystemTest {
	WebConversation webConversation;

	protected WebRequest getCreateProjectRequest() {
		WebRequest request = new PostMethodWebRequest(SERVER_LOCATION + "/project/");
		request.setHeaderField("EclipseWeb-Version", "1");
		setAuthentication(request);
		return request;
	}

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	WebResponse createProject() throws IOException, SAXException {
		WebRequest request = getCreateProjectRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		return response;

	}

	@Before
	public void setUp() throws CoreException, BackingStoreException {
		clearWorkspace();
		EclipseWebScope prefs = new EclipseWebScope();
		prefs.getNode("Users").removeNode();
		prefs.getNode("Workspaces").removeNode();
		prefs.getNode("Projects").removeNode();
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
	}

	@Test
	public void testCreateProject() throws IOException, SAXException, JSONException {
		//create a project
		WebRequest request = getCreateProjectRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);
	}

	/**
	 * Tests creating a project that is stored at a non-default location on the server.
	 */
	@Test
	public void testCreateProjectNonDefaultLocation() throws IOException, SAXException, JSONException {
		String tmp = System.getProperty("java.io.tmpdir");
		File projectLocation = new File(new File(tmp), "EclipseWeb-testCreateProjectNonDefaultLocation");
		projectLocation.mkdir();

		//at first forbid all project locations
		ServletTestingSupport.allowedPrefixes = null;

		//create a project
		JSONObject body = new JSONObject();
		body.put("ContentLocation", projectLocation.toString());
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(SERVER_LOCATION + "/project/", in, "UTF-8");
		request.setHeaderField("EclipseWeb-Version", "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		//now set the allowed prefixes and try again
		ServletTestingSupport.allowedPrefixes = projectLocation.toString();
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);
	}

	/**
	 * Tests getting metadata for a project that does not exist.
	 */
	@Test
	public void testGetNonExistentProjectMetadata() throws IOException, SAXException, JSONException {
		//get project metadata and ensure it is correct
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/project/doesnotexist");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testGetProjectMetadata() throws IOException, SAXException, JSONException {
		//create project
		WebResponse response = createProject();
		String locationString = response.getHeaderField("Location");
		assertNotNull(locationString);
		URI projectLocation = new URI(locationString);
		JSONObject project = new JSONObject(response.getText());
		String workspaceId = project.getString("Id");

		//get project metadata and ensure it is correct
		WebRequest request = new GetMethodWebRequest(projectLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		project = new JSONObject(response.getText());
		assertNotNull(project);
		assertEquals(locationString, project.optString("Location"));
		assertEquals(workspaceId, project.optString("Id"));
		String childrenLocation = project.optString("ChildrenLocation", null);
		assertNotNull(childrenLocation);

		//a GET on children location should succeed
		request = new GetMethodWebRequest(childrenLocation);
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

	}

	@Test
	public void testGetProjects() throws IOException, SAXException, JSONException {
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/project/");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);

		//before creating any projects we should get an empty list
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No project information in response", responseObject);
		assertEquals("test", responseObject.optString("UserName"));
		JSONArray projects = responseObject.optJSONArray("Projects");
		assertNotNull(projects);
		assertEquals(0, projects.length());

		//now create a project
		response = createProject();
		responseObject = new JSONObject(response.getText());
		String projectId = responseObject.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		//get the project list again
		request = new GetMethodWebRequest(SERVER_LOCATION + "/project/");
		setAuthentication(request);
		response = webConversation.getResponse(request);

		//assert that the project we created is found by a subsequent GET
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		responseObject = new JSONObject(response.getText());
		assertNotNull("No project information in response", responseObject);
		assertEquals("test", responseObject.optString("UserName"));
		projects = responseObject.optJSONArray("Projects");
		assertNotNull(projects);
		assertEquals(1, projects.length());
		JSONObject project = (JSONObject) projects.get(0);
		assertEquals(projectId, project.optString("Id"));
		assertNotNull(project.optString("Location", null));
	}
}
