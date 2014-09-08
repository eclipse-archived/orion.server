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

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitInitTest extends GitTest {

	@Test
	public void testInit() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath initPath = getClonePath(workspaceId, project);
		String contentLocation = init(null, initPath, null).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		Repository repository = getRepositoryForContentLocation(toAbsoluteURI(contentLocation));
		assertNotNull(repository);
	}

	@Test
	public void testInitAndCreateProjectByName() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath initPath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		JSONObject repo = init(initPath, null, getMethodName());

		String contentLocation = repo.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetRequest(contentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(getMethodName(), project.getString(ProtocolConstants.KEY_NAME));
		assertGitSectionExists(project);
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testInitAndCreateFolderByPath() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath initPath = getClonePath(workspaceId, project).append("repos").append("repo1").makeAbsolute();

		// /file/{id}/repos/repo1, folders: 'repo' and 'repo1' don't exist
		JSONObject repo = init(null, initPath, null);

		String contentLocation = repo.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetRequest(contentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject folder = new JSONObject(response.getText());
		assertGitSectionExists(folder);
	}

	@Test
	public void testInitWithoutNameAndFilePath() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath initPath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		WebRequest request = getPostGitInitRequest(initPath, null, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	private WebRequest getPostGitInitRequest(IPath workspace, IPath path, String name) throws JSONException, UnsupportedEncodingException {
		return getPostGitCloneRequest(null, workspace, path, name, null, null);
	}

}
