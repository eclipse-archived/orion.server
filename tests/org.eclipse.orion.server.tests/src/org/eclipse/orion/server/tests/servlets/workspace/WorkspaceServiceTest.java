/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
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
import static org.junit.Assert.assertTrue;

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
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.internal.server.servlets.workspace.WorkspaceServlet;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.users.UserConstants2;
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
	protected final List<IFileStore> toDelete = new ArrayList<IFileStore>();

	protected WebRequest getCopyMoveProjectRequest(URI workspaceLocationURI, String projectName, String sourceLocation, boolean isMove) throws UnsupportedEncodingException {
		workspaceLocation = addSchemeHostPort(workspaceLocationURI);
		JSONObject requestObject = new JSONObject();
		try {
			requestObject.put(ProtocolConstants.KEY_LOCATION, sourceLocation);
		} catch (JSONException e) {
			//should never happen
			Assert.fail("Invalid source location: " + sourceLocation);
		}
		InputStream source = IOUtilities.toInputStream(requestObject.toString());
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

	@Before
	public void setUp() throws CoreException {
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
	public void testCreateProject() throws Exception {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//create a project
		String projectName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String locationHeader = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(locationHeader);

		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		//ensure project location = <workspace location>/project/<projectName>
		IPath fullPath = new Path(locationHeader);
		assertEquals(4, fullPath.segmentCount());
		assertEquals("workspace", fullPath.segment(0));
		String workspaceId = fullPath.segment(1);
		URI projectLocation = new URI(toAbsoluteURI(locationHeader));
		URI relative = workspaceLocation.relativize(projectLocation);
		IPath projectPath = new Path(relative.getPath());
		assertEquals(2, projectPath.segmentCount());
		assertEquals("project", projectPath.segment(0));
		assertEquals(projectName, projectPath.segment(1));

		//ensure project appears in the workspace metadata
		request = new GetMethodWebRequest(addSchemeHostPort(workspaceLocation).toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertNotNull(workspace);
		JSONArray projects = workspace.getJSONArray(ProtocolConstants.KEY_PROJECTS);
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
		assertEquals("true", child.optString(ProtocolConstants.KEY_DIRECTORY));
		String contentLocation = child.optString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(contentLocation);

		//ensure project content exists
		testProjectBaseLocation = "/" + workspaceId + '/' + projectName;
		IFileStore projectStore = EFS.getStore(makeLocalPathAbsolute(""));
		assertTrue(projectStore.fetchInfo().exists());

		//add a file in the project
		String fileName = "file.txt";
		request = getPostFilesRequest(toAbsoluteURI(contentLocation), getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		assertEquals("Response should contain file metadata in JSON, but was " + response.getText(), "application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", responseObject);
		checkFileMetadata(responseObject, fileName, null, null, null, null, null, null, null, projectName);
	}

	@Test
	public void testMoveBadRequest() throws IOException, SAXException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//request a bogus move
		String projectName = "My Project";
		WebRequest request = getCopyMoveProjectRequest(workspaceLocation, projectName, "badsource", true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());
	}

	@Test
	public void testMoveFolderToProject() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//create a project
		String sourceName = "Source Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, sourceName, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(sourceContentLocation);

		//add a folder and file in the project
		final String dirName = "someDir";
		request = getPostFilesRequest(sourceContentLocation, getNewDirJSON(dirName).toString(), dirName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String folderLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		String fileName = "file.txt";
		request = getPostFilesRequest(folderLocation, getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		// move the folder to a project
		String destinationName = "Destination Project";
		request = getCopyMoveProjectRequest(workspaceLocation, destinationName, folderLocation, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		responseObject = new JSONObject(response.getText());
		String destinationContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);

		//assert the move took effect
		assertFalse(sourceLocation.equals(destinationLocation));
		assertEquals(destinationName, responseObject.getString(ProtocolConstants.KEY_NAME));

		//source project should still exist
		response = webConversation.getResponse(getGetRequest(sourceLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		//ensure the source folder is gone
		response = webConversation.getResponse(getGetRequest(folderLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		//ensure the destination is intact
		response = webConversation.getResponse(getGetRequest(destinationContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals(destinationName, responseObject.getString(ProtocolConstants.KEY_NAME));
		JSONArray children = responseObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, children.length());
		JSONObject child = children.getJSONObject(0);
		assertEquals(fileName, child.getString(ProtocolConstants.KEY_NAME));
	}

	@Test
	public void testMoveProject() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//create a project
		String projectName = "Source Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
		WebResponse response = webConversation.getResponse(request);
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
		assertEquals(destinationName, resultObject.getString(ProtocolConstants.KEY_NAME));
	}

	@Test
	public void testMoveProjectToFolder() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//create a source project
		String projectName = "Source Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

		// create a destination project
		String destinationName = "Destination Project";
		request = getCreateProjectRequest(workspaceLocation, destinationName, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

		// Current location path: /workspace/{workspace-name}/project/{project-name}/
		// Need to change it to: /file/{workspace-name}/{project-name}/
		Path sourcePath = new Path(sourceLocation);
		sourcePath = (Path) new Path("/file/").append(sourcePath.segment(1)).append(sourcePath.segment(3));
		Path destinationPath = new Path(destinationLocation);
		destinationPath = (Path) new Path("/file/").append(destinationPath.segment(1)).append(destinationPath.segment(3));

		// Final path should be: {destination}/{source-name}
		Path expectedPath = (Path) destinationPath.append(sourcePath.segment(2));

		// move source project inside destination project
		JSONObject requestObject = new JSONObject();
		requestObject.put("Location", sourcePath.toString());
		requestObject.put("Name", projectName);
		request = getPostFilesRequest(destinationPath.toString(), requestObject.toString(), projectName);
		request.setHeaderField(ProtocolConstants.HEADER_CREATE_OPTIONS, "move");
		response = webConversation.getResponse(request);

		//assert the move took effect
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertEquals(destinationLocation, expectedPath.toString());

		// Assert the project has been removed from the workspace
		request = new GetMethodWebRequest(addSchemeHostPort(workspaceLocation).toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertNotNull(workspace);
		JSONArray projects = workspace.getJSONArray(ProtocolConstants.KEY_PROJECTS);
		assertEquals(1, projects.length());
		JSONObject project = projects.getJSONObject(0);
		assertEquals(destinationName, project.get("Id"));
	}

	@Test
	public void testCopyProjectNonDefaultLocation() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String tmp = System.getProperty("java.io.tmpdir");
		File projectLocation = new File(new File(tmp), "Orion-testCopyProjectNonDefaultLocation");
		projectLocation.mkdir();
		toDelete.add(EFS.getLocalFileSystem().getStore(projectLocation.toURI()));
		ServletTestingSupport.allowedPrefixes = projectLocation.toString();

		//create a project
		String sourceName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, sourceName, projectLocation.toString());
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(sourceContentLocation);

		//add a file in the project
		String fileName = "file.txt";
		request = getPostFilesRequest(toAbsoluteURI(sourceContentLocation), "{}", fileName);
		response = webConversation.getResource(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		assertEquals("Response should contain file metadata in JSON, but was " + response.getText(), "application/json", response.getContentType());
		JSONObject fileResponseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", fileResponseObject);
		checkFileMetadata(fileResponseObject, fileName, null, null, null, null, null, null, null, sourceName);

		// copy the project
		String destinationName = "Destination Project";
		request = getCopyMoveProjectRequest(workspaceLocation, destinationName, sourceLocation, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		String destinationContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);

		//assert the copy took effect
		assertFalse(sourceLocation.equals(destinationLocation));
		JSONObject resultObject = new JSONObject(response.getText());
		assertEquals(destinationName, resultObject.getString(ProtocolConstants.KEY_NAME));

		//ensure the source is still intact
		response = webConversation.getResponse(getGetRequest(sourceContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		resultObject = new JSONObject(response.getText());
		assertEquals(sourceName, resultObject.getString(ProtocolConstants.KEY_NAME));
		JSONArray children = resultObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, children.length());
		JSONObject child = children.getJSONObject(0);
		assertEquals(fileName, child.getString(ProtocolConstants.KEY_NAME));

		//ensure the destination is intact
		response = webConversation.getResponse(getGetRequest(destinationContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		resultObject = new JSONObject(response.getText());
		assertEquals(sourceName, resultObject.getString(ProtocolConstants.KEY_NAME));
		children = resultObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, children.length());
		child = children.getJSONObject(0);
		assertEquals(fileName, child.getString(ProtocolConstants.KEY_NAME));
	}

	@Test
	public void testCopyFolderToProject() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//create a project
		String sourceName = "Source Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, sourceName, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(sourceContentLocation);

		//add a folder and file in the project
		final String dirName = "someDir";
		request = getPostFilesRequest(sourceContentLocation, getNewDirJSON(dirName).toString(), dirName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String folderLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		String fileName = "file.txt";
		request = getPostFilesRequest(folderLocation, getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		// copy the folder to a project
		String destinationName = "Destination Project";
		request = getCopyMoveProjectRequest(workspaceLocation, destinationName, folderLocation, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		responseObject = new JSONObject(response.getText());
		String destinationContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);

		//assert the copy took effect
		assertFalse(sourceLocation.equals(destinationLocation));
		assertEquals(destinationName, responseObject.getString(ProtocolConstants.KEY_NAME));

		//ensure the source is still intact
		response = webConversation.getResponse(getGetRequest(sourceContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals(sourceName, responseObject.getString(ProtocolConstants.KEY_NAME));
		JSONArray children = responseObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, children.length());
		JSONObject child = children.getJSONObject(0);
		assertEquals(dirName, child.getString(ProtocolConstants.KEY_NAME));

		//ensure the destination is intact
		response = webConversation.getResponse(getGetRequest(destinationContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals(destinationName, responseObject.getString(ProtocolConstants.KEY_NAME));
		children = responseObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, children.length());
		child = children.getJSONObject(0);
		assertEquals(fileName, child.getString(ProtocolConstants.KEY_NAME));
	}

	@Test
	public void testCopyProject() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//create a project
		String sourceName = "Source Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, sourceName, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(sourceContentLocation);

		//add a file in the project
		String fileName = "file.txt";
		request = getPostFilesRequest(toAbsoluteURI(sourceContentLocation), "{}", fileName);
		response = webConversation.getResource(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		// copy the project
		String destinationName = "Destination Project";
		request = getCopyMoveProjectRequest(workspaceLocation, destinationName, sourceLocation, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String destinationLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		String destinationContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);

		//assert the copy took effect
		assertFalse(sourceLocation.equals(destinationLocation));
		JSONObject resultObject = new JSONObject(response.getText());
		assertEquals(destinationName, resultObject.getString(ProtocolConstants.KEY_NAME));

		//ensure the source is still intact
		response = webConversation.getResponse(getGetRequest(sourceContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		resultObject = new JSONObject(response.getText());
		assertEquals(sourceName, resultObject.getString(ProtocolConstants.KEY_NAME));
		JSONArray children = resultObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, children.length());
		JSONObject child = children.getJSONObject(0);
		assertEquals(fileName, child.getString(ProtocolConstants.KEY_NAME));

		//ensure the destination is intact
		response = webConversation.getResponse(getGetRequest(destinationContentLocation + "?depth=1"));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		resultObject = new JSONObject(response.getText());
		assertEquals(sourceName, resultObject.getString(ProtocolConstants.KEY_NAME));
		children = resultObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, children.length());
		child = children.getJSONObject(0);
		assertEquals(fileName, child.getString(ProtocolConstants.KEY_NAME));
	}

	@Test
	public void testCreateProjectBadName() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//check a variety of bad project names
		for (String badName : Arrays.asList("", " ", "/")) {
			//create a project
			WebRequest request = getCreateProjectRequest(workspaceLocation, badName, null);
			WebResponse response = webConversation.getResponse(request);
			assertEquals("Shouldn't allow name: " + badName, HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
		}
	}

	/**
	 * Tests creating a project that is stored at a non-default location on the server.
	 */
	@Test
	public void testCreateProjectNonDefaultLocation() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String tmp = System.getProperty("java.io.tmpdir");
		File projectLocation = new File(new File(tmp), "Orion-testCreateProjectNonDefaultLocation");
		toDelete.add(EFS.getLocalFileSystem().getStore(projectLocation.toURI()));
		projectLocation.mkdir();

		//at first forbid all project locations
		ServletTestingSupport.allowedPrefixes = null;

		//create a project
		String projectName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, projectLocation.toString());
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
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
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WebResponse response = basicCreateWorkspace(workspaceName);

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
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WebResponse response = basicCreateWorkspace(workspaceName);
		String locationString = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		URI workspaceLocationURI = new URI(locationString);
		JSONObject workspace = new JSONObject(response.getText());
		String workspaceId = workspace.getString(ProtocolConstants.KEY_ID);

		//get workspace metadata and ensure it is correct
		WebRequest request = getGetRequest(workspaceLocationURI.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		workspace = new JSONObject(response.getText());
		assertNotNull(workspace);
		assertEquals(locationString, workspace.optString(ProtocolConstants.KEY_LOCATION));
		assertEquals(workspaceName, workspace.optString(ProtocolConstants.KEY_NAME));
		assertEquals(workspaceId, workspace.optString(ProtocolConstants.KEY_ID));
	}

	@Test
	public void testGetProjectMetadata() throws IOException, SAXException, JSONException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//create a project
		String sourceName = "testGetProjectMetadata Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, sourceName, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String sourceLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

		//now get the project metadata
		request = getGetRequest(sourceLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertEquals(sourceName, responseObject.optString(ProtocolConstants.KEY_NAME));
		assertNotNull(sourceContentLocation);
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
	public void testDeleteProject() throws IOException, SAXException, JSONException, URISyntaxException, CoreException {
		//create workspace
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		//create a project
		String projectName = "My Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String projectLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		// update the global variables for the test
		IPath workspacePath = new Path(workspaceLocation.getPath());
		String workspaceId = new Path(workspaceLocation.getPath()).segment(workspacePath.segmentCount() - 1);
		testProjectBaseLocation = "/" + workspaceId + '/' + projectName;
		JSONObject project = new JSONObject(response.getText());
		testProjectLocalFileLocation = "/" + project.optString(ProtocolConstants.KEY_ID, null);
		String contentLocation = project.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
		IFileStore projectStore = EFS.getStore(makeLocalPathAbsolute(""));

		//add a file in the project
		String fileName = "file.txt";
		request = getPostFilesRequest(contentLocation, getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		assertEquals("Response should contain file metadata in JSON, but was " + response.getText(), "application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", responseObject);
		checkFileMetadata(responseObject, fileName, null, null, null, null, null, null, null, projectName);

		//delete project
		request = new DeleteMethodWebRequest(toAbsoluteURI(projectLocation));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		//ensure project does not appear in the workspace metadata
		request = new GetMethodWebRequest(addSchemeHostPort(workspaceLocation).toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertNotNull(workspace);
		JSONArray projects = workspace.getJSONArray(ProtocolConstants.KEY_PROJECTS);
		assertEquals(0, projects.length());

		//ensure project content is deleted
		assertFalse(projectStore.fetchInfo().exists());

		//deleting again should be safe (DELETE is idempotent)
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		//check user rights have been removed

		String testUser = getTestUserId();
		try {
			//first we need to remove the global rights assigned to test user
			AuthorizationService.removeUserRight(testUser, "/");
			AuthorizationService.removeUserRight(testUser, "/*");

			IPath path = new Path(new URI(projectLocation).getPath());
			//project location format is /workspace/<workspaceId>/project/<projectName>
			projectName = path.segment(3);
			assertFalse(AuthorizationService.checkRights(testUser, "/file/" + projectName, "PUT"));
			assertFalse(AuthorizationService.checkRights(testUser, "/file/" + projectName + "/*", "PUT"));
		} finally {
			//give test user global rights again
			AuthorizationService.addUserRight(testUser, "/");
			AuthorizationService.addUserRight(testUser, "/*");
		}
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
		assertEquals(testUserId, responseObject.optString(UserConstants2.USER_NAME));
		JSONArray workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(0, workspaces.length());

		//now create a workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		response = basicCreateWorkspace(workspaceName);
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
		assertEquals(testUserId, responseObject.optString(UserConstants2.USER_NAME));
		workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(1, workspaces.length());
		JSONObject workspace = (JSONObject) workspaces.get(0);
		assertEquals(workspaceId, workspace.optString(ProtocolConstants.KEY_ID));
		assertNotNull(workspace.optString(ProtocolConstants.KEY_LOCATION, null));
	}
}
