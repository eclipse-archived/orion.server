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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitPullTest extends GitTest {

	@Test
	public void testPullRemoteUpToDate() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		JSONObject clone = clone(clonePath);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// get HEAD
		JSONArray commitsArray = log(gitHeadUri);
		String headSha1 = commitsArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME);

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

		// pull
		pull(cloneLocation);

		// get remote details again
		String newRefId = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER).getString(ProtocolConstants.KEY_ID);
		// up to date
		assertEquals(refId, newRefId);

		// get the current branch
		request = getGetRequest(gitHeadUri);
		response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);
		assertTrue(status.toString(), status.isOK());
		JSONObject newHead = status.getJsonData();
		String newHeadSha1 = newHead.getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_NAME);
		assertEquals(headSha1, newHeadSha1);
	}

	@Test
	public void testPullRemote() throws Exception {
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
		JSONObject clone2 = clone(clonePath2);
		String cloneLocation2 = clone2.getString(ProtocolConstants.KEY_LOCATION);

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
		JSONObject masterDetails = getRemoteBranch(gitRemoteUri2, 2, 0, Constants.MASTER);
		String masterOldRefId = masterDetails.getString(ProtocolConstants.KEY_ID);

		// clone2: pull
		pull(cloneLocation2);

		// clone2: check for new content on 'a'
		masterDetails = getRemoteBranch(gitRemoteUri2, 2, 1, "a");
		String newRefId = masterDetails.getString(ProtocolConstants.KEY_ID);
		assertFalse(aOldRefId.equals(newRefId));

		// clone2: assert nothing new on 'master'
		masterDetails = getRemoteBranch(gitRemoteUri2, 2, 0, Constants.MASTER);
		newRefId = masterDetails.getString(ProtocolConstants.KEY_ID);
		assertFalse(masterOldRefId.equals(newRefId));

		// make sure the change has been pulled into the current branch
		JSONObject testTxt2 = getChild(project2, "test.txt");
		assertEquals("branch 'master' change", getFileContent(testTxt2));
	}

	static WebRequest getPostGitRemoteRequest(String location, boolean force) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_PULL, Boolean.TRUE.toString());
		body.put(GitConstants.KEY_FORCE, force);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
