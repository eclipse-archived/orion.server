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
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
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
	public void testPushNoBody() throws JSONException, IOException, SAXException, URISyntaxException {
		// add clone
		String contentLocation = clone(null);

		URI workspaceLocation = createWorkspace(getMethodName());

		// link
		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		// list remotes
		request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);

		// get remote branch location
		JSONObject remoteBranch = GitRemoteTest.getRemoteBranch(remoteLocation, 1, 0, Constants.MASTER);
		String remoteBranchLocation = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// push with no body
		request = getPostGitRemoteRequest(remoteBranchLocation, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testPushHead() throws JSONException, IOException, SAXException, URISyntaxException {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1: create
		String contentLocation1 = clone(null);

		// clone1: link
		ServletTestingSupport.allowedPrefixes = contentLocation1;
		String projectName1 = getMethodName() + "1";
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation1);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName1 != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName1);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project1 = new JSONObject(response.getText());
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.optString(GitConstants.KEY_INDEX);
		String gitCommitUri1 = gitSection1.optString(GitConstants.KEY_COMMIT);

		// clone2: create
		String contentLocation2 = clone(null);

		// clone2: link
		ServletTestingSupport.allowedPrefixes = contentLocation2;
		String projectName2 = getMethodName() + "2";
		body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation2);
		in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName2 != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName2);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project2 = new JSONObject(response.getText());
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
		request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation1 = remote.getString(ProtocolConstants.KEY_LOCATION);

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

		// clone1: get remote branch location
		JSONObject remoteBranch1 = GitRemoteTest.getRemoteBranch(remoteLocation1, 1, 0, Constants.MASTER);
		String remoteBranchLocation1 = remoteBranch1.getString(ProtocolConstants.KEY_LOCATION);

		// clone1: push
		request = getPostGitRemoteRequest(remoteBranchLocation1, Constants.HEAD);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: list remotes
		request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		remotes = new JSONObject(response.getText());
		remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation2 = remote.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: get remote branch location
		JSONObject remoteBranch = GitRemoteTest.getRemoteBranch(remoteLocation2, 1, 0, Constants.MASTER);
		String remoteBranchLocation2 = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: fetch
		request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation2, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		waitForTaskCompletion(taskLocation);

		// clone2: get remote details
		JSONObject remoteBranch2 = GitRemoteTest.getRemoteBranch(remoteLocation2, 1, 0, Constants.MASTER);
		String newRefId2 = remoteBranch2.getString(ProtocolConstants.KEY_ID);

		// clone2: merge into HEAD, "git merge origin/master"
		gitCommitUri2 = remoteBranch2.getString(GitConstants.KEY_HEAD);
		request = GitMergeTest.getPostGitMergeRequest(gitCommitUri2, newRefId2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

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

	private static WebRequest getPostGitRemoteRequest(String location, String srcRef) throws JSONException {
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
