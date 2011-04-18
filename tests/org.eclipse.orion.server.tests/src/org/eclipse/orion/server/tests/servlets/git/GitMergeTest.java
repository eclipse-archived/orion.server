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
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitMergeTest extends GitTest {
	@Test
	public void testMergeSelf() throws IOException, SAXException, JSONException, URISyntaxException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
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
		WebRequest request = getPostGitMergeRequest(gitCommitUri, Constants.MASTER);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testMerge() throws IOException, SAXException, JSONException, URISyntaxException, JGitInternalException, GitAPIException {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone: create
		URIish uri = new URIish(gitDir.toURL());
		String name = null;
		WebRequest request = GitCloneTest.getPostGitCloneRequest(uri, name);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// clone: validate the clone metadata
		response = webConversation.getResponse(getCloneRequest(cloneLocation));
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);

		// clone: link
		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		// create branch 'a'
		FileRepository db1 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation)), Constants.DOT_GIT));
		Git git = new Git(db1);
		Ref aBranch = git.branchCreate().setName("a").call();
		assertEquals(Constants.R_HEADS + "a", aBranch.getName());

		// checkout 'a'
		GitRemoteTest.ensureOnBranch(git, "a");

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
		request = getPostGitMergeRequest(gitCommitUri, "a");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

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
	public void testMergeAlreadyUpToDate() throws IOException, SAXException, JSONException, URISyntaxException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		// TODO: don't create URIs out of thin air
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change in master");
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
		request = getPostGitMergeRequest(gitCommitUri, Constants.MASTER);
		response = webConversation.getResponse(request);
		// "Already up-to-date"
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

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
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone: create
		URIish uri = new URIish(gitDir.toURL());
		String name = null;
		WebRequest request = GitCloneTest.getPostGitCloneRequest(uri, name);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// clone: validate the clone metadata
		response = webConversation.getResponse(getCloneRequest(cloneLocation));
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);

		// clone: link
		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		// create branch 'a'
		FileRepository db1 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation)), Constants.DOT_GIT));
		Git git = new Git(db1);
		Ref aBranch = git.branchCreate().setName("a").call();
		assertEquals(Constants.R_HEADS + "a", aBranch.getName());

		// checkout 'a'
		GitRemoteTest.ensureOnBranch(git, "a");

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
		request = getPostGitMergeRequest(gitCommitUri, "a");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

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
	public void testMergeRemote() throws IOException, SAXException, JSONException, URISyntaxException, JGitInternalException, GitAPIException {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1: create
		URIish uri = new URIish(gitDir.toURL());
		String name = null;
		WebRequest request = GitCloneTest.getPostGitCloneRequest(uri, name);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		//validate the clone metadata
		response = webConversation.getResponse(getCloneRequest(cloneLocation));
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation1 = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation1);

		// clone1: link
		ServletTestingSupport.allowedPrefixes = contentLocation1;
		String projectName1 = getMethodName() + "1";
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation1);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName1 != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName1);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project1 = new JSONObject(response.getText());
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone2: create
		request = GitCloneTest.getPostGitCloneRequest(uri, name);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		cloneLocation = waitForTaskCompletion(taskLocation);

		//validate the clone metadata
		response = webConversation.getResponse(getCloneRequest(cloneLocation));
		clone = new JSONObject(response.getText());
		String contentLocation2 = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation2);

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
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
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
		String remoteLocation1 = remote.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteLocation1);

		// clone1: get remote details
		JSONObject details = GitRemoteTest.getRemoteBranch(remoteLocation1, 1, 0, Constants.MASTER);
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
		// TODO: replace with REST API for git push once bug 339115 is fixed
		Repository db2 = getRepositoryForContentLocation(contentLocation2);
		Git git = new Git(db2);
		git.push().call();

		// clone1: fetch
		request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation1, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		waitForTaskCompletion(taskLocation);

		// clone1: get remote details again
		JSONObject remoteBranch = GitRemoteTest.getRemoteBranch(remoteLocation1, 1, 0, Constants.MASTER);
		String newRefId1 = remoteBranch.getString(ProtocolConstants.KEY_ID);
		// an incoming commit
		assertFalse(refId1.equals(newRefId1));

		// clone1: merge into HEAD, "git merge origin/master"
		//String gitCommitUri = remoteBranch.getString(GitConstants.KEY_COMMIT);
		// TODO: should fail when POSTing to the above URI, see bug 342845

		String gitCommitUri = remoteBranch.getString(GitConstants.KEY_HEAD);
		assertNotNull(gitCommitUri);

		request = getPostGitMergeRequest(gitCommitUri, newRefId1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetFilesRequest(projectId1 + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("incoming change", response.getText());
	}

	static WebRequest getPostGitMergeRequest(String location, String commit) throws JSONException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_MERGE, commit);
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
