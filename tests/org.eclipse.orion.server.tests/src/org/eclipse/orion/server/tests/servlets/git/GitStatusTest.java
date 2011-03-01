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
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitStatusTest extends GitTest {
	@Test
	public void testStatusClean() throws IOException, SAXException, URISyntaxException, JSONException {
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

		String gitStatusUri = gitSection.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);

		WebRequest request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	public void testStatusAdded() throws IOException, SAXException, URISyntaxException, JSONException, NoFilepatternException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		String fileName = "new.txt";
		WebRequest request = getPostFilesRequest(projectId + "/", getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		// TODO: replace with REST API for adding to index when bug 338200 is fixed
		Repository db = new FileRepository(new File(gitDir, Constants.DOT_GIT));
		Git git = new Git(db);
		AddCommand add = git.add();
		add.addFilepattern(fileName);
		add.call();

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitStatusUri = gitSection.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(1, statusArray.length());
		assertEquals(fileName, statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	@Ignore("not yet implemented")
	public void testStatusAssumeUnchanged() {
		// TODO: implement
	}

	@Test
	@Ignore("not yet implemented")
	public void testStatusChanged() {
		// TODO: implement
	}

	@Test
	public void testStatusMissing() throws IOException, SAXException, URISyntaxException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		WebRequest request = getDeleteFilesRequest(projectId + "/test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitStatusUri = gitSection.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());

		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	public void testStatusModified() throws IOException, SAXException, URISyntaxException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitStatusUri = gitSection.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	@Ignore("not yet implemented")
	public void testStatusRemoved() {
		// TODO: implement
	}

	@Test
	public void testStatusUntracked() throws IOException, SAXException, URISyntaxException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		String fileName = "new.txt";
		WebRequest request = getPostFilesRequest(projectId + "/", getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitStatusUri = gitSection.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(1, statusArray.length());
		assertEquals(fileName, statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
	}

	/**
	 * Creates a request to get the status result for the given location.
	 * @param location Either an absolute URI, or a workspace-relative URI
	 */
	private WebRequest getGetGitStatusRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.STATUS_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
