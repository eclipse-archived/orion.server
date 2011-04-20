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
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.HttpURLConnection;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitPushTest extends GitTest {
	@Test
	public void testPushNoBody() throws JSONException, IOException, SAXException {
		// add clone
		String contentLocation = clone(null);

		// link
		JSONObject project = linkProject(contentLocation, getMethodName());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		// get remote branch location
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		String remoteBranchLocation = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// push with no body
		WebRequest request = getPostGitRemoteRequest(remoteBranchLocation, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testPushHead() throws JSONException, IOException, SAXException {
		// clone1: create
		String contentLocation1 = clone(null);

		// clone1: link
		JSONObject project1 = linkProject(contentLocation1, getMethodName() + "1");
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.optString(GitConstants.KEY_INDEX);
		String gitCommitUri1 = gitSection1.optString(GitConstants.KEY_COMMIT);

		// clone2: create
		String contentLocation2 = clone(null);

		// clone2: link
		JSONObject project2 = linkProject(contentLocation2, getMethodName() + "2");
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri2);
		String gitIndexUri2 = gitSection2.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri2);
		String gitCommitUri2 = gitSection2.optString(GitConstants.KEY_COMMIT, null);
		assertNotNull(gitCommitUri2);

		// clone1: list remotes
		WebRequest request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri1);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));

		// clone1: change
		request = getPutFileRequest(projectId1 + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri1, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		JSONObject push = push(gitRemoteUri1, Constants.HEAD);
		Status result = Status.valueOf(push.getString(GitConstants.KEY_RESULT));
		assertEquals(Status.OK, result);

		// clone2: get remote branch location
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String remoteBranchLocation2 = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: fetch
		fetch(remoteBranchLocation2);

		// clone2: get remote details
		JSONObject remoteBranch2 = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String newRefId2 = remoteBranch2.getString(ProtocolConstants.KEY_ID);

		// clone2: merge into HEAD, "git merge origin/master"
		gitCommitUri2 = remoteBranch2.getString(GitConstants.KEY_HEAD);
		JSONObject merge = merge(gitCommitUri2, newRefId2);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAST_FORWARD, mergeResult);

		// clone2: assert change from clone1 is in place
		request = getGetFilesRequest(projectId2 + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("incoming change", response.getText());
	}

	@Test
	@Ignore("not implemented yet")
	public void testPushBranch() {
		// TODO:
		// clone1
		// clone2
		// clone1: create branch 'a'
		// clone1: add, commit
		// clone1: switch to master
		// clone1: push 'a'
		// clone2: fetch, merge
		// clone2: assert branch 'a' and change from clone1 are in place
	}

	@Test
	@Ignore("not implemented yet")
	public void testPushNoSrc() {
		// TODO:
		// clone1
		// clone2
		// clone1: add, commit, try to push with empty Src, DELETE should be suggested
	}

	@Test
	@Ignore("not implemented yet")
	public void testPushToDelete() {
		// TODO:
		// clone1
		// clone1: create branch 'a'
		// clone1: add, commit
		// clone1: push 'a'
		// list remote branches, assert=2
		// clone1: DELETE 'a' ('git push origin :a')
		// list remote branches, assert=1
	}

	@Test
	public void testPushFromLog() throws JSONException, IOException, SAXException {
		String contentLocation = clone(null);

		JSONObject project = linkProject(contentLocation, getMethodName());
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// log
		WebRequest request = GitCommitTest.getGetGitCommitRequest(gitCommitUri, false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject logResponse = new JSONObject(response.getText());
		JSONArray commitsArray = logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, commitsArray.length());

		// change
		request = getPutFileRequest(projectId + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// log again
		request = GitCommitTest.getGetGitCommitRequest(gitCommitUri, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		logResponse = new JSONObject(response.getText());
		commitsArray = logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(2, commitsArray.length());

		String remoteBranchLocation = logResponse.getString(GitConstants.KEY_REMOTE);

		// push
		request = getPostGitRemoteRequest(remoteBranchLocation, Constants.HEAD);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testPushRejected() throws JSONException, IOException, SAXException {
		// clone1: create
		String contentLocation1 = clone(null);

		// clone1: link
		JSONObject project1 = linkProject(contentLocation1, getMethodName() + "1");
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.optString(GitConstants.KEY_INDEX);
		String gitCommitUri1 = gitSection1.optString(GitConstants.KEY_COMMIT);

		// clone2: create
		String contentLocation2 = clone(null);

		// clone2: link
		JSONObject project2 = linkProject(contentLocation2, getMethodName() + "2");
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri2);
		String gitIndexUri2 = gitSection2.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri2);
		String gitCommitUri2 = gitSection2.optString(GitConstants.KEY_COMMIT, null);
		assertNotNull(gitCommitUri2);

		// clone1: change
		WebRequest request = getPutFileRequest(projectId1 + "/test.txt", "clone1 change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri1, "clone1 change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		JSONObject push = push(gitRemoteUri1, Constants.HEAD);
		Status result = Status.valueOf(push.getString(GitConstants.KEY_RESULT));
		assertEquals(Status.OK, result);

		// clone2: change
		request = getPutFileRequest(projectId2 + "/test.txt", "clone2 change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri2, "clone2 change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		push = push(gitRemoteUri2, Constants.HEAD);
		result = Status.valueOf(push.getString(GitConstants.KEY_RESULT));
		assertEquals(Status.REJECTED_NONFASTFORWARD, result);
	}

	static WebRequest getPostGitRemoteRequest(String location, String srcRef) throws JSONException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.REMOTE_RESOURCE + location;

		JSONObject body = new JSONObject();
		if (srcRef != null)
			body.put(GitConstants.KEY_PUSH_SRC_REF, srcRef);
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
