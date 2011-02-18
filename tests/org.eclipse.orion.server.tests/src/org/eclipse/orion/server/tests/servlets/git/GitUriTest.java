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
import static org.junit.Assert.assertNull;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitUriTest extends GitTest {
	@Test
	public void testGitUrisAfterLinkingToExistingClone() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		String key = project.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(key);
		key = project.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(key);
		key = project.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(key);
	}

	@Test
	public void testGitUrisInContentLocation() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);
		String location = project.optString(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		assertNotNull(location);

		WebRequest request = getGetFilesRequest(location);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject files = new JSONObject(response.getText());

		String key = files.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(key);
		key = project.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(key);
		key = project.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(key);
	}

	@Test
	public void testGitUrisForEmptyDir() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		File emptyDir = getRandomLocation().toFile();
		emptyDir.mkdir();
		ServletTestingSupport.allowedPrefixes = emptyDir.toString();

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, emptyDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);
		String location = project.optString(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		assertNotNull(location);

		WebRequest request = getGetFilesRequest(location);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject files = new JSONObject(response.getText());

		String key = files.optString(GitConstants.KEY_STATUS, null);
		assertNull(key);
		key = project.optString(GitConstants.KEY_DIFF, null);
		assertNull(key);
		key = project.optString(GitConstants.KEY_INDEX, null);
		assertNull(key);
	}

	@Test
	public void testGitUrisForFile() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		File dir = getRandomLocation().toFile();
		dir.mkdir();
		File file = new File(dir, "test.txt");
		file.createNewFile();

		ServletTestingSupport.allowedPrefixes = dir.toString();

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, dir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);
		String location = project.optString(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		assertNotNull(location);

		WebRequest request = getGetFilesRequest(location);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject files = new JSONObject(response.getText());

		String key = files.optString(GitConstants.KEY_STATUS, null);
		assertNull(key);
		key = project.optString(GitConstants.KEY_DIFF, null);
		assertNull(key);
		key = project.optString(GitConstants.KEY_INDEX, null);
		assertNull(key);

		// TODO get children
	}

}
