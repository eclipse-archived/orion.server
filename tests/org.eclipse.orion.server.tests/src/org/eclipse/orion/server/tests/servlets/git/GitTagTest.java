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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

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

public class GitTagTest extends GitTest {
	@Test
	@Ignore("not implemented yet")
	public void testTag() {
		// TODO: 
		// tag
		// => tag
	}

	@Test
	public void testListTags() throws IOException, SAXException, JSONException, URISyntaxException {

		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
		String gitTagUri = gitSection.getString(GitConstants.KEY_TAG);

		WebRequest request = getGetGitTagRequest(gitTagUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject tags = new JSONObject(response.getText());
		JSONArray tagsArray = tags.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(0, tagsArray.length());

		request = GitCommitTest.getGetGitCommitRequest(gitCommitUri, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject logResponse = new JSONObject(response.getText());
		JSONArray commitsArray = logResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, commitsArray.length());

		JSONObject commit = commitsArray.getJSONObject(0);
		String commitId = commit.getString(ProtocolConstants.KEY_NAME);

		tag(gitTagUri, "tag1", commitId);

		request = getGetGitTagRequest(gitTagUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		tags = new JSONObject(response.getText());
		tagsArray = tags.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, tagsArray.length());

		tag(gitTagUri, "tag2", commitId);

		request = getGetGitTagRequest(gitTagUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		tags = new JSONObject(response.getText());
		tagsArray = tags.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(2, tagsArray.length());
	}

	static WebRequest getGetGitTagRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.TAG_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
