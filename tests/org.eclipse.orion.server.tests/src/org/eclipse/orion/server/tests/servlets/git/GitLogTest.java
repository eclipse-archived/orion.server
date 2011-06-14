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

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitLogTest extends GitTest {

	@Test
	public void testLog() throws Exception {
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

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			// modify
			String folderLocation = folder.getString(ProtocolConstants.KEY_LOCATION);
			request = getPutFileRequest(folderLocation + "test.txt", "first change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// TODO: don't create URIs out of thin air
			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit1
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// modify again
			request = getPutFileRequest(folderLocation + "test.txt", "second change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit2
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit2", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get the full log
			JSONArray commitsArray = log(gitHeadUri, false);
			assertEquals(3, commitsArray.length());

			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

			commit = commitsArray.getJSONObject(1);
			assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

			commit = commitsArray.getJSONObject(2);
			assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			String initialGitCommitName = commit.getString(ProtocolConstants.KEY_NAME);
			String initialGitCommitURI = gitHeadUri.replaceAll(Constants.HEAD, initialGitCommitName);

			//get log for given page size
			commitsArray = log(gitHeadUri, false, 1, 1);
			assertEquals(1, commitsArray.length());

			commit = commitsArray.getJSONObject(0);
			assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

			//get log for second page
			commitsArray = log(gitHeadUri, false, 2, 1);
			assertEquals(1, commitsArray.length());

			commit = commitsArray.getJSONObject(0);
			assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

			// prepare a scoped log location
			request = getPostForScopedLogRequest(initialGitCommitURI, Constants.HEAD);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			String newGitCommitUri = response.getHeaderField(ProtocolConstants.KEY_LOCATION);

			// get a scoped log
			commitsArray = log(newGitCommitUri, false);
			assertEquals(2, commitsArray.length());

			commit = commitsArray.getJSONObject(0);
			assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

			commit = commitsArray.getJSONObject(1);
			assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
		}
	}

	@Test
	public void testLogWithRemote() throws Exception {

		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetFilesRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			JSONArray commitsArray = log(gitHeadUri, true);
			assertEquals(1, commitsArray.length());
		}
	}

	@Test
	public void testLogWithTag() throws Exception {
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

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		JSONArray commitsArray = log(gitHeadUri, true);
		assertEquals(1, commitsArray.length());

		String commitUri = commitsArray.getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION);
		JSONObject updatedCommit = tag(commitUri, "tag");
		JSONArray tagsAndBranchesArray = updatedCommit.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, tagsAndBranchesArray.length());
		assertEquals(Constants.R_TAGS + "tag", tagsAndBranchesArray.getJSONObject(0).get(ProtocolConstants.KEY_FULL_NAME));

		commitsArray = log(gitHeadUri, true);
		assertEquals(1, commitsArray.length());

		tagsAndBranchesArray = commitsArray.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, tagsAndBranchesArray.length());
		assertEquals(Constants.R_TAGS + "tag", tagsAndBranchesArray.getJSONObject(0).get(ProtocolConstants.KEY_FULL_NAME));
	}

	@Test
	@Ignore("bug 343644")
	public void testLogWithBranch() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		JSONObject clone = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		JSONArray commitsArray = log(gitCommitUri, true);
		assertEquals(1, commitsArray.length());

		branch(branchesLocation, "branch");

		commitsArray = log(gitCommitUri, true);
		assertEquals(1, commitsArray.length());

		JSONArray tagsAndBranchesArray = commitsArray.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, tagsAndBranchesArray.length());
		assertEquals(Constants.R_HEADS + "branch", tagsAndBranchesArray.get(0));
	}

	@Test
	public void testLogFolder() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// modify
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "test.txt change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit1
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modify again
		request = getPutFileRequest(projectId + "/folder/folder.txt", "folder.txt change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "folder/folder.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit2
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit2", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// get log for file
		// TODO: don't create URIs out of thin air
		request = GitCommitTest.getGetGitCommitRequest(gitHeadUri, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject log = new JSONObject(response.getText());

		String repositoryPath = log.getString(GitConstants.KEY_REPOSITORY_PATH);
		assertEquals("", repositoryPath);

		JSONArray commitsArray = log.getJSONArray(ProtocolConstants.KEY_CHILDREN);

		assertEquals(3, commitsArray.length());

		JSONObject commit = commitsArray.getJSONObject(0);
		assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = commitsArray.getJSONObject(1);
		assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = commitsArray.getJSONObject(2);
		assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		request = GitCommitTest.getGetGitCommitRequest(gitHeadUri + "/folder/", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		log = new JSONObject(response.getText());

		repositoryPath = log.getString(GitConstants.KEY_REPOSITORY_PATH);
		assertEquals("folder/", repositoryPath);

		commitsArray = log.getJSONArray(ProtocolConstants.KEY_CHILDREN);

		assertEquals(2, commitsArray.length());

		commit = commitsArray.getJSONObject(0);
		assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = commitsArray.getJSONObject(1);
		assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
	}

	@Test
	public void testLogFile() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// modify
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "test.txt change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit1
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modify again
		request = getPutFileRequest(projectId + "/folder/folder.txt", "folder.txt change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "folder/folder.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit2
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit2", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// get log for file
		// TODO: don't create URIs out of thin air
		request = GitCommitTest.getGetGitCommitRequest(gitHeadUri + "test.txt", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject log = new JSONObject(response.getText());

		String repositoryPath = log.getString(GitConstants.KEY_REPOSITORY_PATH);
		assertEquals("test.txt", repositoryPath);

		JSONArray commitsArray = log.getJSONArray(ProtocolConstants.KEY_CHILDREN);

		assertEquals(2, commitsArray.length());

		JSONObject commit = commitsArray.getJSONObject(0);
		assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = commitsArray.getJSONObject(1);
		assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
	}

	@Test
	public void testLogNewBranch() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		JSONObject clone = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		JSONArray commitsArray = log(gitHeadUri, true);
		assertEquals(1, commitsArray.length());

		branch(branchesLocation, "a");
		checkoutBranch(cloneLocation, "a");

		// get project metadata again
		request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		log(gitHeadUri, true /* RemoteLocation should be available */);
	}

	@Test
	public void testDiffFromLog() throws Exception {
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

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);

			// modify
			String folderLocation = folder.getString(ProtocolConstants.KEY_LOCATION);
			request = getPutFileRequest(folderLocation + "test.txt", "hello");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// TODO: don't create URIs out of thin air
			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit1
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "2nd commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get the full log
			JSONArray commits = log(gitHeadUri, false);
			assertEquals(2, commits.length());
			JSONObject commit = commits.getJSONObject(0);
			assertEquals("2nd commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			JSONArray diffs = commit.getJSONArray(GitConstants.KEY_COMMIT_DIFFS);
			assertEquals(1, diffs.length());

			// check diff object
			JSONObject diff = diffs.getJSONObject(0);
			assertEquals("test.txt", diff.getString(GitConstants.KEY_COMMIT_DIFF_NEWPATH));
			assertEquals("test.txt", diff.getString(GitConstants.KEY_COMMIT_DIFF_OLDPATH));
			assertEquals(GitConstants.DIFF_TYPE, diff.getString(ProtocolConstants.KEY_TYPE));
			assertEquals(ChangeType.MODIFY, ChangeType.valueOf(diff.getString(GitConstants.KEY_COMMIT_DIFF_CHANGETYPE)));
			String diffLocation = diff.getString(GitConstants.KEY_DIFF);

			// check diff location
			request = GitDiffTest.getGetGitDiffRequest(diffLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			String[] parts = GitDiffTest.parseMultiPartResponse(response);

			StringBuilder sb = new StringBuilder();
			sb.append("diff --git a/test.txt b/test.txt").append("\n");
			sb.append("index 30d74d2..b6fc4c6 100644").append("\n");
			sb.append("--- a/test.txt").append("\n");
			sb.append("+++ b/test.txt").append("\n");
			sb.append("@@ -1 +1 @@").append("\n");
			sb.append("-test").append("\n");
			sb.append("\\ No newline at end of file").append("\n");
			sb.append("+hello").append("\n");
			sb.append("\\ No newline at end of file").append("\n");
			assertEquals(sb.toString(), parts[1]);

		}
	}

	private static WebRequest getPostForScopedLogRequest(String location, String newCommit) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_COMMIT_NEW, newCommit);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
