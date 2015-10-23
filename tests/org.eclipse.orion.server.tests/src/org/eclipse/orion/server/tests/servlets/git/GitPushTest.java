/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others 
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitPushTest extends GitTest {

	@BeforeClass
	public static void prepareSsh() {
		readSshProperties();
	}

	@Test
	public void testPushNoBody() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		// get remote branch location
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		String remoteBranchLocation = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// push with no body
		request = getPostGitRemoteRequest(remoteBranchLocation, null, false, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testPushHead() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath1 = getClonePath(workspaceId, project1);
		clone(clonePath1);

		// get project1 metadata
		WebRequest request = getGetRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());

		JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
		String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project2"), null);
		IPath clonePath2 = getClonePath(workspaceId, project2);
		clone(clonePath2);

		// get project2 metadata
		request = getGetRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

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

		// clone1: change
		JSONObject testTxt1 = getChild(project1, "test.txt");
		modifyFile(testTxt1, "incoming change");

		addFile(testTxt1);

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// clone2: get remote branch location
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String remoteBranchLocation2 = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: fetch
		fetch(remoteBranchLocation2);

		// clone2: get remote details
		JSONObject remoteBranch2 = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String newRefId2 = remoteBranch2.getString(ProtocolConstants.KEY_ID);

		// clone2: merge into HEAD, "git merge origin/master"
		gitHeadUri2 = remoteBranch2.getString(GitConstants.KEY_HEAD);
		JSONObject merge = merge(gitHeadUri2, newRefId2);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAST_FORWARD, mergeResult);

		// clone2: assert change from clone1 is in place
		JSONObject testTxt2 = getChild(project2, "test.txt");
		request = getGetRequest(testTxt2.getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("incoming change", response.getText());
	}

	@Test
	public void testPushHeadSshWithPrivateKeyPassphrase() throws Exception {
		Assume.assumeTrue(sshRepo2 != null);
		Assume.assumeTrue(knownHosts2 != null);
		Assume.assumeTrue(privateKey != null);
		Assume.assumeTrue(passphrase != null);

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		URIish uri = new URIish(sshRepo2);

		// clone1: create
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath = getClonePath(workspaceId, project1);

		WebRequest request = new PostGitCloneRequest().setURIish(uri).setFilePath(clonePath).setKnownHosts(knownHosts2).setPrivateKey(privateKey).setPublicKey(publicKey).setPassphrase(passphrase).getWebRequest();
		String cloneContentLocation1 = clone(request);

		// clone1: get project/folder metadata
		request = getGetRequest(cloneContentLocation1);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());

		// clone1: get git links
		JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.optString(GitConstants.KEY_INDEX);
		String gitHeadUri1 = gitSection1.optString(GitConstants.KEY_HEAD);

		// clone2: create
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project2"), null);
		clonePath = getClonePath(workspaceId, project2);
		request = new PostGitCloneRequest().setURIish(uri).setFilePath(clonePath).setKnownHosts(knownHosts2).setPrivateKey(privateKey).setPublicKey(publicKey).setPassphrase(passphrase).getWebRequest();
		String cloneContentLocation2 = clone(request);

		// clone2: get project/folder metadata
		request = getGetRequest(cloneContentLocation2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());

		// clone2: get git links
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitCommitUri2 = gitSection2.getString(GitConstants.KEY_COMMIT);

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

		// clone1: change
		JSONObject testTxt1 = getChild(project1, "test.txt");
		modifyFile(testTxt1, "incoming change");

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false, null, knownHosts2, privateKey, publicKey, passphrase, true);
		assertEquals(true, pushStatus.isOK());

		// clone2: get remote branch location
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String remoteBranchLocation2 = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: fetch
		fetch(remoteBranchLocation2, null, knownHosts2, privateKey, publicKey, passphrase, true);

		// clone2: get remote details
		JSONObject remoteBranch2 = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String newRefId2 = remoteBranch2.getString(ProtocolConstants.KEY_ID);

		// clone2: merge into HEAD, "git merge origin/master"
		gitCommitUri2 = remoteBranch2.getString(GitConstants.KEY_HEAD);
		JSONObject merge = merge(gitCommitUri2, newRefId2);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAST_FORWARD, mergeResult);

		// clone2: assert change from clone1 is in place
		JSONObject testTxt2 = getChild(project2, "test.txt");
		request = getGetRequest(testTxt2.getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("incoming change", response.getText());
	}

	@Test
	public void testPushBranch() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		// clone1: create
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath1 = getClonePath(workspaceId, project1);
		JSONObject clone1 = clone(clonePath1);
		String cloneLocation1 = clone1.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation1 = clone1.getString(GitConstants.KEY_BRANCH);

		// get project1 metadata
		WebRequest request = getGetRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
		String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

		// clone1: branch 'a'
		response = branch(branchesLocation1, "a");
		JSONObject branch = new JSONObject(response.getText());

		checkoutBranch(cloneLocation1, "a");

		// clone1: change
		JSONObject testTxt1 = getChild(project1, "test.txt");
		modifyFile(testTxt1, "branch 'a' change");

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "incoming branch 'a' commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push by remote branch
		JSONArray remoteBranchLocations = branch.getJSONArray(GitConstants.KEY_REMOTE);
		assertTrue(remoteBranchLocations.length() >= 1);
		String remoteBranchLocation = remoteBranchLocations.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION);
		ServerStatus pushStatus = push(remoteBranchLocation, Constants.HEAD, false);
		assertTrue(pushStatus.isOK());

		// clone1: get the remote branch name
		request = getGetRequest(remoteBranchLocation);
		response = webConversation.getResponse(request);
		JSONObject remoteBranch1 = new JSONObject(response.getText());
		String remoteBranchName1 = remoteBranch1.getString(ProtocolConstants.KEY_NAME);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project2"), null);
		IPath clonePath2 = getClonePath(workspaceId, project2);
		JSONObject clone2 = clone(clonePath2);
		String cloneLocation2 = clone2.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation2 = clone2.getString(GitConstants.KEY_BRANCH);

		// get project2 metadata
		request = getGetRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);

		// create a local branch 'a' tracking remoteBranchName1 and checkout 'a'
		response = branch(branchesLocation2, "a", remoteBranchName1);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		response = checkoutBranch(cloneLocation2, "a");
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject testTxt2 = getChild(project2, "test.txt");
		request = getGetRequest(testTxt2.getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("branch 'a' change", response.getText());
	}

	@Test
	public void testPushToDelete() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[][] clonePaths = createTestClonePairs(workspaceLocation);

		for (IPath[] clonePath : clonePaths) {
			// clone 1
			JSONObject clone1 = clone(clonePath[0]);
			String cloneLocation1 = clone1.getString(ProtocolConstants.KEY_LOCATION);
			String contentLocation1 = clone1.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation1 = clone1.getString(GitConstants.KEY_BRANCH);

			// clone 1 - get project1 metadata
			WebRequest request = getGetRequest(contentLocation1);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project1 = new JSONObject(response.getText());
			JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
			String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

			// clone 1 - create branch "a"
			response = branch(branchesLocation1, "a");
			JSONObject newBranch = new JSONObject(response.getText());
			JSONArray remoteBranchLocations1 = newBranch.getJSONArray(GitConstants.KEY_REMOTE);
			assertTrue(remoteBranchLocations1.length() >= 1);

			// clone 1 - checkout "a"
			final String newBranchName = "a";
			response = checkoutBranch(cloneLocation1, newBranchName);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - change
			JSONObject testTxt1 = getChild(project1, "test.txt");
			modifyFile(testTxt1, "clone1 change");

			// clone 1 - add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "clone1 change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone 1 - push "a"
			int i = 0;
			String remoteBranchName = remoteBranchLocations1.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(i).getString(ProtocolConstants.KEY_NAME);
			if (!remoteBranchName.equals("origin/a")) {
				i = 1;
				remoteBranchName = remoteBranchLocations1.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(i).getString(ProtocolConstants.KEY_NAME);
			}
			assertEquals("origin/a", remoteBranchName);
			String remoteBranchLocation1 = remoteBranchLocations1.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(i).getString(ProtocolConstants.KEY_LOCATION);
			ServerStatus pushStatus = push(remoteBranchLocation1, Constants.HEAD, false);
			assertEquals(true, pushStatus.isOK());

			// clone 1 - list remote branches - expect 2
			JSONObject remote1 = getRemote(gitRemoteUri1, 1, 0, Constants.DEFAULT_REMOTE_NAME);
			String remoteLocation1 = remote1.getString(ProtocolConstants.KEY_LOCATION);

			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation1);
			response = webConversation.getResponse(request);
			ServerStatus status = waitForTask(response);
			assertTrue(status.toString(), status.isOK());
			remote1 = status.getJsonData();
			JSONArray refsArray = remote1.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, refsArray.length());
			JSONObject ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + newBranchName, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			ref = refsArray.getJSONObject(1);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));

			// clone 2 
			JSONObject clone2 = clone(clonePath[1]);
			String cloneLocation2 = clone2.getString(ProtocolConstants.KEY_LOCATION);
			String contentLocation2 = clone2.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// clone 2 - get project2 metadata
			request = getGetRequest(contentLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project2 = new JSONObject(response.getText());
			JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);

			// clone 2 - check if the branch "a" is available
			JSONObject remote2 = getRemote(gitRemoteUri2, 1, 0, Constants.DEFAULT_REMOTE_NAME);
			String remoteLocation2 = remote2.getString(ProtocolConstants.KEY_LOCATION);

			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote2 = new JSONObject(response.getText());
			refsArray = remote2.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, refsArray.length());
			ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			ref = refsArray.getJSONObject(1);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + newBranchName, ref.getString(ProtocolConstants.KEY_FULL_NAME));
			String remoteBranchLocation2 = ref.getString(ProtocolConstants.KEY_LOCATION);

			// clone 2 - checkout branch "a"
			response = checkoutBranch(cloneLocation2, newBranchName);

			// clone 1 - delete remote branch "a"
			push(remoteBranchLocation1, "", false, false);

			// clone 1 - list remote branches - expect 1
			request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			remote1 = new JSONObject(response.getText());
			refsArray = remote1.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(1, refsArray.length());
			ref = refsArray.getJSONObject(0);
			assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_FULL_NAME));

			// clone 2 - fetch
			request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation2, true, false);
			response = webConversation.getResponse(request);
			status = waitForTask(response);
			assertFalse(status.toString(), status.isOK());

			// clone 2 - fetch task should fail
			JSONObject statusJson = status.toJSON();
			JSONObject result = statusJson.has("Result") ? statusJson.getJSONObject("Result") : statusJson;
			assertEquals("Error", result.getString("Severity"));
		}
	}

	@Test
	public void testPushFromLog() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// log
		JSONArray commitsArray = log(gitHeadUri);
		assertEquals(1, commitsArray.length());

		// change
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "incoming change");

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// log again
		JSONObject logResponse = logObject(gitHeadUri);
		assertEquals(2, logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());

		JSONObject toRefBranch = logResponse.getJSONObject(GitConstants.KEY_LOG_TO_REF);
		JSONArray remoteLocations = toRefBranch.getJSONArray(GitConstants.KEY_REMOTE);
		assertEquals(1, remoteLocations.length());
		String remoteBranchLocation = remoteLocations.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION);

		// push
		request = getPostGitRemoteRequest(remoteBranchLocation, Constants.HEAD, false, false);
		response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);
		assertTrue(status.toString(), status.isOK());
	}

	@Test
	public void testPushRejected() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath1 = getClonePath(workspaceId, project1);
		clone(clonePath1);

		// get project1 metadata
		WebRequest request = getGetRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.optString(GitConstants.KEY_INDEX);
		String gitHeadUri1 = gitSection1.optString(GitConstants.KEY_HEAD);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project2"), null);
		IPath clonePath2 = getClonePath(workspaceId, project2);
		clone(clonePath2);

		// get project2 metadata
		request = getGetRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

		// clone1: change
		JSONObject testTxt1 = getChild(project1, "test.txt");
		modifyFile(testTxt1, "clone1 change");

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "clone1 change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// clone2: change
		JSONObject testTxt2 = getChild(project2, "test.txt");
		modifyFile(testTxt2, "clone2 change");

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "clone2 change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
		JSONObject jo = pushStatus.getJsonData();
		assertEquals("Error", jo.get("Severity"));

		JSONArray up = (JSONArray) jo.get("Updates");
		assertEquals(1, up.length());

		Status pushResult = Status.valueOf((String) ((JSONObject) up.get(0)).get("Result"));
		assertEquals(Status.REJECTED_NONFASTFORWARD, pushResult);
	}

	@Test
	public void testPushRemoteRejected() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		// 1st commit
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "1st change");
		addFile(testTxt);
		commitFile(testTxt, "1st change commit", false);

		// push
		ServerStatus pushStatus = push(gitRemoteUri, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(IStatus.OK, pushStatus.getSeverity());

		// 2nd commit
		modifyFile(testTxt, "2nd change");
		addFile(testTxt);
		commitFile(testTxt, "2nd change commit", false);

		FileUtils.delete(new File(gitDir, Constants.DOT_GIT + "/objects/pack/"), FileUtils.RECURSIVE);

		pushStatus = push(gitRemoteUri, 1, 0, Constants.MASTER, Constants.HEAD, false);
		JSONObject jo = pushStatus.getJsonData();

		JSONArray up = (JSONArray) jo.get("Updates");
		assertEquals(1, up.length());

		assertEquals("Error", jo.get("Severity"));
		Status pushResult = Status.valueOf((String) ((JSONObject) up.get(0)).get("Result"));
		assertEquals(Status.REJECTED_OTHER_REASON, pushResult);

		assertTrue(((JSONObject) up.get(0)).getString("Message"), ((JSONObject) up.get(0)).getString("Message").matches("^object [\\da-f]+ missing$"));
	}

	@Test
	public void testForcedPush() throws Exception {
		// overwrite system settings, allow forced pushes, see bug 371881
		StoredConfig cfg = db.getConfig();
		cfg.setBoolean("receive", null, "denyNonFastforwards", false);
		cfg.save();

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[][] clonePaths = createTestClonePairs(workspaceLocation);

		for (IPath[] clonePath : clonePaths) {
			// clone1
			String contentLocation1 = clone(clonePath[0]).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project1 metadata
			WebRequest request = getGetRequest(contentLocation1);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project1 = new JSONObject(response.getText());
			JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
			String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

			// clone2
			String contentLocation2 = clone(clonePath[1]).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project2 metadata
			request = getGetRequest(contentLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project2 = new JSONObject(response.getText());
			JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
			String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

			// clone1: change
			JSONObject testTxt1 = getChild(project1, "test.txt");
			modifyFile(testTxt1, "clone1 change");

			// clone1: add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone1: commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "clone1 change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone1: push
			ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false);
			assertEquals(true, pushStatus.isOK());

			// clone2: change
			JSONObject testTxt2 = getChild(project2, "test.txt");
			modifyFile(testTxt2, "clone2 change");

			// clone2: add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone2: commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "clone2 change commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// clone2: push
			pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
			JSONObject jo = pushStatus.getJsonData();

			JSONArray up = (JSONArray) jo.get("Updates");
			assertEquals(1, up.length());

			assertEquals("Error", jo.get("Severity"));
			Status pushResult = Status.valueOf((String) ((JSONObject) up.get(0)).get("Result"));
			assertEquals(Status.REJECTED_NONFASTFORWARD, pushResult);

			// clone2: forced push
			pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false, true);
			jo = pushStatus.getJsonData();
			up = (JSONArray) jo.get("Updates");
			assertEquals(1, up.length());
			assertEquals("OK", ((JSONObject) up.get(0)).get("Result"));
		}
	}

	@Test
	public void testPushTags() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath1 = getClonePath(workspaceId, project1);
		clone(clonePath1);

		// get project1 metadata
		WebRequest request = getGetRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());

		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE);
		String gitTagUri1 = gitSection1.optString(GitConstants.KEY_TAG);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project2"), null);
		IPath clonePath2 = getClonePath(workspaceId, project2);
		clone(clonePath2);

		// get project2 metadata
		request = getGetRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());

		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);
		String gitTagUri2 = gitSection2.getString(GitConstants.KEY_TAG);

		// clone1: tag HEAD with 'tag'
		tag(gitTagUri1, "tag", Constants.HEAD);

		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, true);
		assertEquals(true, pushStatus.isOK());

		// clone2: list tags
		JSONArray tags = listTags(gitTagUri2);
		assertEquals(0, tags.length());

		// clone2: fetch + merge
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri2, 1, 0, Constants.MASTER);
		String remoteBranchLocation2 = remoteBranch.getString(ProtocolConstants.KEY_LOCATION);
		fetch(remoteBranchLocation2);
		String id = remoteBranch.getString(ProtocolConstants.KEY_ID);
		merge(gitHeadUri2, id);

		// clone2: list tags again
		tags = listTags(gitTagUri2);
		assertEquals(1, tags.length());
	}

	@Test
	public void testPushNewBranchToSecondaryRemote() throws Exception {
		// see bug 353557
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		for (int i = 0; i < clonePaths.length; i++) {
			IPath clonePath = clonePaths[i];
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			String remotesLocation = clone.getString(GitConstants.KEY_REMOTE);
			// expect only origin
			getRemote(remotesLocation, 1, 0, Constants.DEFAULT_REMOTE_NAME);

			// create secondary repository
			IPath randomLocation = createTempDir();
			randomLocation = randomLocation.addTrailingSeparator().append(Constants.DOT_GIT);
			File dotGitDir = randomLocation.toFile().getCanonicalFile();
			Repository db2 = FileRepositoryBuilder.create(dotGitDir);
			assertFalse(dotGitDir.exists());
			db2.create(false /* non bare */);
			toClose.add(db2);

			// dummy commit to start off master branch
			File dummyFile = new File(dotGitDir.getParentFile(), "test.txt");
			dummyFile.createNewFile();
			createFile(dummyFile.toURI(), "dummy");
			Git git2 = Git.wrap(db2);
			git2.add().addFilepattern(".").call();
			git2.commit().setMessage("dummy commit").call();

			// create remote
			response = addRemote(remotesLocation, "secondary", dotGitDir.getParentFile().toURI().toURL().toString());
			String secondaryRemoteLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
			assertNotNull(secondaryRemoteLocation);

			// list remotes
			request = getGetRequest(remotesLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject remotes = new JSONObject(response.getText());
			JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			// expect origin and new remote
			assertEquals(2, remotesArray.length());

			// create branch, checkout
			response = branch(branchesLocation, "branch");
			JSONObject branch = new JSONObject(response.getText());
			checkoutBranch(cloneLocation, "branch");

			// modify, add, commit
			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "branch change");
			addFile(testTxt);
			commitFile(testTxt, "branch commit", false);

			// push the new branch
			JSONArray remoteBranchLocations = branch.getJSONArray(GitConstants.KEY_REMOTE);
			assertEquals(2, remoteBranchLocations.length());
			String remoteBranchLocation = null;
			if (remoteBranchLocations.getJSONObject(0).getString(ProtocolConstants.KEY_NAME).equals("secondary")) {
				remoteBranchLocation = remoteBranchLocations.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION);
			} else if (remoteBranchLocations.getJSONObject(1).getString(ProtocolConstants.KEY_NAME).equals("secondary")) {
				remoteBranchLocation = remoteBranchLocations.getJSONObject(1).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION);
			}
			assertNotNull(remoteBranchLocation);
			ServerStatus pushStatus = push(remoteBranchLocation, Constants.HEAD, false);
			assertTrue(pushStatus.isOK());

			// see bug 354144
			request = getGetRequest(branch.getString(ProtocolConstants.KEY_LOCATION));
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			branch = new JSONObject(response.getText());
			remoteBranchLocations = branch.getJSONArray(GitConstants.KEY_REMOTE);
			// now, there should be only one remote branch returned
			assertTrue(remoteBranchLocations.length() >= 1);
			assertEquals("secondary", remoteBranchLocations.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
			assertEquals("secondary/branch", remoteBranchLocations.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_NAME));

			// clone the secondary branch and check if the new branch is there
			JSONObject secondProject = createProjectOrLink(workspaceLocation, getMethodName().concat("-second-").concat(Integer.toString(i)), null);
			IPath secondClonePath = getClonePath(workspaceId, secondProject);
			URIish uri = new URIish(dotGitDir.getParentFile().toURI().toURL());
			JSONObject clone2 = clone(uri, null, secondClonePath, null, null, null);
			String cloneLocation2 = clone2.getString(ProtocolConstants.KEY_LOCATION);
			String branchesLocation2 = clone2.getString(GitConstants.KEY_BRANCH);

			String remotesLocation2 = clone2.getString(GitConstants.KEY_REMOTE);
			// expecting two branches, second named "branch"
			JSONObject remoteBranch2 = getRemoteBranch(remotesLocation2, 2, 1, "branch");
			String remoteBranchName2 = remoteBranch2.getString(ProtocolConstants.KEY_NAME);

			// create tracking branch and check it out
			response = branch(branchesLocation2, null /* deduct from the remote branch name */, remoteBranchName2);
			JSONObject branch2 = new JSONObject(response.getText());
			response = checkoutBranch(cloneLocation2, branch2.getString(ProtocolConstants.KEY_NAME));
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check file content
			String cloneContentLocation2 = clone2.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			request = getGetRequest(cloneContentLocation2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder2 = new JSONObject(response.getText());
			JSONObject testTxt2 = getChild(folder2, "test.txt");
			assertEquals("branch change", getFileContent(testTxt2));
		}
	}

	static WebRequest getPostGitRemoteRequest(String location, String srcRef, boolean tags, boolean force) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		if (srcRef != null)
			body.put(GitConstants.KEY_PUSH_SRC_REF, srcRef);
		body.put(GitConstants.KEY_PUSH_TAGS, tags);
		body.put(GitConstants.KEY_FORCE, force);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getPostGitRemoteRequest(String location, String srcRef, boolean tags, boolean force, String name, String kh, byte[] privk, byte[] pubk, byte[] p) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();

		body.put(ProtocolConstants.KEY_NAME, name);
		if (kh != null)
			body.put(GitConstants.KEY_KNOWN_HOSTS, kh);
		if (privk != null)
			body.put(GitConstants.KEY_PRIVATE_KEY, new String(privk));
		if (pubk != null)
			body.put(GitConstants.KEY_PUBLIC_KEY, new String(pubk));
		if (p != null)
			body.put(GitConstants.KEY_PASSPHRASE, new String(p));

		if (srcRef != null)
			body.put(GitConstants.KEY_PUSH_SRC_REF, srcRef);
		body.put(GitConstants.KEY_PUSH_TAGS, tags);
		body.put(GitConstants.KEY_FORCE, force);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
