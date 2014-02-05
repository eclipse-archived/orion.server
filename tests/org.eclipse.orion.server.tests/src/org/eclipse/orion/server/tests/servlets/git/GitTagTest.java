/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.eclipse.orion.server.tests.IsJSONObjectEqual.isJSONObjectEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitTagTest extends GitTest {
	@Test
	public void testTag() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String tagLocation = clone.getString(GitConstants.KEY_TAG);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);
			assertEquals(tagLocation, gitTagUri);

			String[] tagNames = {"tag", "t/a/g"};
			for (String tagName : tagNames) {
				// tag HEAD with the tagName
				JSONObject tag = tag(gitTagUri, tagName, Constants.HEAD);
				assertEquals(tagName, tag.getString(ProtocolConstants.KEY_NAME));
				URI tagUri = new URI(tag.getString(ProtocolConstants.KEY_LOCATION));

				// check tag metadata
				request = getGetGitTagRequest(tagUri.toString());
				response = webConversation.getResponse(request);
				assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
				JSONObject tag1 = new JSONObject(response.getText());
				assertThat(tag1, isJSONObjectEqual(tag));
			}
		}
	}

	@Test
	public void testListDeleteTags() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);

			JSONArray tags = listTags(gitTagUri);
			assertEquals(0, tags.length());

			// log
			JSONArray commitsArray = log(gitHeadUri);
			assertEquals(1, commitsArray.length());

			JSONObject commit = commitsArray.getJSONObject(0);
			String commitId = commit.getString(ProtocolConstants.KEY_NAME);
			String commitLocation = commit.getString(ProtocolConstants.KEY_LOCATION);

			tag(gitTagUri, "tag1", commitId);

			tags = listTags(gitTagUri);
			assertEquals(1, tags.length());
			assertEquals("tag1", tags.getJSONObject(0).get(ProtocolConstants.KEY_NAME));

			// update commit with tag
			tag(commitLocation, "tag2");

			tags = listTags(gitTagUri);
			assertEquals(2, tags.length());
			assertEquals("tag2", tags.getJSONObject(0).get(ProtocolConstants.KEY_NAME));

			// delete 'tag1'
			JSONObject tag1 = tags.getJSONObject(1);
			assertEquals("tag1", tag1.get(ProtocolConstants.KEY_NAME));
			String tag1Uri = tag1.getString(ProtocolConstants.KEY_LOCATION);
			deleteTag(tag1Uri);

			tags = listTags(gitTagUri);
			assertEquals(1, tags.length());
			assertEquals("tag2", tags.getJSONObject(0).get(ProtocolConstants.KEY_NAME));

			// check if the deleted tag is gone
			request = getGetGitTagRequest(tag1Uri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
		}
	}

	@Test
	public void testTagFailed() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			// tag HEAD with 'tag'
			JSONObject tag = tag(gitTagUri, "tag", Constants.HEAD);
			assertEquals("tag", tag.getString(ProtocolConstants.KEY_NAME));
			new URI(tag.getString(ProtocolConstants.KEY_LOCATION));

			// tag HEAD with 'tag' again (TagHandler) - should fail
			request = getPostGitTagRequest(gitTagUri, "tag", Constants.HEAD);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());

			// tag HEAD with 'tag' again (CommitHandler) - should fail
			request = getPutGitCommitRequest(gitHeadUri, "tag");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());

			// modify
			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "test.txt change");

			// add
			JSONObject folder1 = getChild(folder, "folder");
			JSONObject folderTxt = getChild(folder1, "folder.txt");
			addFile(folderTxt);

			// commit
			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// tag next commit with 'tag' again (TagHandler) - should fail
			request = getPostGitTagRequest(gitTagUri, "tag", Constants.HEAD);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
			JSONObject result = new JSONObject(response.getText());
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, result.getInt("HttpCode"));
			assertEquals("Error", result.getString("Severity"));
			assertEquals("An error occured when tagging.", result.getString("Message"));
			assertTrue(result.toString(), result.getString("DetailedMessage").endsWith("already exists"));

			// tag HEAD with 'tag' again (CommitHandler) - should fail
			request = getPutGitCommitRequest(gitHeadUri, "tag");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
			result = new JSONObject(response.getText());
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, result.getInt("HttpCode"));
			assertEquals("Error", result.getString("Severity"));
			assertEquals("An error occured when tagging.", result.getString("Message"));
			assertTrue(result.toString(), result.getString("DetailedMessage").endsWith("already exists"));
		}
	}

	@Test
	public void testTagFromLogAll() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String cloneCommitUri = clone.getString(GitConstants.KEY_COMMIT);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String folderTagUri = gitSection.getString(GitConstants.KEY_TAG);

			// get the full log
			JSONArray commits = log(cloneCommitUri);
			assertEquals(1, commits.length());
			String commitLocation = commits.getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION);

			// tag
			tag(commitLocation, "tag1");

			// check
			JSONArray tags = listTags(folderTagUri);
			assertEquals(1, tags.length());
			assertEquals("tag1", tags.getJSONObject(0).get(ProtocolConstants.KEY_NAME));
		}
	}

	@Test
	public void testCheckoutTag() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "tag me");
			addFile(testTxt);
			commitFile(testTxt, "tag me", false);

			// tag HEAD with 'tag'
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			tag(gitTagUri, "tag", Constants.HEAD);

			modifyFile(testTxt, "after tag");
			addFile(testTxt);
			commitFile(testTxt, "after tag", false);

			assertEquals("after tag", getFileContent(testTxt));

			response = checkoutTag(cloneLocation, "tag");
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			assertEquals("tag me", getFileContent(testTxt));
			// check current branch
			request = getGetRequest(clone.getString(GitConstants.KEY_BRANCH));
			response = webConversation.getResponse(request);
			ServerStatus status = waitForTask(response);
			assertTrue(status.toString(), status.isOK());
			JSONObject branches = status.getJsonData();
			assertEquals("tag_tag", GitBranchTest.getCurrentBranch(branches).getString(ProtocolConstants.KEY_NAME));
			// log
			JSONArray commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());
			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals("tag me", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			commit = commitsArray.getJSONObject(1);
			assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

			response = checkoutBranch(cloneLocation, Constants.MASTER);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			assertEquals("after tag", getFileContent(testTxt));
		}
	}

	@Test
	public void testListOrionServerTags() throws Exception {
		File orionServer = new File("").getAbsoluteFile().getParentFile(/*org.eclipse.orion.server.tests*/).getParentFile(/*tests*/);
		Assume.assumeTrue(new File(orionServer, Constants.DOT_GIT).exists());

		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), orionServer.toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// get project/folder metadata
		WebRequest request = getGetRequest(location);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject folder = new JSONObject(response.getText());

		JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
		String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);

		JSONArray tags = listTags(gitTagUri);
		assertTrue(tags.length() > 0);
		long lastTime = Long.MAX_VALUE;
		for (int i = 0; i < 5; i++) {
			JSONObject tag = tags.getJSONObject(i);

			// assert properly sorted, new first
			long t = tag.getLong(ProtocolConstants.KEY_LOCAL_TIMESTAMP);
			assertTrue(t <= lastTime);
			lastTime = t;

			// get 'tag' metadata
			request = getGetGitTagRequest(tag.getString(ProtocolConstants.KEY_LOCATION).toString());
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		}
		for (int i = tags.length() - 5; i < tags.length(); i++) {
			JSONObject tag = tags.getJSONObject(i);

			// assert properly sorted, new first
			long t = tag.getLong(ProtocolConstants.KEY_LOCAL_TIMESTAMP);
			assertTrue(t <= lastTime);
			lastTime = t;

			// get 'tag' metadata
			request = getGetGitTagRequest(tag.getString(ProtocolConstants.KEY_LOCATION).toString());
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		}
	}
}