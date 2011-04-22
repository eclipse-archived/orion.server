/*******************************************************************************
 * Copyright (c)  2011 IBM Corporation and others.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitMergeTest extends GitTest {
	@Test
	public void testMergeSelf() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);
		String gitStatusUri = gitSection.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);
		String gitCommitUri = gitSection.optString(GitConstants.KEY_COMMIT, null);
		assertNotNull(gitCommitUri);

		// "git merge master"
		JSONObject merge = merge(gitCommitUri, Constants.MASTER);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.ALREADY_UP_TO_DATE, mergeResult);
	}

	@Test
	public void testMerge() throws IOException, SAXException, JSONException, URISyntaxException, JGitInternalException, GitAPIException {
		// clone: create
		String contentLocation = clone(null);

		// clone: link
		JSONObject project = linkProject(contentLocation, getMethodName());
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		// create branch 'a'
		branch(contentLocation, "a");

		// checkout 'a'
		FileRepository db1 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation)), Constants.DOT_GIT));
		Git git = new Git(db1);
		GitRemoteTest.ensureOnBranch(git, "a");

		// modify while on 'a'
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change in a");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit on a", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		GitStatusTest.assertStatusClean(statusResponse);

		// checkout 'master'
		GitRemoteTest.ensureOnBranch(git, Constants.MASTER);

		// modify a different file on master
		request = getPutFileRequest(projectId + "/folder/folder.txt", "change in master");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit on master", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		GitStatusTest.assertStatusClean(statusResponse);

		// merge: "git merge a"
		JSONObject merge = merge(gitCommitUri, "a");
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.MERGED, mergeResult);

		// assert clean
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		GitStatusTest.assertStatusClean(statusResponse);

		// TODO: don't create URIs out of thin air
		request = getGetFilesRequest(projectId + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("change in a", response.getText());

		// TODO: don't create URIs out of thin air
		request = getGetFilesRequest(projectId + "/folder/folder.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("change in master", response.getText());

		// TODO: check commits, bug 340051
	}

	@Test
	public void testMergeAlreadyUpToDate() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		// TODO: don't create URIs out of thin air
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change in master");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

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
		assertEquals(1, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		// "git merge master"
		JSONObject merge = merge(gitCommitUri, Constants.MASTER);
		MergeStatus mergeResult = MergeResult.MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeResult.MergeStatus.ALREADY_UP_TO_DATE, mergeResult);

		// assert clean
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		// status hasn't changed
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(1, statusArray.length());
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
	public void testMergeConflict() throws IOException, SAXException, JSONException, URISyntaxException, JGitInternalException, GitAPIException {
		// clone: create
		String contentLocation = clone(null);

		// clone: link
		JSONObject project = linkProject(contentLocation, getMethodName());
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		// create branch 'a'
		branch(contentLocation, "a");

		// checkout 'a'
		FileRepository db1 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation)), Constants.DOT_GIT));
		Git git = new Git(db1);
		GitRemoteTest.ensureOnBranch(git, "a");

		// modify while on 'a'
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change in a");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit on a", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		GitStatusTest.assertStatusClean(statusResponse);

		// checkout 'master'
		GitRemoteTest.ensureOnBranch(git, Constants.MASTER);

		// modify a different file on master
		request = getPutFileRequest(projectId + "/test.txt", "change in master");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit on master", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		GitStatusTest.assertStatusClean(statusResponse);

		// merge: "git merge a"
		JSONObject merge = merge(gitCommitUri, "a");
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.CONFLICTING, mergeResult);

		// assert clean
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(1, statusArray.length());
		assertNotNull(GitStatusTest.getChildByName(statusArray, "test.txt"));
		assertNotNull(GitStatusTest.getChildByKey(statusArray, GitConstants.KEY_PATH, "test.txt"));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(1, statusArray.length());
		assertNotNull(GitStatusTest.getChildByName(statusArray, "test.txt"));
		assertNotNull(GitStatusTest.getChildByKey(statusArray, GitConstants.KEY_PATH, "test.txt"));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(1, statusArray.length());
		assertNotNull(GitStatusTest.getChildByName(statusArray, "test.txt"));
		assertNotNull(GitStatusTest.getChildByKey(statusArray, GitConstants.KEY_PATH, "test.txt"));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertNotNull(GitStatusTest.getChildByName(statusArray, "test.txt"));
		assertNotNull(GitStatusTest.getChildByKey(statusArray, GitConstants.KEY_PATH, "test.txt"));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		// TODO: don't create URIs out of thin air
		request = getGetFilesRequest(projectId + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] responseLines = response.getText().split("\n");
		assertEquals(5, responseLines.length);
		assertEquals("<<<<<<< HEAD", responseLines[0]);
		assertEquals("change in master", responseLines[1]);
		assertEquals("=======", responseLines[2]);
		assertEquals("change in a", responseLines[3]);
		// ignore the last line since it's different each time
		// assertEquals(">>>>>>> c5ddb0e22e7e829683bb3b336ca6cb24a1b5bb2e", responseLines[4]);

		// TODO: check commits, bug 340051
	}

	@Test
	public void testMergeRemote() throws IOException, SAXException, JSONException, JGitInternalException {
		// clone1: create
		String contentLocation1 = clone(null);

		// clone1: link
		JSONObject project1 = linkProject(contentLocation1, getMethodName() + "1");
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone2: create
		String contentLocation2 = clone(null);

		// clone2: link
		JSONObject project2 = linkProject(contentLocation2, getMethodName() + "2");
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
		String gitCommitUri2 = gitSection2.getString(GitConstants.KEY_COMMIT);

		// clone1: get remote details
		JSONObject details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String refId1 = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation1 = details.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: change
		WebRequest request = getPutFileRequest(projectId2 + "/test.txt", "incoming change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri2, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		ServerStatus pushStatus = push(gitRemoteUri2, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// clone1: fetch
		request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation1, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		waitForTaskCompletion(taskLocation);

		// clone1: get remote details again
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String newRefId1 = remoteBranch.getString(ProtocolConstants.KEY_ID);
		// an incoming commit
		assertFalse(refId1.equals(newRefId1));

		// clone1: merge into HEAD, "git merge origin/master"
		//String gitCommitUri = remoteBranch.getString(GitConstants.KEY_COMMIT);
		// TODO: should fail when POSTing to the above URI, see bug 342845

		String gitCommitUri = remoteBranch.getString(GitConstants.KEY_HEAD);
		assertNotNull(gitCommitUri);

		// merge
		JSONObject merge = merge(gitCommitUri, newRefId1);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAST_FORWARD, mergeResult);

		request = getGetFilesRequest(projectId1 + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("incoming change", response.getText());
	}

}
