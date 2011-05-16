/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
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

public class GitLogTest extends GitTest {

	@Test
	public void testLog() throws IOException, SAXException, JSONException, URISyntaxException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// modify
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "first change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit1
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit1", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modify again
		request = getPutFileRequest(projectId + "/test.txt", "second change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit2
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit2", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// get the full log
		JSONArray commitsArray = log(gitCommitUri, false);
		assertEquals(3, commitsArray.length());

		JSONObject commit = commitsArray.getJSONObject(0);
		assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = commitsArray.getJSONObject(1);
		assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = commitsArray.getJSONObject(2);
		assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
		String initialGitCommitName = commit.getString(ProtocolConstants.KEY_NAME);
		String initialGitCommitURI = gitCommitUri.replaceAll("HEAD", initialGitCommitName);

		// prepare a scoped log location
		request = getPostForScopedLogRequest(initialGitCommitURI, "HEAD");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String newGitCommitUri = response.getHeaderField(ProtocolConstants.KEY_LOCATION);

		// get a scoped log
		commitsArray = log(newGitCommitUri, false);
		assertEquals(2, commitsArray.length());

		commit = commitsArray.getJSONObject(0);
		assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = commitsArray.getJSONObject(1);
		assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
	}

	@Test
	public void testLogWithRemote() throws IOException, SAXException, JSONException, URISyntaxException {
		String contentLocation = clone(null);
		JSONObject project = linkProject(contentLocation, getMethodName());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		JSONArray commitsArray = log(gitCommitUri, true);
		assertEquals(1, commitsArray.length());
	}

	@Test
	public void testLogWithTag() throws IOException, SAXException, JSONException, URISyntaxException {
		String contentLocation = clone(null);
		JSONObject project = linkProject(contentLocation, getMethodName());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		JSONArray commitsArray = log(gitCommitUri, true);
		assertEquals(1, commitsArray.length());

		String commitUri = commitsArray.getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION);
		JSONObject updatedCommit = tag(commitUri, "tag");
		JSONArray tagsAndBranchesArray = updatedCommit.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, tagsAndBranchesArray.length());
		assertEquals(Constants.R_TAGS + "tag", tagsAndBranchesArray.get(0));

		commitsArray = log(gitCommitUri, true);
		assertEquals(1, commitsArray.length());

		tagsAndBranchesArray = commitsArray.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, tagsAndBranchesArray.length());
		assertEquals(Constants.R_TAGS + "tag", tagsAndBranchesArray.get(0));
	}

	@Test
	@Ignore("not implemented yet")
	public void testLogWithBranch() throws IOException, SAXException, JSONException, JGitInternalException, GitAPIException, URISyntaxException {
		String contentLocation = clone(null);
		JSONObject project = linkProject(contentLocation, getMethodName());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		JSONArray commitsArray = log(gitCommitUri, true);
		assertEquals(1, commitsArray.length());

		branch(contentLocation, "branch");

		commitsArray = log(gitCommitUri, true);
		assertEquals(1, commitsArray.length());

		JSONArray tagsAndBranchesArray = commitsArray.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, tagsAndBranchesArray.length());
		assertEquals(Constants.R_HEADS + "branch", tagsAndBranchesArray.get(0));
	}

	@Test
	public void testLogFile() throws IOException, SAXException, JSONException, URISyntaxException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// modify
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "test.txt change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit1
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit1", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modify again
		request = getPutFileRequest(projectId + "/folder/folder.txt", "folder.txt change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "folder/folder.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit2
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit2", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// get log for file
		// TODO: don't create URIs out of thin air
		JSONArray commitsArray = log(gitCommitUri + "test.txt", false);
		assertEquals(2, commitsArray.length());

		JSONObject commit = commitsArray.getJSONObject(0);
		assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = commitsArray.getJSONObject(1);
		assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
	}

	private static WebRequest getPostForScopedLogRequest(String location, String newCommit) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_COMMIT_NEW, newCommit);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
