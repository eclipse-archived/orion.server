/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.internal.server.servlets.workspace.WorkspaceServlet;
import org.eclipse.orion.server.core.users.OrionScope;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.service.prefs.BackingStoreException;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Tests for {@link WorkspaceServlet}.
 */
public class WorkspaceServiceTest extends FileSystemTest {
	WebConversation webConversation;

	protected final List<IFileStore> toDelete = new ArrayList<IFileStore>();

	protected WebRequest getCreateProjectRequest(URI workspaceLocation, String projectName, String projectLocation) throws JSONException, IOException {
		workspaceLocation = addSchemeHostPort(workspaceLocation);
		JSONObject body = new JSONObject();
		if (projectLocation != null)
			body.put(ProtocolConstants.KEY_CONTENT_LOCATION, projectLocation);
		InputStream in = getJsonAsStream(body.toString());
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private URI addSchemeHostPort(URI uri) {
		String scheme = uri.getScheme();
		String host = uri.getHost();
		int port = uri.getPort();
		if (scheme == null) {
			scheme = "http";
		}
		if (host == null) {
			host = "localhost";
		}
		if (port == -1) {
			port = 8080;
		}
		try {
			return new URI(scheme, uri.getUserInfo(), host, port, uri.getPath(), uri.getQuery(), uri.getFragment());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	protected WebRequest getCopyMoveProjectRequest(URI workspaceLocation, String projectName, String sourceLocation, boolean isMove) throws UnsupportedEncodingException {
		workspaceLocation = addSchemeHostPort(workspaceLocation);
		JSONObject requestObject = new JSONObject();
		try {
			requestObject.put("Location", sourceLocation);
		} catch (JSONException e) {
			//should never happen
			Assert.fail("Invalid source location: " + sourceLocation);
		}
		InputStream source = getJsonAsStream(requestObject.toString());
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), source, "application/json");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		request.setHeaderField(ProtocolConstants.HEADER_CREATE_OPTIONS, isMove ? "move" : "copy");
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
		toDelete.clear();
	}

	@After
	public void tearDown() {
		for (IFileStore file : toDelete) {
			try {
				file.delete(EFS.NONE, null);
			} catch (CoreException e) {
				//skip
			}
		}
		toDelete.clear();
	}

	@Test
	public void testCreateProject() throws IOException, SAXException, JSONException, URISyntaxException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCreateProject";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		//create a project
		String projectName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String locationHeader = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(locationHeader);

		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		//ensure project location = <workspace location>/project/<projectId>
		URI projectLocation = new URI(locationHeader);
		URI relative = workspaceLocation.relativize(projectLocation);
		IPath projectPath = new Path(relative.getPath());
		assertEquals(2, projectPath.segmentCount());
		assertEquals("project", projectPath.segment(0));
		assertEquals(projectId, projectPath.segment(1));

		//ensure project appears in the workspace metadata
		request = new GetMethodWebRequest(addSchemeHostPort(workspaceLocation).toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertNotNull(workspace);
		JSONArray projects = workspace.getJSONArray("Projects");
		assertEquals(1, projects.length());
		JSONObject createdProject = projects.getJSONObject(0);
		assertEquals(projectId, createdProject.get(ProtocolConstants.KEY_ID));
		assertNotNull(createdProject.optString(ProtocolConstants.KEY_LOCATION, null));

		//check for children element to conform to structure of file API
		JSONArray children = workspace.optJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertNotNull(children);
		assertEquals(1, children.length());
		JSONObject child = children.getJSONObject(0);
		assertEquals(projectName, child.optString(ProtocolConstants.KEY_NAME));
		assertEquals("true", child.optString("Directory"));
		String contentLocation = child.optString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(contentLocation);
	}

	@Test
	public void testMoveBadRequest() throws IOException, SAXException, URISyntaxException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testMoveProject";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		//request a bogus move
		String projectName = "My Project";
		WebRequest request = getCopyMoveProjectRequest(workspaceLocation, projectName, "badsource", true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

	}

	@Test
	public void testMoveProject() throws IOException, SAXException, URISyntaxException, JSONException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testMoveProject";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		//create a project
		String projectName = "Source Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

		// move the project
		String destinationName = "Destination Project";
		request = getCopyMoveProjectRequest(workspaceLocation, destinationName, sourceLocation, true);
		response = webConversation.getResponse(request);
		//since project already existed, we should get OK rather than CREATED
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

		//assert the move (rename) took effect
		assertEquals(sourceLocation, destinationLocation);
		JSONObject resultObject = new JSONObject(response.getText());
		assertEquals(destinationName, resultObject.getString("Name"));
	}

	@Test
	public void testCopyProjectNonDefaultLocation() throws IOException, SAXException, URISyntaxException, JSONException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCopyProjectNonDefaultLocation";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		String tmp = System.getProperty("java.io.tmpdir");
		File projectLocation = new File(new File(tmp), "Orion-testCopyProjectNonDefaultLocation");
		projectLocation.mkdir();
		toDelete.add(EFS.getLocalFileSystem().getStore(projectLocation.toURI()));
		ServletTestingSupport.allowedPrefixes = projectLocation.toString();

		//create a project
		String sourceName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, sourceName, projectLocation.toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString("ContentLocation");
		assertNotNull(sourceContentLocation);

		//add a file in the project
		String fileName = "file.txt";
		request = getPostFilesRequest(sourceContentLocation, "{}", fileName);
		response = webConversation.getResource(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		// copy the project
		String destinationName = "Destination Project";
		request = getCopyMoveProjectRequest(workspaceLocation, destinationName, sourceLocation, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		String destinationContentLocation = responseObject.optString("ContentLocation");

		//assert the copy took effect
		assertFalse(sourceLocation.equals(destinationLocation));
		JSONObject resultObject = new JSONObject(response.getText());
		assertEquals(destinationName, resultObject.getString("Name"));

		//ensure the source is still intact
		response = webConversation.getResponse(getGetFilesRequest(sourceContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		resultObject = new JSONObject(response.getText());
		assertEquals(sourceName, resultObject.getString("Name"));
		JSONArray children = resultObject.getJSONArray("Children");
		assertEquals(1, children.length());
		JSONObject child = children.getJSONObject(0);
		assertEquals(fileName, child.getString("Name"));

		//ensure the destination is intact
		response = webConversation.getResponse(getGetFilesRequest(destinationContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		resultObject = new JSONObject(response.getText());
		assertEquals(sourceName, resultObject.getString("Name"));
		children = resultObject.getJSONArray("Children");
		assertEquals(1, children.length());
		child = children.getJSONObject(0);
		assertEquals(fileName, child.getString("Name"));
	}

	@Test
	public void testCopyProject() throws IOException, SAXException, URISyntaxException, JSONException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCopyProject";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		//create a project
		String sourceName = "Source Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, sourceName, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString("ContentLocation");
		assertNotNull(sourceContentLocation);

		//add a file in the project
		String fileName = "file.txt";
		request = getPostFilesRequest(sourceContentLocation, "{}", fileName);
		response = webConversation.getResource(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		// copy the project
		String destinationName = "Destination Project";
		request = getCopyMoveProjectRequest(workspaceLocation, destinationName, sourceLocation, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		String destinationContentLocation = responseObject.optString("ContentLocation");

		//assert the copy took effect
		assertFalse(sourceLocation.equals(destinationLocation));
		JSONObject resultObject = new JSONObject(response.getText());
		assertEquals(destinationName, resultObject.getString("Name"));

		//ensure the source is still intact
		response = webConversation.getResponse(getGetFilesRequest(sourceContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		resultObject = new JSONObject(response.getText());
		assertEquals(sourceName, resultObject.getString("Name"));
		JSONArray children = resultObject.getJSONArray("Children");
		assertEquals(1, children.length());
		JSONObject child = children.getJSONObject(0);
		assertEquals(fileName, child.getString("Name"));

		//ensure the destination is intact
		response = webConversation.getResponse(getGetFilesRequest(destinationContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		resultObject = new JSONObject(response.getText());
		assertEquals(sourceName, resultObject.getString("Name"));
		children = resultObject.getJSONArray("Children");
		assertEquals(1, children.length());
		child = children.getJSONObject(0);
		assertEquals(fileName, child.getString("Name"));
	}

	@Test
	public void testCreateProjectBadName() throws IOException, SAXException, URISyntaxException, JSONException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCreateProjectBadName";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		//check a variety of bad project names
		for (String badName : Arrays.asList("", " ", "/")) {
			//create a project
			WebRequest request = getCreateProjectRequest(workspaceLocation, badName, null);
			response = webConversation.getResponse(request);
			assertEquals("Shouldn't allow name: " + badName, HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
		}
	}

	/**
	 * Tests creating a project that is stored at a non-default location on the server.
	 */
	@Test
	public void testCreateProjectNonDefaultLocation() throws IOException, SAXException, JSONException, URISyntaxException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCreateProjectNonDefaultLocation";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		String tmp = System.getProperty("java.io.tmpdir");
		File projectLocation = new File(new File(tmp), "Orion-testCreateProjectNonDefaultLocation");
		toDelete.add(EFS.getLocalFileSystem().getStore(projectLocation.toURI()));
		projectLocation.mkdir();

		//at first forbid all project locations
		ServletTestingSupport.allowedPrefixes = null;

		//create a project
		String projectName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, projectLocation.toString());
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		//now set the allowed prefixes and try again
		ServletTestingSupport.allowedPrefixes = projectLocation.toString();
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);
	}

	@Test
	public void testCreateWorkspace() throws IOException, SAXException, JSONException {
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testCreateWorkspace";
		WebResponse response = createWorkspace(workspaceName);

		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(location);
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No workspace information in response", responseObject);
		assertNotNull(responseObject.optString(ProtocolConstants.KEY_ID));
		assertEquals(workspaceName, responseObject.optString(ProtocolConstants.KEY_NAME));
	}

	@Test
	public void testGetWorkspaceMetadata() throws IOException, SAXException, JSONException, URISyntaxException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testGetWorkspaceMetadata";
		WebResponse response = createWorkspace(workspaceName);
		String locationString = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		URI workspaceLocation = new URI(locationString);
		JSONObject workspace = new JSONObject(response.getText());
		String workspaceId = workspace.getString(ProtocolConstants.KEY_ID);

		//get workspace metadata and ensure it is correct
		WebRequest request = new GetMethodWebRequest(addSchemeHostPort(workspaceLocation).toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		workspace = new JSONObject(response.getText());
		assertNotNull(workspace);
		assertEquals(locationString, workspace.optString(ProtocolConstants.KEY_LOCATION));
		assertEquals(workspaceName, workspace.optString(ProtocolConstants.KEY_NAME));
		assertEquals(workspaceId, workspace.optString(ProtocolConstants.KEY_ID));
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
		assertEquals("Error", responseObject.optString("Severity"));
		assertNotNull(responseObject.optString("message"));
	}

	@Test
	public void testDeleteProject() throws IOException, SAXException, JSONException, URISyntaxException {
		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#testDeleteProject";
		WebResponse response = createWorkspace(workspaceName);
		URI workspaceLocation = new URI(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		//create a project
		String projectName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String projectLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

		//delete project
		request = new DeleteMethodWebRequest(makeAbsolute(projectLocation));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		//deleting again should be safe (DELETE is idempotent)
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testGetWorkspaces() throws IOException, SAXException, JSONException {
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);

		//before creating an workspaces we should get an empty list
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No workspace information in response", responseObject);
		String userId = responseObject.optString(ProtocolConstants.KEY_ID, null);
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
		request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace");
		setAuthentication(request);
		response = webConversation.getResponse(request);

		//assert that the workspace we created is found by a subsequent GET
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		responseObject = new JSONObject(response.getText());
		assertNotNull("No workspace information in response", responseObject);
		assertEquals(userId, responseObject.optString(ProtocolConstants.KEY_ID));
		assertEquals("test", responseObject.optString("UserName"));
		workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(1, workspaces.length());
		JSONObject workspace = (JSONObject) workspaces.get(0);
		assertEquals(workspaceId, workspace.optString(ProtocolConstants.KEY_ID));
		assertNotNull(workspace.optString(ProtocolConstants.KEY_LOCATION, null));
	}
}
