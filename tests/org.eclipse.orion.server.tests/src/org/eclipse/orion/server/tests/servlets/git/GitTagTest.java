/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
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
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitTagTest extends GitTest {
	@Test
	public void testTag() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetFilesRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);

			// tag HEAD with 'tag'
			JSONObject tag = tag(gitTagUri, "tag", Constants.HEAD);
			assertEquals("tag", tag.getString(ProtocolConstants.KEY_NAME));
			new URI(tag.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		}
	}

	@Test
	public void testListTags() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetFilesRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);

			JSONArray tags = listTags(gitTagUri);
			assertEquals(0, tags.length());

			// log
			request = GitCommitTest.getGetGitCommitRequest(gitHeadUri, false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject logResponse = new JSONObject(response.getText());
			JSONArray commitsArray = logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
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
			assertEquals("tag2", tags.getJSONObject(1).get(ProtocolConstants.KEY_NAME));
		}
	}

	@Test
	public void testTagFailed() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetFilesRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());
			String folderLocation = folder.getString(ProtocolConstants.KEY_LOCATION);

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);

			// tag HEAD with 'tag'
			JSONObject tag = tag(gitTagUri, "tag", Constants.HEAD);
			assertEquals("tag", tag.getString(ProtocolConstants.KEY_NAME));
			new URI(tag.getString(ProtocolConstants.KEY_CONTENT_LOCATION));

			// tag HEAD with 'tag' again (TagHandler) - should fail
			request = getPostGitTagRequest(gitTagUri, "tag", Constants.HEAD);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());

			// tag HEAD with 'tag' again (CommitHandler) - should fail
			request = getPutGitCommitRequest(gitHeadUri, "tag");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());

			// modify
			request = getPutFileRequest(folderLocation + "/test.txt", "test.txt change");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// add
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "folder/folder.txt");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

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
			assertTrue(result.getString("DetailedMessage").endsWith("REJECTED"));

			// tag HEAD with 'tag' again (CommitHandler) - should fail
			request = getPutGitCommitRequest(gitHeadUri, "tag");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
			result = new JSONObject(response.getText());
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, result.getInt("HttpCode"));
			assertEquals("Error", result.getString("Severity"));
			assertEquals("An error occured when tagging.", result.getString("Message"));
			assertTrue(result.getString("DetailedMessage").endsWith("REJECTED"));
		}
	}

}
