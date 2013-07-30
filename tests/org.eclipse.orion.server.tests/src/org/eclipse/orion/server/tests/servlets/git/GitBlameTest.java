/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others
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
import java.net.URI;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
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

public class GitBlameTest extends GitTest {

	@Test
	public void testBlameNoCommits() throws IOException, SAXException, JSONException, CoreException {
		URI workspaceLocation = createWorkspace(getMethodName());
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
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// test
			JSONObject blameObject = new JSONObject(response.getText());
			assertEquals(blameObject.length(), 1);
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");

		}
	}

	@Test
	public void testBlameOneCommit() throws IOException, SAXException, JSONException, CoreException {

		URI workspaceLocation = createWorkspace(getMethodName());
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

			JSONArray blame = blameObject.getJSONArray(GitConstants.KEY_BLAME_INFO);
			assertNotNull(blameObject.get(GitConstants.KEY_BLAME_INFO));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertEquals(blameObject.getInt(GitConstants.KEY_START_RANGE), 1);
			assertEquals(blameObject.getInt(GitConstants.KEY_END_RANGE), 4);
			assertCommitUri(blameObject.getString(GitConstants.KEY_COMMIT));

		}
	}

	@Test
	public void testBlameMultiCommit() throws IOException, SAXException, JSONException, CoreException {

		URI workspaceLocation = createWorkspace(getMethodName());
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
			assertEquals(blameObject.length(), 3);
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");
			assertEquals(blameObject.getString(GitConstants.KEY_BLAME_LOCATION), blameUri);

			// blame info tests
			JSONArray blame = blameObject.getJSONArray(GitConstants.KEY_BLAME_INFO);
			assertNotNull(blameObject.get(GitConstants.KEY_BLAME_INFO));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertEquals(blameObject.getInt(GitConstants.KEY_START_RANGE), 1);
			assertEquals(blameObject.getInt(GitConstants.KEY_END_RANGE), 4);
			//	assertCommitUri(blameObject.getString(GitConstants.KEY_COMMIT));

			// modify the file
			modifyFile(testTxt, "line one \n line two \n line 3 \n LINE FOUR \n LINE FIVE");
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
			assertEquals(blameObject.length(), 3);
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");
			assertEquals(blameObject.getString(GitConstants.KEY_BLAME_LOCATION), blameUri);

			// blame info tests
			blame = blameObject.getJSONArray(GitConstants.KEY_BLAME_INFO);
			assertNotNull(blameObject.get(GitConstants.KEY_BLAME_INFO));
			assertEquals(blame.length(), 2);

			// test object 1 from the first commit
			blameObject = blame.getJSONObject(0);
			assertEquals(blameObject.getInt(GitConstants.KEY_START_RANGE), 1);
			assertEquals(blameObject.getInt(GitConstants.KEY_END_RANGE), 3);
			assertCommitUri(blameObject.getString(GitConstants.KEY_COMMIT));

			//test object 2 from the second commit
			blameObject = blame.getJSONObject(1);
			assertEquals(blameObject.getInt(GitConstants.KEY_START_RANGE), 4);
			assertEquals(blameObject.getInt(GitConstants.KEY_END_RANGE), 5);
			assertCommitUri(blameObject.getString(GitConstants.KEY_COMMIT));

			// make sure commits are not the same
			assertNotSame(blame.getJSONObject(0).get(GitConstants.KEY_COMMIT), blame.getJSONObject(1).get(GitConstants.KEY_COMMIT));
			assertNotSame(blame.getJSONObject(0).get(GitConstants.KEY_END_RANGE), blame.getJSONObject(1).get(GitConstants.KEY_START_RANGE));

		}
	}

	@Test
	public void testBlameMultiFile() throws IOException, SAXException, JSONException, CoreException {

		URI workspaceLocation = createWorkspace(getMethodName());
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
			assertEquals(blameObject.getString(GitConstants.KEY_BLAME_LOCATION), blameUri);

			//test blameInfo
			JSONArray blame = blameObject.getJSONArray(GitConstants.KEY_BLAME_INFO);
			assertNotNull(blameObject.get(GitConstants.KEY_BLAME_INFO));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertEquals(blameObject.getInt(GitConstants.KEY_START_RANGE), 1);
			assertEquals(blameObject.getInt(GitConstants.KEY_END_RANGE), 4);
			assertCommitUri(blameObject.getString(GitConstants.KEY_COMMIT));

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
			 */
			blameObject = new JSONObject(response.getText());
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");
			assertEquals(blameObject.getString(GitConstants.KEY_BLAME_LOCATION), blameUri);

			//test blameInfo
			blame = blameObject.getJSONArray(GitConstants.KEY_BLAME_INFO);
			assertNotNull(blameObject.get(GitConstants.KEY_BLAME_INFO));
			assertEquals(blame.length(), 1);
			blameObject = blame.getJSONObject(0);
			assertEquals(blameObject.getInt(GitConstants.KEY_START_RANGE), 1);
			assertEquals(blameObject.getInt(GitConstants.KEY_END_RANGE), 4);
			assertCommitUri(blameObject.getString(GitConstants.KEY_COMMIT));

			/*
			 * commit 3 - original file
			 */

			// modify original file
			modifyFile(testTxt, "line one \n line two \n line 3 \n LINE FOUR \n LINE FIVE");

			//commit the changes
			addFile(testTxt);
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "modified testFile.txt", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			blameUri = testTxtGitSection.getString(GitConstants.KEY_BLAME);

			// do blame computation
			request = getGetGitBlameRequest(blameUri);
			response = webConversation.getResource(request);

			// test
			blameObject = new JSONObject(response.getText());

			// non blame info tests
			//assertEquals(blameObject.length(), 3);
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");
			assertEquals(blameObject.getString(GitConstants.KEY_BLAME_LOCATION), blameUri);

			// blame info tests
			blame = blameObject.getJSONArray(GitConstants.KEY_BLAME_INFO);
			assertNotNull(blameObject.get(GitConstants.KEY_BLAME_INFO));
			assertEquals(blame.length(), 2);

			// test object 1 from the first commit
			blameObject = blame.getJSONObject(0);
			assertEquals(blameObject.getInt(GitConstants.KEY_START_RANGE), 1);
			assertEquals(blameObject.getInt(GitConstants.KEY_END_RANGE), 3);
			assertCommitUri(blameObject.getString(GitConstants.KEY_COMMIT));

			//test object 2 from the second commit
			blameObject = blame.getJSONObject(1);
			assertEquals(blameObject.getInt(GitConstants.KEY_START_RANGE), 4);
			assertEquals(blameObject.getInt(GitConstants.KEY_END_RANGE), 5);
			assertCommitUri(blameObject.getString(GitConstants.KEY_COMMIT));

			// make sure commits are not the same
			assertNotSame(blame.getJSONObject(0).get(GitConstants.KEY_COMMIT), blame.getJSONObject(1).get(GitConstants.KEY_COMMIT));
			assertNotSame(blame.getJSONObject(0).get(GitConstants.KEY_END_RANGE), blame.getJSONObject(1).get(GitConstants.KEY_START_RANGE));

		}
	}

	@Test
	public void testFolderBlame() throws IOException, SAXException, JSONException, CoreException {

		URI workspaceLocation = createWorkspace(getMethodName());
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

			assertEquals(blameObject.length(), 1);
			assertEquals(blameObject.getString(ProtocolConstants.KEY_TYPE), "Blame");

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