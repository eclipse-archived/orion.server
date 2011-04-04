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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FileUtils;
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

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		assertNotNull(gitSection.optString(GitConstants.KEY_STATUS, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_DIFF, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_INDEX, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_COMMIT, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_REMOTE, null));
	}

	@Test
	public void testGitUrisInContentLocation() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		// http://<host>/workspace/<workspaceId>/
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject newProject = new JSONObject(response.getText());
		assertEquals(projectName, newProject.getString(ProtocolConstants.KEY_NAME));
		String projectId = newProject.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);
		String contentLocation = newProject.optString(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		assertNotNull(contentLocation);

		// http://<host>/file/<projectId>/
		WebRequest request = getGetFilesRequest(contentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		assertNotNull(gitSection.optString(GitConstants.KEY_STATUS, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_DIFF, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_INDEX, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_COMMIT, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_REMOTE, null));

		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetFilesRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
		String[] expectedChildren = new String[] {Constants.DOT_GIT, "folder", "test.txt"};
		assertEquals("Wrong number of directory children", expectedChildren.length, children.size());
		for (JSONObject child : children) {
			gitSection = child.optJSONObject(GitConstants.KEY_GIT);
			assertNotNull(gitSection);
			assertNotNull(gitSection.optString(GitConstants.KEY_STATUS, null));
			assertNotNull(gitSection.optString(GitConstants.KEY_DIFF, null));
			assertNotNull(gitSection.optString(GitConstants.KEY_INDEX, null));
			assertNotNull(gitSection.optString(GitConstants.KEY_COMMIT, null));
			assertNotNull(gitSection.optString(GitConstants.KEY_REMOTE, null));
		}
		childrenLocation = getChildByName(children, "folder").getString(ProtocolConstants.KEY_CHILDREN_LOCATION);

		// http://<host>/file/<projectId>/folder/?depth=1
		request = getGetFilesRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		children = getDirectoryChildren(new JSONObject(response.getText()));
		expectedChildren = new String[] {"folder.txt"};
		assertEquals("Wrong number of directory children", expectedChildren.length, children.size());
		for (JSONObject child : children) {
			gitSection = child.optJSONObject(GitConstants.KEY_GIT);
			assertNotNull(gitSection);
			assertNotNull(gitSection.optString(GitConstants.KEY_STATUS, null));
			assertNotNull(gitSection.optString(GitConstants.KEY_DIFF, null));
			assertNotNull(gitSection.optString(GitConstants.KEY_INDEX, null));
			assertNotNull(gitSection.optString(GitConstants.KEY_COMMIT, null));
			assertNotNull(gitSection.optString(GitConstants.KEY_REMOTE, null));
		}
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

		assertNull(files.optString(GitConstants.KEY_STATUS, null));
		assertNull(files.optString(GitConstants.KEY_DIFF, null));
		assertNull(files.optString(GitConstants.KEY_DIFF, null));
		assertNull(files.optString(GitConstants.KEY_COMMIT, null));
		assertNull(files.optString(GitConstants.KEY_REMOTE, null));

		assertTrue(emptyDir.delete());
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

		assertNull(files.optString(GitConstants.KEY_STATUS, null));
		assertNull(files.optString(GitConstants.KEY_DIFF, null));
		assertNull(files.optString(GitConstants.KEY_DIFF, null));
		assertNull(files.optString(GitConstants.KEY_COMMIT, null));
		assertNull(files.optString(GitConstants.KEY_REMOTE, null));

		FileUtils.delete(dir, FileUtils.RECURSIVE);
	}

}
