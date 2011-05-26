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

import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitMergeTest extends GitTest {
	@Test
	public void testMergeSelf() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// "git merge master"
		JSONObject merge = merge(gitCommitUri, Constants.MASTER);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.ALREADY_UP_TO_DATE, mergeResult);
	}

	@Test
	public void testMerge() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
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
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation);
		Git git = new Git(db1);
		assertBranchExist(git, "a");
		checkoutBranch(cloneLocation, "a");

		// modify while on 'a'
		request = getPutFileRequest(projectId + "/test.txt", "change in a");
		response = webConversation.getResponse(request);
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
		checkoutBranch(cloneLocation, Constants.MASTER);

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
	public void testMergeAlreadyUpToDate() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
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
	public void testMergeConflict() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
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
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		// create branch 'a'
		branch(branchesLocation, "a");

		// checkout 'a'
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation);
		Git git = new Git(db1);
		assertBranchExist(git, "a");
		checkoutBranch(cloneLocation, "a");

		// modify while on 'a'
		request = getPutFileRequest(projectId + "/test.txt", "change in a");
		response = webConversation.getResponse(request);
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
		checkoutBranch(cloneLocation, Constants.MASTER);

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

		// check status
		request = GitStatusTest.getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING);
		assertEquals(1, statusArray.length());
		assertNotNull(GitStatusTest.getChildByName(statusArray, "test.txt"));

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
	public void testMergeRemote() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		IPath clonePath1 = new Path("file").append(project1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath1);

		// get project1 metadata
		WebRequest request = getGetFilesRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		IPath clonePath2 = new Path("file").append(project2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath2);

		// get project2 metadata
		request = getGetFilesRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
		String gitCommitUri2 = gitSection2.getString(GitConstants.KEY_COMMIT);

		// clone1: get remote details
		JSONObject details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String refId1 = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation1 = details.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: change
		request = getPutFileRequest(projectId2 + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
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
		ServerStatus pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// clone1: fetch
		request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation1, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
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
