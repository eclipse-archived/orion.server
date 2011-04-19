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

import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitRemoteTest extends GitTest {
	@Test
	public void testGetNoRemote() throws IOException, SAXException, JSONException, URISyntaxException {
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
	public void testGetOrigin() throws IOException, SAXException, JSONException, URISyntaxException {
		String contentLocation = clone(null);

		JSONObject project = linkProject(contentLocation.toString(), getMethodName());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		assertNotNull(remoteBranch);
	}

	@Test
	public void testGetUnknownRemote() throws IOException, SAXException, JSONException, URISyntaxException {
		String contentLocation = clone(null);

		JSONObject project = linkProject(contentLocation.toString(), getMethodName());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		WebRequest request = getGetGitRemoteRequest(gitRemoteUri);
		WebResponse response = webConversation.getResponse(request);
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
	public void testGetRemoteCommits() throws JSONException, IOException, SAXException, URISyntaxException, JGitInternalException, GitAPIException {
		String contentLocation = clone(null);

		JSONObject project = linkProject(contentLocation.toString(), getMethodName());
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX);
		String gitCommitUri = gitSection.optString(GitConstants.KEY_COMMIT);

		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		assertNotNull(remoteBranch);
		String remoteBranchLocation = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);
		WebRequest request = getGetGitRemoteRequest(remoteBranchLocation);
		WebResponse response = webConversation.getResponse(request);
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
		push(gitRemoteUri, Constants.HEAD);

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
	public void testGetRemoteBranches() throws JSONException, IOException, SAXException, URISyntaxException, JGitInternalException, GitAPIException {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone: create
		String contentLocation = clone(null);

		// clone: link
		JSONObject project = linkProject(contentLocation.toString(), getMethodName());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		FileRepository db1 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation)), Constants.DOT_GIT));
		Git git = new Git(db1);
		int localBefore = git.branchList().call().size();
		int remoteBefore = git.branchList().setListMode(ListMode.REMOTE).call().size();
		int allBefore = git.branchList().setListMode(ListMode.ALL).call().size();
		Ref aBranch = git.branchCreate().setName("a").call();
		assertEquals(Constants.R_HEADS + "a", aBranch.getName());

		assertEquals(1, git.branchList().call().size() - localBefore);
		assertEquals(0, git.branchList().setListMode(ListMode.REMOTE).call().size() - remoteBefore);
		assertEquals(1, git.branchList().setListMode(ListMode.ALL).call().size() - allBefore);

		// push all, replace with REST API when bug 339115 is fixed
		git.push().setPushAll().call();

		assertEquals(1, git.branchList().call().size() - localBefore);
		assertEquals(1, git.branchList().setListMode(ListMode.REMOTE).call().size() - remoteBefore);
		assertEquals(2, git.branchList().setListMode(ListMode.ALL).call().size() - allBefore);

		ensureOnBranch(git, Constants.MASTER);
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 2, 0, Constants.MASTER);
		assertNotNull(remoteBranch);

		ensureOnBranch(git, "a");
		remoteBranch = getRemoteBranch(gitRemoteUri, 2, 0, "a");
		assertNotNull(remoteBranch);
	}

	static WebRequest getGetGitRemoteRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.REMOTE_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static void ensureOnBranch(Git git, String branch) {
		// ensure the branch exists locally
		List<Ref> list = git.branchList().call();
		for (Ref ref : list) {
			if (ref.getName().equals(Constants.R_HEADS + branch)) {
				try {
					Ref r = git.checkout().setName(branch).call();
					assertEquals(Constants.R_HEADS + branch, r.getName());
					assertNotNull(git.getRepository().getRef(branch));
					return;
				} catch (Exception e) {
					fail(e.getMessage());
				}
			}
		}
		fail("branch '" + branch + "' doesn't exist locally");
	}

}