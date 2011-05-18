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

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitRemoteTest extends GitTest {
	@Test
	public void testGetNoRemote() throws Exception {
		URI contentLocation = gitDir.toURI();

		JSONObject project = linkProject(contentLocation.toString(), getMethodName());
		String projectContentLocation = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(projectContentLocation);

		// http://<host>/file/<projectId>/
		WebRequest request = getGetFilesRequest(projectContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		request = getGetGitRemoteRequest(gitRemoteUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(0, remotesArray.length());
	}

	@Test
	public void testGetOrigin() throws Exception {
		// clone a  repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String gitRemoteUri = clone.getString(GitConstants.KEY_REMOTE);
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		assertNotNull(remoteBranch);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// check if Git locations are in place
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		assertEquals(gitRemoteUri, gitSection.getString(GitConstants.KEY_REMOTE));
	}

	@Test
	public void testGetUnknownRemote() throws Exception {
		// clone a  repo
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
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		request = getGetGitRemoteRequest(gitRemoteUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteLocation);

		URI u = URI.create(remoteLocation);
		IPath p = new Path(u.getPath());
		p = p.uptoSegment(2).append("xxx").append(p.removeFirstSegments(3));
		URI nu = new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), p.toString(), u.getQuery(), u.getFragment());

		request = getGetGitRemoteRequest(nu.toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testGetRemoteCommits() throws Exception {
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

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX);
		String gitCommitUri = gitSection.optString(GitConstants.KEY_COMMIT);

		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		assertNotNull(remoteBranch);
		String remoteBranchLocation = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);
		request = getGetGitRemoteRequest(remoteBranchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		remoteBranch = new JSONObject(response.getText());

		String commitLocation = remoteBranch.getString(GitConstants.KEY_COMMIT);
		request = GitCommitTest.getGetGitCommitRequest(commitLocation, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject logResponse = new JSONObject(response.getText());
		JSONArray commitsArray = logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, commitsArray.length());

		// change
		request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "new change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// push
		ServerStatus pushStatus = push(gitRemoteUri, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		remoteBranch = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		assertNotNull(remoteBranch);
		remoteBranchLocation = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);
		request = getGetGitRemoteRequest(remoteBranchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		remoteBranch = new JSONObject(response.getText());

		commitLocation = remoteBranch.getString(GitConstants.KEY_COMMIT);
		request = GitCommitTest.getGetGitCommitRequest(commitLocation, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		logResponse = new JSONObject(response.getText());
		commitsArray = logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(2, commitsArray.length());

		// TODO: test pushing change from another repo and fetch here
	}

	@Test
	public void testGetRemoteBranches() throws Exception {
		// clone a  repo
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

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		Repository db1 = getRepositoryForContentLocation(cloneContentLocation);
		Git git = new Git(db1);
		int localBefore = git.branchList().call().size();
		int remoteBefore = git.branchList().setListMode(ListMode.REMOTE).call().size();
		int allBefore = git.branchList().setListMode(ListMode.ALL).call().size();
		branch(branchesLocation, "a");

		assertEquals(1, git.branchList().call().size() - localBefore);
		assertEquals(0, git.branchList().setListMode(ListMode.REMOTE).call().size() - remoteBefore);
		assertEquals(1, git.branchList().setListMode(ListMode.ALL).call().size() - allBefore);

		// push all, replace with REST API when bug 339115 is fixed
		git.push().setPushAll().call();

		assertEquals(1, git.branchList().call().size() - localBefore);
		assertEquals(1, git.branchList().setListMode(ListMode.REMOTE).call().size() - remoteBefore);
		assertEquals(2, git.branchList().setListMode(ListMode.ALL).call().size() - allBefore);

		checkoutBranch(cloneLocation, Constants.MASTER);
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 2, 0, Constants.MASTER);
		assertNotNull(remoteBranch);

		checkoutBranch(cloneLocation, "a");
		remoteBranch = getRemoteBranch(gitRemoteUri, 2, 0, "a");
		assertNotNull(remoteBranch);
	}

	static WebRequest getGetGitRemoteRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.REMOTE_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static void assertOnBranch(Git git, String branch) throws IOException {
		assertNotNull(git.getRepository().getRef(branch));
	}

}