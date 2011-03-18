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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitRemoteTest extends GitTest {
	@Test
	public void testGetNoRemote() throws IOException, SAXException, JSONException, URISyntaxException {
		URI contentLocation = gitDir.toURI();

		URI workspaceLocation = createWorkspace(getMethodName());

		ServletTestingSupport.allowedPrefixes = contentLocation.toString();
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject newProject = new JSONObject(response.getText());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(projectContentLocation);

		// http://<host>/file/<projectId>/
		request = getGetFilesRequest(projectContentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		request = getGetGitRemoteRequest(gitRemoteUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(0, remotesArray.length());
	}

	@Test
	public void testGetOrigin() throws IOException, SAXException, JSONException, URISyntaxException {
		URIish uri = new URIish(gitDir.toURL());
		String name = null;
		WebRequest request = GitCloneTest.getPostGitCloneRequest(uri, name);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(location);
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);

		URI workspaceLocation = createWorkspace(getMethodName());

		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		request = getGetGitRemoteRequest(gitRemoteUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteLocation);

		request = getGetGitRemoteRequest(remoteLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		// TODO: check response body
	}

	@Test
	public void testGetUnknownRemote() throws IOException, SAXException, JSONException, URISyntaxException {
		// clone repo
		// get list = 1
		// get xxx, NOT_FOUND
		URIish uri = new URIish(gitDir.toURL());
		String name = null;
		WebRequest request = GitCloneTest.getPostGitCloneRequest(uri, name);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(location);
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);

		URI workspaceLocation = createWorkspace(getMethodName());

		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		request = getGetGitRemoteRequest(gitRemoteUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteLocation);

		URI u = URI.create(remoteLocation);
		IPath p = new Path(u.getPath());
		p = p.uptoSegment(2).append("xxx").append(p.removeFirstSegments(3));
		URI nu = new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), p.toString(), u.getQuery(), u.getFragment());

		request = getGetGitRemoteRequest(nu.toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	private static WebRequest getGetGitRemoteRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.REMOTE_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
