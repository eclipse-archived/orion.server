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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.CherryPickResult.CherryPickStatus;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitCherryPickTest extends GitTest {
	@Test
	public void testCherryPick() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

			// get project/folder metadata
			WebRequest request = getGetFilesRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			String folderLocation = folder.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			// modify
			request = getPutFileRequest(folderLocation + "test.txt", "first line\nsec. line\nthird line\n");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "lines in test.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// create new file
			String fileName = "new.txt";
			request = getPostFilesRequest(folderLocation + "/", getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			// add all
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "added new.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// modify
			request = getPutFileRequest(folderLocation + "test.txt", "first line\nsec. line\nthird line\nfourth line\n");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "enlarged test.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// modify
			request = getPutFileRequest(folderLocation + "test.txt", "first line\nsecond line\nthird line\nfourth line\n");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "fixed test.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// remember starting point and commit to cherry-pick
			JSONArray commitsArray = log(gitHeadUri, false);
			assertEquals(5, commitsArray.length());
			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals("fixed test.txt", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			String toCherryPick = commit.getString(ProtocolConstants.KEY_NAME);
			commit = commitsArray.getJSONObject(3);
			assertEquals("lines in test.txt", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			String startingPoint = commit.getString(ProtocolConstants.KEY_NAME);

			// branch
			response = branch(branchesLocation, "side", startingPoint);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			response = checkoutBranch(cloneLocation, "side");
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// modify
			request = getPutFileRequest(folderLocation + "test.txt", "first line\nsec. line\nthird line\nfeature++\n");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "enhanced test.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// CHERRY-PICK
			JSONObject cherryPick = cherryPick(gitHeadUri, toCherryPick);
			CherryPickStatus mergeResult = CherryPickStatus.valueOf(cherryPick.getString(GitConstants.KEY_RESULT));
			assertEquals(CherryPickStatus.OK, mergeResult);

			// 'new.txt' should be not there
			request = getGetFilesRequest(folderLocation + "/new.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

			// check cherry-pick result in the file
			request = getGetFilesRequest(folderLocation + "/test.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("first line\nsecond line\nthird line\nfeature++\n", response.getText());

			// check log
			commitsArray = log(gitHeadUri, false);
			assertEquals(4, commitsArray.length());
			commit = commitsArray.getJSONObject(0);
			assertEquals("fixed test.txt", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			commit = commitsArray.getJSONObject(1);
			assertEquals("enhanced test.txt", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			commit = commitsArray.getJSONObject(2);
			assertEquals("lines in test.txt", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			commit = commitsArray.getJSONObject(3);
			assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
		}
	}

	// TODO: add more tests for cherry-picking that failed

	private JSONObject cherryPick(String gitHeadUri, String toCherryPick) throws JSONException, IOException, SAXException {
		assertCommitUri(gitHeadUri);
		WebRequest request = getPostGitCherryPickRequest(gitHeadUri, toCherryPick);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	private static WebRequest getPostGitCherryPickRequest(String location, String toCherryPick) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_CHERRY_PICK, toCherryPick);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
