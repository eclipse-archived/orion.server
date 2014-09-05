/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.api.CherryPickResult.CherryPickStatus;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
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

			String folderLocation = folder.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "first line\nsec. line\nthird line\n");

			addFile(testTxt);

			commitFile(testTxt, "lines in test.txt", false);

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
			modifyFile(testTxt, "first line\nsec. line\nthird line\nfourth line\n");

			// add
			addFile(testTxt);

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "enlarged test.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// modify
			modifyFile(testTxt, "first line\nsecond line\nthird line\nfourth line\n");

			// add
			addFile(testTxt);

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "fixed test.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// remember starting point and commit to cherry-pick
			JSONArray commitsArray = log(gitHeadUri);
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
			modifyFile(testTxt, "first line\nsec. line\nthird line\nfeature++\n");

			// add
			addFile(testTxt);

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "enhanced test.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// CHERRY-PICK
			JSONObject cherryPick = cherryPick(gitHeadUri, toCherryPick);
			CherryPickStatus mergeResult = CherryPickStatus.valueOf(cherryPick.getString(GitConstants.KEY_RESULT));
			assertEquals(CherryPickStatus.OK, mergeResult);
			assertTrue(cherryPick.getBoolean(GitConstants.KEY_HEAD_UPDATED));

			// try again, should be OK, but nothing changed
			cherryPick = cherryPick(gitHeadUri, toCherryPick);
			mergeResult = CherryPickStatus.valueOf(cherryPick.getString(GitConstants.KEY_RESULT));
			assertEquals(CherryPickStatus.OK, mergeResult);
			assertFalse(cherryPick.getBoolean(GitConstants.KEY_HEAD_UPDATED));

			// 'new.txt' should be not there
			JSONObject newTxt = getChild(folder, "new.txt");
			assertNull(newTxt);

			// check cherry-pick result in the file
			request = getGetRequest(testTxt.getString(ProtocolConstants.KEY_LOCATION));
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("first line\nsecond line\nthird line\nfeature++\n", response.getText());

			// check log
			commitsArray = log(gitHeadUri);
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

	// TODO: add more tests for cherry-picking that failed, see bug 351826

	private JSONObject cherryPick(String gitHeadUri, String toCherryPick) throws JSONException, IOException, SAXException {
		assertCommitUri(gitHeadUri);
		WebRequest request = getPostGitCherryPickRequest(gitHeadUri, toCherryPick);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	private static WebRequest getPostGitCherryPickRequest(String location, String toCherryPick) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_CHERRY_PICK, toCherryPick);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
