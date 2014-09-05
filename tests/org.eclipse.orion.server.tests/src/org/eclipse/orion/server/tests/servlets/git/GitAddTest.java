/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitAddTest extends GitTest {

	// modified + add = changed
	@Test
	public void testAddChanged() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		addFile(testTxt);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(new StatusResult().setChanged(1), gitStatusUri);
	}

	// missing + add = removed
	@Test
	public void testAddMissing() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject testTxt = getChild(project, "test.txt");

		deleteFile(testTxt);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(new StatusResult().setMissingNames("test.txt"), gitStatusUri);

		addFile(testTxt);

		assertStatus(new StatusResult().setRemoved(1), gitStatusUri);
	}

	@Test
	public void testAddAll() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		String fileName = "new.txt";
		WebRequest request = getPostFilesRequest("", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject folder = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder, "folder.txt");
		deleteFile(folderTxt);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(new StatusResult().setMissing(1).setModified(1).setUntracked(1), gitStatusUri);

		request = getPutGitIndexRequest(gitIndexUri /* add all */, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setAdded(1).setChanged(1).setRemoved(1), gitStatusUri);
	}

	@Test
	public void testAddFolder() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject folder = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder, "folder.txt");
		modifyFile(folderTxt, "hello");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(new StatusResult().setModified(2), gitStatusUri);

		addFile(folder);

		assertStatus(new StatusResult().setChanged(1).setModified(1), gitStatusUri);
	}

	@Test
	public void testAddAllWhenInFolder() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "hello");

			JSONObject folder1 = getChild(folder, "folder");
			JSONObject folder1Txt = getChild(folder1, "folder.txt");
			modifyFile(folder1Txt, "hello");

			// in folder
			JSONObject folder1GitSection = folder1.getJSONObject(GitConstants.KEY_GIT);
			String folder1GitStatusUri = folder1GitSection.getString(GitConstants.KEY_STATUS);

			assertStatus(new StatusResult().setModified(2), folder1GitStatusUri);

			clone = getCloneForGitResource(folder1);
			String gitIndexUri = clone.getString(GitConstants.KEY_INDEX);

			request = getPutGitIndexRequest(gitIndexUri /* add all*/, null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			assertStatus(new StatusResult().setChanged(2), folder1GitStatusUri);
		}
	}

	@Test
	public void testAddSelected() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), gitDir.toString());

		// get project/folder metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		request = getPostFilesRequest(project.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON("added.txt").toString(), "added.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject addedTxt = getChild(project, "added.txt");

		request = getPostFilesRequest(project.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON("untracked.txt").toString(), "untracked.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "testAddSelected");
		JSONObject folder = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder, "folder.txt");
		modifyFile(folderTxt, "testAddSelected");

		// add 2 of 4
		addFile(testTxt, addedTxt);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(new StatusResult().setChangedNames("test.txt").setModifiedNames("folder/folder.txt").setAddedNames("added.txt").setUntrackedNames("untracked.txt"), gitStatusUri);
	}

	static WebRequest getPutGitIndexRequest(String location) throws UnsupportedEncodingException, JSONException {
		return getPutGitIndexRequest(location, null);
	}

	static WebRequest getPutGitIndexRequest(String location, Set<String> patterns) throws UnsupportedEncodingException, JSONException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		if (patterns != null) {
			body.put(ProtocolConstants.KEY_PATH, patterns);
		}
		WebRequest request = new PutMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
