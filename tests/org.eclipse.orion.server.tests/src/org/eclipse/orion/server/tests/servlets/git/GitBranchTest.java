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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitBranchTest extends GitTest {
	@Test
	public void testListBranches() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		JSONObject clone = clone(clonePath);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		branch(branchesLocation, "a");
		branch(branchesLocation, "z");

		JSONObject branches = listBranches(branchesLocation);
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(3, branchesArray.length());

		// validate branch metadata
		JSONObject branch = branchesArray.getJSONObject(0);
		assertEquals(Constants.MASTER, branch.getString(ProtocolConstants.KEY_NAME));
		assertBranchUri(branch.getString(ProtocolConstants.KEY_LOCATION));
		assertTrue(branch.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false));
		branch = branchesArray.getJSONObject(1);
		assertEquals("z", branch.getString(ProtocolConstants.KEY_NAME));
		// assert properly sorted, current branch is first, then other branches sorted by timestamp
		long lastTime = Long.MAX_VALUE;
		for (int i = 1; i < branchesArray.length(); i++) {
			long t = branchesArray.getJSONObject(i).getLong(ProtocolConstants.KEY_LOCAL_TIMESTAMP);
			assertTrue(t <= lastTime);
			lastTime = t;
		}
	}

	@Test
	public void testAddRemoveBranch() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		JSONObject clone = clone(clonePath);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		String[] branchNames = {"dev", "change/1/1", "working@bug1"};
		for (String branchName : branchNames) {
			// create branch
			WebResponse response = branch(branchesLocation, branchName);
			String branchLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

			// check details
			WebRequest request = getGetRequest(branchLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			JSONObject branches = listBranches(branchesLocation);
			JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, branchesArray.length());
			JSONObject branch0 = branchesArray.getJSONObject(0);
			JSONObject branch1 = branchesArray.getJSONObject(1);
			if (branch0.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false))
				assertFalse(branch1.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false));
			else
				assertTrue(branch1.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false));

			// remove branch
			request = getDeleteGitBranchRequest(branchLocation);
			response = webConversation.getResponse(request);
			assertTrue(HttpURLConnection.HTTP_OK == response.getResponseCode() || HttpURLConnection.HTTP_ACCEPTED == response.getResponseCode());

			// list branches again, make sure it's gone
			request = getGetRequest(branchesLocation);
			response = webConversation.getResponse(request);
			ServerStatus status = waitForTask(response);
			assertTrue(status.toString(), status.isOK());
			branches = status.getJsonData();
			branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(1, branchesArray.length());
			JSONObject branch = branchesArray.getJSONObject(0);
			assertTrue(branch.optBoolean(GitConstants.KEY_BRANCH_CURRENT, false));
		}
	}

	@Test
	public void testCreateTrackingBranch() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// overwrite user settings, do not rebase when pulling, see bug 372489
			StoredConfig cfg = getRepositoryForContentLocation(cloneContentLocation).getConfig();
			cfg.setBoolean(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_REBASE, false);
			cfg.save();

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());

			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE);

			// create local branch tracking origin/master
			final String BRANCH_NAME = "a";
			final String REMOTE_BRANCH = Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER;

			branch(branchesLocation, BRANCH_NAME, REMOTE_BRANCH);

			// modify, add, commit
			JSONObject testTxt = getChild(project, "test.txt");
			modifyFile(testTxt, "some change");
			addFile(testTxt);
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// push
			ServerStatus pushStatus = push(gitRemoteUri, 1, 0, Constants.MASTER, Constants.HEAD, false);
			assertEquals(true, pushStatus.isOK());

			// TODO: replace with RESTful API for git pull when available
			// try to pull - up to date status is expected
			Git git = new Git(getRepositoryForContentLocation(cloneContentLocation));
			PullResult pullResults = git.pull().call();
			assertEquals(Constants.DEFAULT_REMOTE_NAME, pullResults.getFetchedFrom());
			assertEquals(MergeStatus.ALREADY_UP_TO_DATE, pullResults.getMergeResult().getMergeStatus());
			assertNull(pullResults.getRebaseResult());

			// checkout branch which was created a moment ago
			response = checkoutBranch(cloneLocation, BRANCH_NAME);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// TODO: replace with RESTful API for git pull when available
			// try to pull again - now fast forward update is expected
			pullResults = git.pull().call();
			assertEquals(Constants.DEFAULT_REMOTE_NAME, pullResults.getFetchedFrom());
			assertEquals(MergeStatus.FAST_FORWARD, pullResults.getMergeResult().getMergeStatus());
			assertNull(pullResults.getRebaseResult());
		}
	}

	@Test
	public void testCheckoutAmbiguousName() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "tagged");
			addFile(testTxt);
			commitFile(testTxt, "tagged", false);

			// tag HEAD with 'tag'
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);
			tag(gitTagUri, "tag", Constants.HEAD);

			modifyFile(testTxt, "branched");
			addFile(testTxt);
			commitFile(testTxt, "branched", false);

			// use the same name for the new branch
			branch(branchesLocation, "tag");

			modifyFile(testTxt, "head");
			addFile(testTxt);
			commitFile(testTxt, "head", false);

			// checkout branch
			response = checkoutBranch(cloneLocation, "tag");
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("branched", getFileContent(testTxt));

			// checkout tag
			response = checkoutTag(cloneLocation, "tag");
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("tagged", getFileContent(testTxt));
		}
	}

	static JSONObject getCurrentBranch(JSONObject branches) throws JSONException {
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		for (int i = 0; i < branchesArray.length(); i++) {
			JSONObject branch = branchesArray.getJSONObject(i);
			if (branch.getBoolean(GitConstants.KEY_BRANCH_CURRENT))
				return branch;
		}
		return null;
	}

	private WebRequest getDeleteGitBranchRequest(String location) {
		String requestURI = toAbsoluteURI(location);
		WebRequest request = new DeleteMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
