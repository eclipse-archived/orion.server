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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.io.IOException;
import java.net.HttpURLConnection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Tests for servlet implementation of Git blame.
 */
public class GitBlameTest extends GitTest {

	@Test
	public void testBlameNoCommits() throws IOException, SAXException, JSONException, CoreException {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			//clone a repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			//get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			// get blameUri
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitBlameUri = gitSection.getString(GitConstants.KEY_BLAME);

			// blame request
			request = getGetGitBlameRequest(gitBlameUri);
			response = webConversation.getResource(request);
			assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

			// test
			JSONObject blameObject = new JSONObject(response.getText());
			assertEquals(blameObject.get("Severity"), "Error");
			assertEquals(blameObject.get("HttpCode"), 400);
			assertEquals(blameObject.get("Code"), 0);
		}
	}

	@Test
	public void testBlameOneCommit() throws IOException, SAXException, JSONException, CoreException {

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			//clone a repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			//get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			//create and modify file
			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "line one \n line two \n line 3 \n line 4");

			//commit
			addFile(testTxt);
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "initial commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get blame Uri
			JSONObject testTxtGitSection = testTxt.getJSONObject(GitConstants.KEY_GIT);
			String blameUri = testTxtGitSection.getString(GitConstants.KEY_BLAME);

			// blame request
			request = getGetGitBlameRequest(blameUri);
			response = webConversation.getResource(request);

			// get BlameInfo
			JSONObject blameObject = new JSONObject(response.getText());

			//Test
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");

			JSONArray blame = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertNotNull(blameObject.get(ProtocolConstants.KEY_CHILDREN));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_IMAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_MESSAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_TIME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT));
			assertNotNull(blameObject.get(ProtocolConstants.KEY_NAME));
			JSONArray children = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			JSONObject child = children.getJSONObject(0);
			assertEquals(children.length(), 1);
			assertEquals(child.get(GitConstants.KEY_START_RANGE), 1);
			assertEquals(child.get(GitConstants.KEY_END_RANGE), 4);

		}
	}

	@Test
	public void testBlameMultiCommit() throws IOException, SAXException, JSONException, CoreException {

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			//clone a repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			//get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			//create file test.txtx
			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "line one \n line two \n line 3 \n line 4");

			//commit the file
			addFile(testTxt);
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "initial commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get the blame uri for this file
			JSONObject testTxtGitSection = testTxt.getJSONObject(GitConstants.KEY_GIT);
			String blameUri = testTxtGitSection.getString(GitConstants.KEY_BLAME);

			// blame the file
			request = getGetGitBlameRequest(blameUri);
			response = webConversation.getResource(request);

			// testing
			JSONObject blameObject = new JSONObject(response.getText());

			// non blame info tests
			JSONArray blame = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertNotNull(blameObject.get(ProtocolConstants.KEY_CHILDREN));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_IMAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_MESSAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_TIME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT));
			assertNotNull(blameObject.get(ProtocolConstants.KEY_NAME));
			JSONArray children = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			JSONObject child = children.getJSONObject(0);
			assertEquals(children.length(), 1);
			assertEquals(child.get(GitConstants.KEY_START_RANGE), 1);
			assertEquals(child.get(GitConstants.KEY_END_RANGE), 4);

			//save commit info to test
			String commitLocation1 = blameObject.getString(GitConstants.KEY_COMMIT);
			int commitTime1 = blameObject.getInt(GitConstants.KEY_COMMIT_TIME);
			String commitId1 = blameObject.getString(ProtocolConstants.KEY_NAME);

			// modify the file
			modifyFile(testTxt, "LINE ONE \n LINE TWO \n LINE THREE \n LINE FOUR \n LINE FIVE");
			addFile(testTxt);

			//commit the new changes
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "initial commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get blame uri
			testTxtGitSection = testTxt.getJSONObject(GitConstants.KEY_GIT);
			blameUri = testTxtGitSection.getString(GitConstants.KEY_BLAME);

			// blame file
			request = getGetGitBlameRequest(blameUri);
			response = webConversation.getResource(request);

			// test
			blameObject = new JSONObject(response.getText());

			// non blame info tests
			assertEquals(blameObject.length(), 4);
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");
			assertEquals(blameObject.getString(ProtocolConstants.KEY_LOCATION), blameUri);

			blame = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertNotNull(blameObject.get(ProtocolConstants.KEY_CHILDREN));

			// test first commit
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);

			//test second commit
			blameObject = blame.getJSONObject(0);

			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_IMAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_MESSAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_TIME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT));
			assertNotNull(blameObject.get(ProtocolConstants.KEY_NAME));
			children = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			child = children.getJSONObject(0);
			assertEquals(children.length(), 1);
			assertEquals(child.get(GitConstants.KEY_START_RANGE), 1);
			assertEquals(child.get(GitConstants.KEY_END_RANGE), 5);

			String commitLocation2 = blameObject.getString(GitConstants.KEY_COMMIT);
			int commitTime2 = blameObject.getInt(GitConstants.KEY_COMMIT_TIME);
			String commitId2 = blameObject.getString(ProtocolConstants.KEY_NAME);

			// test that there are not duplicates of the same commit
			assertNotSame(commitId1, commitId2);
			assertNotSame(commitLocation1, commitLocation2);
			assertNotSame(commitTime1, commitTime2);

		}
	}

	@Test
	public void testBlameMultiFile() throws IOException, SAXException, JSONException, CoreException {

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);
		for (IPath clonePath : clonePaths) {
			//clone a repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			//get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			/*
			 * Commit 1
			 */
			// create the original file and modify it
			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "line one \n line two \n line 3 \n line 4");

			//commit file
			addFile(testTxt);
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "initial commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get blameURI
			JSONObject testTxtGitSection = testTxt.getJSONObject(GitConstants.KEY_GIT);
			String blameUri = testTxtGitSection.getString(GitConstants.KEY_BLAME);

			// make blame request
			request = getGetGitBlameRequest(blameUri);
			response = webConversation.getResource(request);

			// test
			JSONObject blameObject = new JSONObject(response.getText());
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");
			assertEquals(blameObject.getString(ProtocolConstants.KEY_LOCATION), blameUri);

			//test blameInfo
			JSONArray blame = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertNotNull(blameObject.get(ProtocolConstants.KEY_CHILDREN));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_IMAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_MESSAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_TIME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT));
			assertNotNull(blameObject.get(ProtocolConstants.KEY_NAME));
			JSONArray children = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			JSONObject child = children.getJSONObject(0);
			assertEquals(children.length(), 1);
			assertEquals(child.get(GitConstants.KEY_START_RANGE), 1);
			assertEquals(child.get(GitConstants.KEY_END_RANGE), 4);

			/*
			 * commit 2 - different file
			 */

			// create a second file in a different folder
			JSONObject newfolder = getChild(folder, "folder");
			JSONObject folderTxt = getChild(newfolder, "folder.txt");
			modifyFile(folderTxt, "commit me");

			// commit the new file
			addFile(folderTxt);
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "initial commit on testFile2.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// make blame request
			request = getGetGitBlameRequest(blameUri);
			response = webConversation.getResource(request);

			/*
			 * These tests should produce the same results as the above tests
			 * They should be not affected by the recent commit
			 */
			blameObject = new JSONObject(response.getText());
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");
			assertEquals(blameObject.getString(ProtocolConstants.KEY_LOCATION), blameUri);

			//test blameInfo
			blame = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertNotNull(blameObject.get(ProtocolConstants.KEY_CHILDREN));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_IMAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_MESSAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_TIME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT));
			assertNotNull(blameObject.get(ProtocolConstants.KEY_NAME));
			children = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			child = children.getJSONObject(0);
			assertEquals(children.length(), 1);
			assertEquals(child.get(GitConstants.KEY_START_RANGE), 1);
			assertEquals(child.get(GitConstants.KEY_END_RANGE), 4);

			/*
			 * commit 3 - original file
			 */

			// modify original file
			modifyFile(testTxt, "LINE ONE \n LINE TWO \n LINE THREE \n LINE FOUR \n LINE FIVE");

			//commit the changes
			addFile(testTxt);
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "modified testFile.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			blameUri = testTxtGitSection.getString(GitConstants.KEY_BLAME);

			// do blame computation
			request = getGetGitBlameRequest(blameUri);
			response = webConversation.getResource(request);

			blameObject = new JSONObject(response.getText());
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");
			assertEquals(blameObject.getString(ProtocolConstants.KEY_LOCATION), blameUri);

			// test
			blame = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertNotNull(blameObject.get(ProtocolConstants.KEY_CHILDREN));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_AUTHOR_IMAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_EMAIL));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMITTER_NAME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_MESSAGE));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT_TIME));
			assertNotNull(blameObject.get(GitConstants.KEY_COMMIT));
			assertNotNull(blameObject.get(ProtocolConstants.KEY_NAME));
			children = blameObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			child = children.getJSONObject(0);
			assertEquals(children.length(), 1);
			assertEquals(child.get(GitConstants.KEY_START_RANGE), 1);
			assertEquals(child.get(GitConstants.KEY_END_RANGE), 5);

		}
	}

	@Test
	public void testFolderBlame() throws IOException, SAXException, JSONException, CoreException {

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			//clone a repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			//get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			JSONObject newfolder = getChild(folder, "folder");

			//modifyFile(newfolder, "commit me");

			// commit the new file
			addFile(newfolder);
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "initial commit on testFile2.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			JSONObject newfolderGitSection = newfolder.getJSONObject(GitConstants.KEY_GIT);
			String blameUri = newfolderGitSection.getString(GitConstants.KEY_BLAME);

			// blame request
			request = getGetGitBlameRequest(blameUri);
			response = webConversation.getResource(request);

			// get BlameInfo
			JSONObject blameObject = new JSONObject(response.getText());

			//Test
			assertEquals(blameObject.get("Severity"), "Error");
			assertEquals(blameObject.get("HttpCode"), 400);
			assertEquals(blameObject.get("Code"), 0);

		}
	}

	protected static WebRequest getGetGitBlameRequest(String location) {
		String requestURI = toAbsoluteURI(location);
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}