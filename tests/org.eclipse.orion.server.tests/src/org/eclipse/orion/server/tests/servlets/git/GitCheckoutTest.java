/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others
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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitCheckoutTest extends GitTest {

	// modified + checkout = clean
	@Test
	public void testCheckoutAllPaths() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/folder/folder.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = GitStatusTest.getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri, new String[] {"test.txt", "folder/folder.txt"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		GitStatusTest.assertStatusClean(statusResponse);
	}

	// modified + checkout = clean
	@Test
	@Ignore("not supported yet")
	public void testCheckoutDotPath() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/folder/folder.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = GitStatusTest.getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri, new String[] {"."});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		GitStatusTest.assertStatusClean(statusResponse);
	}

	// modified + checkout = clean
	@Test
	public void testCheckoutFolderPath() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/folder/folder.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = GitStatusTest.getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri, new String[] {"folder"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// 'test.txt' is still modified
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	public void testCheckoutEmptyPaths() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitCloneUri = gitSection.getString(GitConstants.KEY_CLONE);

		request = getCheckoutRequest(gitCloneUri, new String[] {});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCheckoutPathInUri() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = GitStatusTest.getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri + "test.txt", new String[] {});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCheckoutWrongPath() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = GitStatusTest.getCloneUri(gitStatusUri);

		// 'notthere.txt' doesn't exist
		request = getCheckoutRequest(gitCloneUri, new String[] {"notthere.txt"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// nothing has changed
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	// modified + checkout = clean
	@Test
	public void testCheckoutInFolder() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/folder/folder.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetFilesRequest(projectId + "/folder/");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject folder = new JSONObject(response.getText());

		JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		// we should get a proper clone URI here: /git/clone/file/{projectId}/
		String gitCloneUri = GitStatusTest.getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri, new String[] {"folder/folder.txt"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// 'test.txt' is still modified
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	public void testCheckoutFileOutsideCurrentFolder() throws Exception {
		// see bug 347847
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// get folder metadata
		WebRequest request = getGetFilesRequest(cloneContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject folder = new JSONObject(response.getText());
		String folderLocation = folder.getString(ProtocolConstants.KEY_LOCATION);

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(folderLocation + "test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(folderLocation + "folder/folder.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// get status for subfolder
		request = getGetFilesRequest(folderLocation + "folder/");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		folder = new JSONObject(response.getText());
		folderLocation = folder.getString(ProtocolConstants.KEY_LOCATION);

		JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = GitStatusTest.getCloneUri(gitStatusUri);

		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(2, statusArray.length());
		JSONObject testTxt = GitStatusTest.getChildByKey(statusArray, ProtocolConstants.KEY_PATH, "../test.txt");
		assertNotNull(testTxt);
		assertNotNull(GitStatusTest.getChildByName(statusArray, "test.txt"));
		assertNotNull(GitStatusTest.getChildByName(statusArray, "folder/folder.txt"));
		assertNotNull(GitStatusTest.getChildByKey(statusArray, ProtocolConstants.KEY_PATH, "folder.txt"));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		// use KEY_NAME not KEY_PATH
		// request = getCheckoutRequest(gitCloneUri, new String[] {testTxt.getString(ProtocolConstants.KEY_PATH)});
		request = getCheckoutRequest(gitCloneUri, new String[] {testTxt.getString(ProtocolConstants.KEY_NAME)});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// 'folder/folder.txt' is still modified
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertEquals("folder/folder.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	public void testCheckoutBranch() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// create branch 'a'
		branch(branchesLocation, "a");

		// checkout 'a'
		response = checkoutBranch(cloneLocation, "a");
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation);
		Git git = new Git(db1);
		GitRemoteTest.assertOnBranch(git, "a");
	}

	@Test
	public void testCheckoutEmptyBranchName() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String location = clone.getString(ProtocolConstants.KEY_LOCATION);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// checkout
		response = checkoutBranch(location, "");
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCheckoutInvalidBranchName() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String location = clone.getString(ProtocolConstants.KEY_LOCATION);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// checkout 'a', which hasn't been created
		response = checkoutBranch(location, "a");
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testCheckoutAborted() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetFilesRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());
			String folderChildrenLocation = folder.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);

			request = getGetFilesRequest(folderChildrenLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
			JSONObject testTxt = getChildByName(children, "test.txt");
			String testTxtLocation = testTxt.getString(ProtocolConstants.KEY_LOCATION);

			// create branch
			JSONObject testTxtGitSection = testTxt.getJSONObject(GitConstants.KEY_GIT);
			String cloneLocation = testTxtGitSection.getString(GitConstants.KEY_CLONE);
			request = getGetRequest(cloneLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject clones = new JSONObject(response.getText());
			JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(1, clonesArray.length());
			clone = clonesArray.getJSONObject(0);

			response = branch(clone.getString(GitConstants.KEY_BRANCH), "branch");

			// change on the branch
			request = getPutFileRequest(testTxtLocation, "master change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			request = GitAddTest.getPutGitIndexRequest(testTxtGitSection.getString(GitConstants.KEY_INDEX));
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			request = GitCommitTest.getPostGitCommitRequest(testTxtGitSection.getString(GitConstants.KEY_HEAD), "commit on master", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// local change, not committed
			request = getPutFileRequest(testTxtLocation, "working tree change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// checkout
			response = checkoutBranch(clone.getString(ProtocolConstants.KEY_LOCATION), "branch");
			assertEquals(HttpURLConnection.HTTP_CONFLICT, response.getResponseCode());
			JSONObject result = new JSONObject(response.getText());
			assertEquals(HttpURLConnection.HTTP_CONFLICT, result.getInt("HttpCode"));
			assertEquals("Error", result.getString("Severity"));
			assertEquals("Checkout aborted.", result.getString("Message"));
			assertEquals("Checkout conflict with files: \ntest.txt", result.getString("DetailedMessage"));
		}
	}

	private WebRequest getCheckoutRequest(String location, String[] paths) throws IOException, JSONException {
		String requestURI;
		if (location.startsWith("http://")) {
			// assume the caller knows what he's doing
			// assertCloneUri(location);
			requestURI = location;
		} else {
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CLONE_RESOURCE + location;
		}
		JSONObject body = new JSONObject();
		JSONArray jsonPaths = new JSONArray();
		for (String path : paths)
			jsonPaths.put(path);
		body.put(ProtocolConstants.KEY_PATH, jsonPaths);
		WebRequest request = new PutMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
