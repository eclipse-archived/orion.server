/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
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

import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
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
	public void testCommitOnly() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change to commit");
		addFile(testTxt);

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "change to commit");
		addFile(folderTxt);

		assertStatus(new StatusResult().setChanged(2), gitStatusUri);

		// "git commit -m 'message' -- test.txt
		final String commitMessage = "message";
		JSONObject commit = commitFile(testTxt, commitMessage);

		// check if response contains most important parts and if commit
		// message is valid
		assertNotNull(commit.optString(ProtocolConstants.KEY_LOCATION, null));
		assertNotNull(commit.optString(ProtocolConstants.KEY_NAME, null));
		assertEquals(commitMessage, commit.getString(GitConstants.KEY_COMMIT_MESSAGE));

		// still in index, not committed
		assertStatus(new StatusResult().setChangedNames("folder/folder.txt"), gitStatusUri);
	}

	@Test
	public void testCommitNoComment() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change to commit");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		addFile(testTxt);

		// commit with a null message
		WebRequest request = getPostGitCommitRequest(gitHeadUri /* all */, null, false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCommitEmptyComment() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change to commit");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		addFile(testTxt);

		// commit with a null message
		WebRequest request = getPostGitCommitRequest(gitHeadUri /* all */, "", false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCommitAll() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change to commit");

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "change to commit");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// "git add ."
		WebRequest request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setChanged(2), gitStatusUri);

		// commit all
		request = getPostGitCommitRequest(gitHeadUri, "message", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		assertStatus(StatusResult.CLEAN, gitStatusUri);
	}

	@Test
	public void testCommitAmend() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change to commit");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// "git add ."
		WebRequest request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = getPostGitCommitRequest(gitHeadUri, "Comit massage", false); // typos
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// amend last commit
		request = getPostGitCommitRequest(gitHeadUri, "Commit message", true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONArray commitsArray = log(gitHeadUri);
		assertEquals(2, commitsArray.length());
		JSONObject commit = commitsArray.getJSONObject(0);
		assertEquals("Commit message", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
		commit = commitsArray.getJSONObject(1);
		assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
	}

	@Test
	public void testCommitHeadContent() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "in HEAD");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		addFile(testTxt);

		// commit all
		WebRequest request = getPostGitCommitRequest(gitHeadUri, "message", false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject testTxtGitSection = testTxt.getJSONObject(GitConstants.KEY_GIT);
		String testTxtGitHeadUri = testTxtGitSection.getString(GitConstants.KEY_HEAD);
		request = getGetGitCommitRequest(testTxtGitHeadUri, true);
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
	public void testCommitAllInFolder() throws Exception {
		// see bug 349480
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject cloneFolder = new JSONObject(response.getText());

			String fileName = "folder2.txt";
			JSONObject folder = getChild(cloneFolder, "folder");
			request = getPostFilesRequest(folder.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			JSONObject testTxt = getChild(cloneFolder, "test.txt");

			// drill down to 'folder'
			JSONObject folderGitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String folderGitStatusUri = folderGitSection.getString(GitConstants.KEY_STATUS);

			JSONObject folder2Txt = getChild(folder, "folder2.txt");
			JSONObject folderTxt = getChild(folder, "folder.txt");

			// git section for the new file
			JSONObject folder2TxtGitSection = folder2Txt.getJSONObject(GitConstants.KEY_GIT);
			String folder2TxtGitIndexUri = folder2TxtGitSection.getString(GitConstants.KEY_INDEX);
			String folder2TxtGitHeadUri = folder2TxtGitSection.getString(GitConstants.KEY_HEAD);

			// stage the new file
			request = GitAddTest.getPutGitIndexRequest(folder2TxtGitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit the new file
			request = getPostGitCommitRequest(folder2TxtGitHeadUri, "folder/folder2.txt added", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status - clean
			assertStatus(StatusResult.CLEAN, folderGitStatusUri);

			// modify all
			modifyFile(testTxt, "change");
			modifyFile(folderTxt, "change");
			modifyFile(folder2Txt, "change");

			// check status - modified=3
			assertStatus(new StatusResult().setModified(3), folderGitStatusUri);

			addFile(folderTxt);
			addFile(testTxt);

			// check status - modified=1, changed=2
			assertStatus(new StatusResult().setModified(1).setChanged(2), folderGitStatusUri);

			// commit all
			// XXX: using HEAD URI for folder will commit all files in the folder, regardless of index state
			// request = getPostGitCommitRequest(folderGitHeadUri, "test.txt and folder/folder.txt changed", false);
			// the UI should use HEAD URI for the clone/root to commit all staged files
			JSONObject clone = getCloneForGitResource(folder);
			String cloneFolderGitHeadUri = clone.getString(GitConstants.KEY_HEAD);

			request = getPostGitCommitRequest(cloneFolderGitHeadUri, "test.txt and folder/folder.txt changed", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status - changed=1
			assertStatus(new StatusResult().setModifiedNames("folder/folder2.txt"), folderGitStatusUri);

			// check the last commit for the repo
			JSONArray commitsArray = log(cloneFolderGitHeadUri);
			assertEquals(3, commitsArray.length());

			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals("test.txt and folder/folder.txt changed", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			JSONObject diffsParent = commit.getJSONObject(GitConstants.KEY_COMMIT_DIFFS);
			JSONArray diffs = diffsParent.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, diffs.length());
			String oldPath = diffs.getJSONObject(0).getString(GitConstants.KEY_COMMIT_DIFF_OLDPATH);
			assertTrue("folder/folder.txt".equals(oldPath) || "test.txt".equals(oldPath));
			oldPath = diffs.getJSONObject(1).getString(GitConstants.KEY_COMMIT_DIFF_OLDPATH);
			assertTrue("folder/folder.txt".equals(oldPath) || "test.txt".equals(oldPath));
		}
	}

	@Test
	public void testCommitWithCommiterOverwritten() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			final String commiterName = "committer name";
			final String commiterEmail = "committer email";

			request = getPostGitCommitRequest(gitHeadUri, "Comit message", false, commiterName, commiterEmail, null, null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// log
			JSONArray commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());

			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals(commiterName, commit.get(GitConstants.KEY_COMMITTER_NAME));
			assertEquals(commiterEmail, commit.get(GitConstants.KEY_COMMITTER_EMAIL));
		}
	}

	@Test
	public void testCommitWithAuthorOverwritten() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit all
			final String commiterName = "committer name";
			final String commiterEmail = "committer email";

			request = getPostGitCommitRequest(gitHeadUri, "Comit message", false, commiterName, commiterEmail, null, null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// log
			JSONArray commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());

			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals(commiterName, commit.get(GitConstants.KEY_COMMITTER_NAME));
			assertEquals(commiterEmail, commit.get(GitConstants.KEY_COMMITTER_EMAIL));
		}
	}

	@Test
	public void testCommitterAndAuthorFallback() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			// set user.name and user.email
			final String name = "name";
			final String email = "email";
			final String defaultName = "default name";
			final String defaultEmail = "default email";
			request = GitConfigTest.getPostGitConfigRequest(gitConfigUri, "user.name", defaultName);
			response = webConversation.getResponse(request);
			assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			request = GitConfigTest.getPostGitConfigRequest(gitConfigUri, "user.email", defaultEmail);
			response = webConversation.getResponse(request);
			assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit - author and committer not specified
			request = getPostGitCommitRequest(gitHeadUri, "1", false, null, null, null, null);
			response = webConversation.getResponse(request);
			assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

			// log - expect default values
			JSONArray commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());

			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals(defaultName, commit.get(GitConstants.KEY_COMMITTER_NAME));
			assertEquals(defaultEmail, commit.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertEquals(defaultName, commit.get(GitConstants.KEY_AUTHOR_NAME));
			assertEquals(defaultEmail, commit.get(GitConstants.KEY_AUTHOR_EMAIL));

			// commit - only committer given
			request = getPostGitCommitRequest(gitHeadUri, "2", true, name, email, null, null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// log - expect author is the same as committer
			commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());

			commit = commitsArray.getJSONObject(0);
			assertEquals(name, commit.get(GitConstants.KEY_COMMITTER_NAME));
			assertEquals(email, commit.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertEquals(name, commit.get(GitConstants.KEY_AUTHOR_NAME));
			assertEquals(email, commit.get(GitConstants.KEY_AUTHOR_EMAIL));

			// commit - only committer name given
			request = getPostGitCommitRequest(gitHeadUri, "3", true, name, null, null, null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// log - expect author is the same as committer and their email is defaultEmail
			commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());

			commit = commitsArray.getJSONObject(0);
			assertEquals(name, commit.get(GitConstants.KEY_COMMITTER_NAME));
			assertEquals(defaultEmail, commit.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertEquals(name, commit.get(GitConstants.KEY_AUTHOR_NAME));
			assertEquals(defaultEmail, commit.get(GitConstants.KEY_AUTHOR_EMAIL));
		}
	}

	static WebRequest getPostGitCommitRequest(String location, String message, boolean amend, String committerName, String committerEmail, String authorName, String authorEmail) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_COMMIT_MESSAGE, message);
		body.put(GitConstants.KEY_COMMIT_AMEND, Boolean.toString(amend));
		body.put(GitConstants.KEY_COMMITTER_NAME, committerName);
		body.put(GitConstants.KEY_COMMITTER_EMAIL, committerEmail);
		body.put(GitConstants.KEY_AUTHOR_NAME, authorName);
		body.put(GitConstants.KEY_AUTHOR_EMAIL, authorEmail);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getPostGitCommitRequest(String location, String message, boolean amend) throws JSONException, UnsupportedEncodingException {
		return getPostGitCommitRequest(location, message, amend, null, null, null, null);
	}

	static WebRequest getGetGitCommitRequest(String location, boolean body) {
		return getGetGitCommitRequest(location, body, null, null);
	}

	static WebRequest getGetGitCommitRequest(String location, boolean body, Integer page, Integer pageSize) {
		String requestURI = toAbsoluteURI(location);
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
