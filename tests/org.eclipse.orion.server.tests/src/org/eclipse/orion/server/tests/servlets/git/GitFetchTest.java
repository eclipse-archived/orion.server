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

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.URIish;
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

public class GitFetchTest extends GitTest {

	@BeforeClass
	public static void prepareSsh() {
		readSshProperties();
	}

	@Test
	public void testFetchRemoteBranchUpToDate() throws Exception {
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
		assertNotNull(remoteLocation);

		// get remote details
		JSONObject details = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		String refId = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation = details.getString(ProtocolConstants.KEY_LOCATION);

		// fetch
		fetch(remoteBranchLocation);

		// get remote details again
		String newRefId = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER).getString(ProtocolConstants.KEY_ID);
		// up to date
		assertEquals(refId, newRefId);
	}

	@Test
	public void testFetchRemoteUpToDate() throws Exception {
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

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		// list remotes
		JSONObject remote = getRemote(gitRemoteUri, 1, 0, Constants.DEFAULT_REMOTE_NAME);
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);

		// get remote details
		JSONObject details = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		String refId = details.getString(ProtocolConstants.KEY_ID);

		// fetch
		fetch(remoteLocation);

		// get remote details again
		String newRefId = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER).getString(ProtocolConstants.KEY_ID);
		// up to date
		assertEquals(refId, newRefId);
	}

	@Test
	public void testPushCommitAndFetch() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath1 = getClonePath(workspaceId, project1);
		String contentLocation1 = clone(clonePath1).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// get project1 metadata
		WebRequest request = getGetRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

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

		// clone2: change
		request = getPutFileRequest(clonePath2 + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		ServerStatus pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		JSONObject masterDetails = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String refId1 = masterDetails.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation = masterDetails.getString(ProtocolConstants.KEY_LOCATION);

		// clone1: fetch
		fetch(remoteBranchLocation);

		masterDetails = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String newRefId1 = masterDetails.getString(ProtocolConstants.KEY_ID);
		assertFalse(newRefId1.equals(refId1));

		// clone1: log master..origin/master
		// TODO replace with tests methods from GitLogTest, bug 340051
		Repository db1 = getRepositoryForContentLocation(contentLocation1);
		ObjectId master = db1.resolve(Constants.MASTER);
		ObjectId originMaster = db1.resolve(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER);
		Git git = Git.wrap(db1);
		Iterable<RevCommit> commits = git.log().addRange(master, originMaster).call();
		int c = 0;
		for (RevCommit commit : commits) {
			assertEquals("incoming change commit", commit.getFullMessage());
			c++;
		}
		// a single incoming commit
		assertEquals(1, c);
	}

	@Test
	public void testPushAndFetchWithPrivateKeyAndPassphrase() throws Exception {

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
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone2: create
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project2"), null);
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
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
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

		// clone2: change
		request = getPutFileRequest(projectId2 + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		ServerStatus pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false, null, knownHosts2, privateKey, publicKey, passphrase, true);
		assertEquals(true, pushStatus.isOK());

		JSONObject details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String refId1 = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation = details.getString(ProtocolConstants.KEY_LOCATION);

		// clone1: fetch
		fetch(remoteBranchLocation, null, knownHosts2, privateKey, publicKey, passphrase, true);

		details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String newRefId1 = details.getString(ProtocolConstants.KEY_ID);
		assertFalse(newRefId1.equals(refId1));

		// clone1: log master..origin/master
		// TODO replace with tests methods from GitLogTest, bug 340051
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation1);
		ObjectId master = db1.resolve(Constants.MASTER);
		ObjectId originMaster = db1.resolve(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER);
		Git git = Git.wrap(db1);
		Iterable<RevCommit> commits = git.log().addRange(master, originMaster).call();
		int c = 0;
		for (RevCommit commit : commits) {
			assertEquals("incoming change commit", commit.getFullMessage());
			c++;
		}
		// a single incoming commit
		assertEquals(1, c);
	}

	@Test
	public void testFetchRemote() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		// clone1: create
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath1 = getClonePath(workspaceId, project1);
		JSONObject clone1 = clone(clonePath1);
		String cloneContentLocation1 = clone1.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String cloneLocation1 = clone1.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation1 = clone1.getString(GitConstants.KEY_BRANCH);

		// get project1 metadata
		WebRequest request = getGetRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
		String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);

		// clone1: branch 'a'
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation1);
		Git git1 = Git.wrap(db1);
		branch(branchesLocation1, "a");

		// clone1: push all
		// TODO: replace with REST API when bug 339115 is fixed
		git1.push().setPushAll().call();

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

		// clone1: switch to 'a'
		assertBranchExist(git1, "a");
		checkoutBranch(cloneLocation1, "a");

		// clone1: change
		request = getPutFileRequest(clonePath1 + "/test.txt", "branch 'a' change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "incoming branch 'a' commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 2, 0, "a", Constants.HEAD, false);
		assertTrue(pushStatus.isOK());

		// clone1: switch to 'master'
		checkoutBranch(cloneLocation1, Constants.MASTER);

		// clone1: change
		request = getPutFileRequest(clonePath1 + "/test.txt", "branch 'master' change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "incoming branch 'master' commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		pushStatus = push(gitRemoteUri1, 2, 0, Constants.MASTER, Constants.HEAD, false);
		assertTrue(pushStatus.isOK());

		// clone2: get remote details
		JSONObject masterDetails = getRemoteBranch(gitRemoteUri2, 2, 0, Constants.MASTER);
		String masterOldRefId = masterDetails.getString(ProtocolConstants.KEY_ID);
		JSONObject aDetails = getRemoteBranch(gitRemoteUri2, 2, 1, "a");
		String aOldRefId = aDetails.getString(ProtocolConstants.KEY_ID);

		// clone2: fetch all: 'master' and 'a'
		JSONObject remote = getRemote(gitRemoteUri2, 1, 0, Constants.DEFAULT_REMOTE_NAME);
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);
		fetch(remoteLocation);

		// clone2: assert both remote branches have new content
		masterDetails = getRemoteBranch(gitRemoteUri2, 2, 0, Constants.MASTER);
		String newRefId = masterDetails.getString(ProtocolConstants.KEY_ID);
		assertFalse(masterOldRefId.equals(newRefId));

		aDetails = getRemoteBranch(gitRemoteUri2, 2, 1, "a");
		newRefId = aDetails.getString(ProtocolConstants.KEY_ID);
		assertFalse(aOldRefId.equals(newRefId));
	}

	@Test
	public void testFetchRemoteBranch() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		// clone1: create
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath1 = getClonePath(workspaceId, project1);
		JSONObject clone1 = clone(clonePath1);
		String cloneContentLocation1 = clone1.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String cloneLocation1 = clone1.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation1 = clone1.getString(GitConstants.KEY_BRANCH);

		// get project1 metadata
		WebRequest request = getGetRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone1: branch 'a'
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation1);
		Git git1 = Git.wrap(db1);
		branch(branchesLocation1, "a");

		// clone1: push all
		// TODO: replace with REST API when bug 339115 is fixed
		git1.push().setPushAll().call();

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

		// clone1: switch to 'a'
		assertBranchExist(git1, "a");
		checkoutBranch(cloneLocation1, "a");

		// clone1: change, add, commit
		JSONObject testTxt1 = getChild(project1, "test.txt");
		modifyFile(testTxt1, "branch 'a' change");
		addFile(testTxt1);
		commitFile(testTxt1, "incoming branch 'a' commit", false);

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 2, 0, "a", Constants.HEAD, false);
		assertTrue(pushStatus.isOK());

		// clone1: switch to 'master'
		checkoutBranch(cloneLocation1, Constants.MASTER);

		// clone1: change
		testTxt1 = getChild(project1, "test.txt");
		modifyFile(testTxt1, "branch 'master' change");
		addFile(testTxt1);
		commitFile(testTxt1, "incoming branch 'master' commit", false);

		// clone1: push
		push(gitRemoteUri1, 2, 0, Constants.MASTER, Constants.HEAD, false);

		// clone2: get remote details
		JSONObject aDetails = getRemoteBranch(gitRemoteUri2, 2, 1, "a");
		String aOldRefId = aDetails.getString(ProtocolConstants.KEY_ID);
		String aBranchLocation = aDetails.getString(ProtocolConstants.KEY_LOCATION);
		JSONObject masterDetails = getRemoteBranch(gitRemoteUri2, 2, 0, Constants.MASTER);
		String masterOldRefId = masterDetails.getString(ProtocolConstants.KEY_ID);

		// clone2: fetch 'a'
		fetch(aBranchLocation);

		// clone2: check for new content on 'a'
		masterDetails = getRemoteBranch(gitRemoteUri2, 2, 1, "a");
		String newRefId = masterDetails.getString(ProtocolConstants.KEY_ID);
		assertFalse(aOldRefId.equals(newRefId));

		// clone2: assert nothing new on 'master'
		masterDetails = getRemoteBranch(gitRemoteUri2, 2, 0, Constants.MASTER);
		newRefId = masterDetails.getString(ProtocolConstants.KEY_ID);
		assertEquals(masterOldRefId, newRefId);
	}

	@Test
	public void testForcedFetch() throws Exception {
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
			String project1Location = project1.getString(ProtocolConstants.KEY_LOCATION);
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
			String project2Location = project2.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
			String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
			String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
			String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

			// clone1: change
			request = getPutFileRequest(project1Location + "/test.txt", "clone1 change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

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
			request = getPutFileRequest(project2Location + "/test.txt", "clone2 change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

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

			// clone1: fetch master
			JSONObject details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
			String remoteBranchLocation = details.getString(ProtocolConstants.KEY_LOCATION);
			String oldRefId = details.getString(ProtocolConstants.KEY_ID);

			//JSONObject newDetails = fetch(remoteBranchLocation);
			request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation, true, false);
			response = webConversation.getResponse(request);
			ServerStatus status = waitForTask(response);

			// get remote (branch) details again
			request = GitRemoteTest.getGetGitRemoteRequest(remoteBranchLocation);
			response = webConversation.getResponse(request);
			status = waitForTask(response);
			assertTrue(status.toString(), status.isOK());
			JSONObject newDetails = status.getJsonData();

			// assert nothing new on 'master'
			String newRefId = newDetails.getString(ProtocolConstants.KEY_ID);
			assertEquals(oldRefId, newRefId);

			// clone1: forced fetch master
			newDetails = fetch(remoteBranchLocation, true);

			// assert that fetch succeed and something new on 'master'
			newRefId = newDetails.getString(ProtocolConstants.KEY_ID);
			assertFalse(oldRefId.equals(newRefId));
		}
	}

	static WebRequest getPostGitRemoteRequest(String location, boolean fetch, boolean force) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_FETCH, Boolean.toString(fetch));
		body.put(GitConstants.KEY_FORCE, force);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getPostGitRemoteRequest(String location, boolean fetch, boolean force, String name, String kh, byte[] privk, byte[] pubk, byte[] p) throws JSONException, UnsupportedEncodingException {
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

		body.put(GitConstants.KEY_FETCH, Boolean.toString(fetch));
		body.put(GitConstants.KEY_FORCE, force);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
