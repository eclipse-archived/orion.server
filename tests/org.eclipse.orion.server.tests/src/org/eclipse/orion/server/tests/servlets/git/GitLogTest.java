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
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

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

public class GitLogTest extends GitTest {

	@Test
	public void testGetLog() throws IOException, SAXException, URISyntaxException, JSONException {
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
		String gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);
		String gitCommitUri = gitSection.optString(GitConstants.KEY_COMMIT, null);
		assertNotNull(gitCommitUri);

		// modify
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "first change");
		response = webConversation.getResponse(request);
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
		request = GitCommitTest.getGetGitCommitRequest(gitCommitUri, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONArray statusResponse = new JSONArray(response.getText());

		assertEquals(3, statusResponse.length());

		JSONObject commit = statusResponse.getJSONObject(0);
		assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = statusResponse.getJSONObject(1);
		assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = statusResponse.getJSONObject(2);
		assertEquals("Initial commit", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
		String initialGitCommitName = commit.getString(ProtocolConstants.KEY_NAME);
		String initialGitCommitURI = gitCommitUri.replaceAll("HEAD", initialGitCommitName);

		// prepare a scoped log location
		request = getPostForScopedLogRequest(initialGitCommitURI, "HEAD");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String newGitCommitUri = response.getHeaderField(ProtocolConstants.KEY_LOCATION);

		// get a scoped log
		request = GitCommitTest.getGetGitCommitRequest(newGitCommitUri, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONArray(response.getText());

		assertEquals(2, statusResponse.length());

		commit = statusResponse.getJSONObject(0);
		assertEquals("commit2", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

		commit = statusResponse.getJSONObject(1);
		assertEquals("commit1", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
	}

	static WebRequest getPostForScopedLogRequest(String location, String newCommit) throws JSONException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_COMMIT_NEW, newCommit);
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
