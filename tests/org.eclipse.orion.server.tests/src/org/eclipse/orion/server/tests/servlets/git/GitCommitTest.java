/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitCommitTest extends GitTest {

	@Test
	public void testCommit() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// modify first file and add it to index
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change to commit");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modify second file and add it to index
		request = getPutFileRequest(projectId + "/folder/folder.txt", "change to commit");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "folder/folder.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(2, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		// TODO: don't create URIs out of thin air
		// "git commit -m 'message' -- test.txt
		final String commitMessage = "message";
		request = getPostGitCommitRequest(gitHeadUri + "test.txt", commitMessage, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check if response contains most important parts and if commit
		// message is valid
		JSONObject commit = new JSONObject(response.getText());
		assertNotNull(commit.optString(ProtocolConstants.KEY_LOCATION, null));
		assertNotNull(commit.optString(ProtocolConstants.KEY_NAME, null));
		assertEquals(commitMessage, commit.getString(GitConstants.KEY_COMMIT_MESSAGE));

		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(1, statusArray.length());
		// still in index, not committed
		assertEquals("folder/folder.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	public void testCommitNoComment() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change to commit");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);
		String gitHeadUri = gitSection.optString(GitConstants.KEY_HEAD, null);
		assertNotNull(gitHeadUri);

		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit with a null message
		request = getPostGitCommitRequest(gitHeadUri /* all */, null, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCommitEmptyComment() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change to commit");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);
		String gitHeadUri = gitSection.optString(GitConstants.KEY_HEAD, null);
		assertNotNull(gitHeadUri);

		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit with a null message
		request = getPostGitCommitRequest(gitHeadUri /* all */, "", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCommitAll() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		// TODO: don't create URIs out of thin air
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change to commit");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/folder/folder.txt", "change to commit");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);
		String gitStatusUri = gitSection.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);
		String gitHeadUri = gitSection.optString(GitConstants.KEY_HEAD, null);
		assertNotNull(gitHeadUri);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(2, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		// commit all
		request = getPostGitCommitRequest(gitHeadUri, "message", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		GitStatusTest.assertStatusClean(statusResponse);
	}

	@Test
	public void testCommitAmend() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		// TODO: don't create URIs out of thin air
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change to commit");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);
		String gitHeadUri = gitSection.optString(GitConstants.KEY_HEAD, null);
		assertNotNull(gitHeadUri);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = getPostGitCommitRequest(gitHeadUri, "Comit massage", false); // typos
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// amend last commit
		request = getPostGitCommitRequest(gitHeadUri, "Commit message", true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: replace with RESTful API for git log when available
		Git git = new Git(db);
		Iterable<RevCommit> commits = git.log().call();
		String expectedMessages[] = new String[] {"Initial commit", "Commit message"};
		int c = 0;
		for (RevCommit commit : commits) {
			assertEquals(expectedMessages[expectedMessages.length - 1 - c], commit.getFullMessage());
			c++;
		}
		assertEquals(expectedMessages.length, c);
	}

	@Test
	@Ignore("not yet implemented")
	public void testCommitLog() {
		// TODO: implement, see bug 340051
	}

	@Test
	@Ignore("not yet implemented")
	public void testCommitLogWithPath() {
		// TODO: implement, see bug 340051
	}

	@Test
	public void testCommitHeadContent() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		// TODO: don't create URIs out of thin air
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "in HEAD");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);
		String gitHeadUri = gitSection.optString(GitConstants.KEY_HEAD, null);
		assertNotNull(gitHeadUri);

		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// commit all
		request = getPostGitCommitRequest(gitHeadUri, "message", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getGetGitCommitRequest(gitHeadUri + "test.txt", true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("in HEAD", response.getText());
	}

	@Test
	@Ignore("not yet implemented")
	public void testCommitContentBySha() {
		// TODO: implement
	}

	@Test
	public void testCommitStagedOnly() throws Exception {
		// see bug 349480
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetFilesRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject cloneFolder = new JSONObject(response.getText());
			String cloneFolderLocation = cloneFolder.getString(ProtocolConstants.KEY_LOCATION);
			String cloneFolderChildrenLocation = cloneFolder.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
			JSONObject cloneFolderGitSection = cloneFolder.getJSONObject(GitConstants.KEY_GIT);
			String cloneFolderGitHeadUri = cloneFolderGitSection.getString(GitConstants.KEY_HEAD);

			String fileName = "folder2.txt";
			request = getPostFilesRequest(cloneFolderLocation + "/folder/", getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			request = getGetFilesRequest(cloneFolderChildrenLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
			JSONObject testTxt = getChildByName(children, "test.txt");
			String testTxtLocation = testTxt.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject testTxtGitSection = testTxt.getJSONObject(GitConstants.KEY_GIT);
			String testTxtGitIndexUri = testTxtGitSection.getString(GitConstants.KEY_INDEX);

			// drill down to 'folder'
			JSONObject folder = getChildByName(children, "folder");
			String folderChildrenLocation = folder.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
			JSONObject folderGitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String folderGitStatusUri = folderGitSection.getString(GitConstants.KEY_STATUS);
			String folderGitIndexUri = folderGitSection.getString(GitConstants.KEY_INDEX);
			String folderGitHeadUri = folderGitSection.getString(GitConstants.KEY_HEAD);

			request = getGetFilesRequest(folderChildrenLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			children = getDirectoryChildren(new JSONObject(response.getText()));
			JSONObject folder2txt = getChildByName(children, "folder2.txt");
			String folder2TxtLocation = folder2txt.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject folderTxt = getChildByName(children, "folder.txt");
			String folderTxtLocation = folderTxt.getString(ProtocolConstants.KEY_LOCATION);

			// git section for the new file
			JSONObject folder2TxtGitSection = folder2txt.getJSONObject(GitConstants.KEY_GIT);
			String folder2TxtGitIndexUri = folder2TxtGitSection.getString(GitConstants.KEY_INDEX);
			String folder2TxtGitHeadUri = folder2TxtGitSection.getString(GitConstants.KEY_HEAD);

			JSONObject folderTxtGitSection = folderTxt.getJSONObject(GitConstants.KEY_GIT);
			String folderTxtGitIndexUri = folderTxtGitSection.getString(GitConstants.KEY_INDEX);

			// stage the new file
			request = GitAddTest.getPutGitIndexRequest(folder2TxtGitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit the new file
			request = getPostGitCommitRequest(folder2TxtGitHeadUri, "folder/folder2.txt added", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status - clean
			request = GitStatusTest.getGetGitStatusRequest(folderGitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject statusResponse = new JSONObject(response.getText());
			GitStatusTest.assertStatusClean(statusResponse);

			// modify all
			request = getPutFileRequest(testTxtLocation, "change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			request = getPutFileRequest(folderTxtLocation, "change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			request = getPutFileRequest(folder2TxtLocation, "change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status - modified=3
			request = GitStatusTest.getGetGitStatusRequest(folderGitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING).length());
			assertEquals(3, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED).length());

			// add all
			request = GitAddTest.getPutGitIndexRequest(testTxtGitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			request = GitAddTest.getPutGitIndexRequest(folderTxtGitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			request = GitAddTest.getPutGitIndexRequest(folder2TxtGitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status - changed=3
			request = GitStatusTest.getGetGitStatusRequest(folderGitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED).length());
			assertEquals(3, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED).length());

			// unstage all
			request = GitResetTest.getPostGitIndexRequest(folderGitIndexUri /* reset all */, ResetType.MIXED);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status - modified=3, again
			request = GitStatusTest.getGetGitStatusRequest(folderGitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING).length());
			assertEquals(3, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED).length());

			// add folder/folder.txt
			request = GitAddTest.getPutGitIndexRequest(folderTxtGitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// add test.txt
			request = GitAddTest.getPutGitIndexRequest(testTxtGitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status - modified=2, changed=1
			request = GitStatusTest.getGetGitStatusRequest(folderGitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED).length());
			assertEquals(2, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING).length());
			assertEquals(1, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED).length());

			// commit
			request = getPostGitCommitRequest(folderGitHeadUri, "test.txt and folder/folder.txt changed", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status - changed=1
			request = GitStatusTest.getGetGitStatusRequest(folderGitStatusUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			statusResponse = new JSONObject(response.getText());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING).length());
			JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
			assertEquals(1, statusArray.length());
			assertNotNull(GitStatusTest.getChildByName(statusArray, "folder/folder2.txt"));
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED).length());
			assertEquals(0, statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED).length());

			// check the last commit for the repo
			JSONArray commitsArray = log(cloneFolderGitHeadUri, false);
			assertEquals(3, commitsArray.length());

			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals("test.txt and folder/folder.txt changed", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			JSONArray diffs = commit.getJSONArray(GitConstants.KEY_COMMIT_DIFFS);
			assertEquals(2, diffs.length());
			String oldPath = diffs.getJSONObject(0).getString(GitConstants.KEY_COMMIT_DIFF_OLDPATH);
			assertTrue("folder/folder.txt".equals(oldPath) || "test.txt".equals(oldPath));
			oldPath = diffs.getJSONObject(1).getString(GitConstants.KEY_COMMIT_DIFF_OLDPATH);
			assertTrue("folder/folder.txt".equals(oldPath) || "test.txt".equals(oldPath));
		}
	}

	static WebRequest getPostGitCommitRequest(String location, String message, boolean amend) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_COMMIT_MESSAGE, message);
		body.put(GitConstants.KEY_COMMIT_AMEND, Boolean.toString(amend));
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getGetGitCommitRequest(String location, boolean body) {
		return getGetGitCommitRequest(location, body, null, null);
	}

	static WebRequest getGetGitCommitRequest(String location, boolean body, Integer page, Integer pageSize) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + '/' + location;
		boolean firstParam = true;
		if (body) {
			if (firstParam) {
				requestURI += "?";
				firstParam = false;
			} else {
				requestURI += "&";
			}
			requestURI += "parts=body";
		}

		if (page != null) {
			if (firstParam) {
				requestURI += "?";
				firstParam = false;
			} else {
				requestURI += "&";
			}
			requestURI += "page=" + page.intValue();
		}

		if (pageSize != null) {
			if (firstParam) {
				requestURI += "?";
				firstParam = false;
			} else {
				requestURI += "&";
			}
			requestURI += "pageSize=" + pageSize.intValue();
		}

		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;

	}
}
