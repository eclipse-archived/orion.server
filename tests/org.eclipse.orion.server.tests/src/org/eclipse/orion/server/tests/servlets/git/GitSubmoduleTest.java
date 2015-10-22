/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.eclipse.jgit.lib.Constants.DOT_GIT_MODULES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitSubmoduleTest extends GitTest {

	@Test
	public void testGetSubmodulesLocation() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		String contentLocation = clone(workspaceId, project).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		
		JSONArray clonesArray = listClones(workspaceId, null);
		JSONObject clone = clonesArray.getJSONObject(0);
		assertNotNull(clone.get(GitConstants.KEY_SUBMODULE));
	}
	
	@Test
	public void testAddSubmodule() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		JSONObject clone = clone(workspaceId, project);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String submoduleLocation = clone.getString(GitConstants.KEY_SUBMODULE);
		String location = clone.getString(ProtocolConstants.KEY_LOCATION);
		
		Repository repository = getRepositoryForContentLocation(contentLocation);
		File file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertFalse(file.exists());
		
		URIish uri = new URIish(gitDir.toURI().toURL());
		WebRequest request = postSubmoduleRequest(submoduleLocation, "test", uri.toString(), location);
		WebResponse response = webConversation.getResponse(request);
		
		file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertTrue(file.exists());
		
		assertNotNull(repository);
	}
	
	@Test
	public void testSyncSubmodule() throws IOException, SAXException, JSONException, CoreException, ConfigInvalidException {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		JSONObject clone = clone(workspaceId, project);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String submoduleLocation = clone.getString(GitConstants.KEY_SUBMODULE);
		String location = clone.getString(ProtocolConstants.KEY_LOCATION);

		Repository repository = getRepositoryForContentLocation(contentLocation);
		File file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertFalse(file.exists());
		
		URIish uri = new URIish(gitDir.toURI().toURL());
		WebRequest request = postSubmoduleRequest(submoduleLocation, "test", uri.toString(), location);
		WebResponse response = webConversation.getResponse(request);
		
		file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertTrue(file.exists());
		
		assertNotNull(repository);
		
		StoredConfig repoConfig = repository.getConfig();
		String originalUrl = repoConfig.getString("submodule", "test", "url");
		repoConfig.setString("submodule", "test", "url", "value");
		repoConfig.save();
		assertEquals(repoConfig.getString("submodule", "test", "url"), "value");
		
		WebRequest reqSync = putSubmoduleRequest(submoduleLocation, "sync");
		WebResponse resSync = webConversation.getResponse(reqSync);

		repoConfig = repository.getConfig();
		assertEquals(repoConfig.getString("submodule", "test", "url"), originalUrl);

	}
	
	@Test
	public void testUpdateSubmodule() throws IOException, SAXException, JSONException, CoreException {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		JSONObject clone = clone(workspaceId, project);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String submoduleLocation = clone.getString(GitConstants.KEY_SUBMODULE);
		String location = clone.getString(ProtocolConstants.KEY_LOCATION);
		
		Repository repository = getRepositoryForContentLocation(contentLocation);
		File file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertFalse(file.exists());
		
		URIish uri = new URIish(gitDir.toURI().toURL());
		WebRequest request = postSubmoduleRequest(submoduleLocation, "test", uri.toString(), location);
		WebResponse response = webConversation.getResponse(request);
		
		file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertTrue(file.exists());
		
		assertNotNull(repository);
		
		file = new File(repository.getWorkTree(), "test");
		assertTrue(file.exists() && file.isDirectory());
		
		FileUtils.delete(file, FileUtils.RECURSIVE);
		
		WebRequest reqUpdate = putSubmoduleRequest(submoduleLocation, "update");
		WebResponse resUpdate = webConversation.getResponse(reqUpdate);
		
		file = new File(repository.getWorkTree(), "test");
		assertFalse(file.exists() && file.isDirectory());
	}
	
	@Test
	public void testRemoveSubmodule() throws IOException, SAXException, JSONException, CoreException {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		JSONObject clone = clone(workspaceId, project);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String submoduleLocation = clone.getString(GitConstants.KEY_SUBMODULE);
		String location = clone.getString(ProtocolConstants.KEY_LOCATION);
		
		Repository repository = getRepositoryForContentLocation(contentLocation);
		File file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertFalse(file.exists());
		
		URIish uri = new URIish(gitDir.toURI().toURL());
		WebRequest request = postSubmoduleRequest(submoduleLocation, "test", uri.toString(), location);
		WebResponse response = webConversation.getResponse(request);
		request = postSubmoduleRequest(submoduleLocation, "test2", uri.toString(), location);
		response = webConversation.getResponse(request);
		
		assertNotNull(repository);
		
		file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertTrue(file.exists());
		
		file = new File(repository.getWorkTree(), "test");
		assertTrue(file.exists() && file.isDirectory());
		file = new File(repository.getWorkTree(), "test2");
		assertTrue(file.exists() && file.isDirectory());
		
		WebRequest reqDelete = deleteSubmoduleRequest(submoduleLocation+"test/");
		WebResponse resDelete = webConversation.getResponse(reqDelete);
		
		file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertTrue(file.exists());
		file = new File(repository.getWorkTree(), "test");
		assertFalse(file.exists() && file.isDirectory());

		reqDelete = deleteSubmoduleRequest(submoduleLocation+"test2/");
		resDelete = webConversation.getResponse(reqDelete);
		
		file = new File(repository.getWorkTree(), DOT_GIT_MODULES);
		assertFalse(file.exists());
		file = new File(repository.getWorkTree(), "test2");
		assertFalse(file.exists() && file.isDirectory());
	}
	
	private WebRequest postSubmoduleRequest(String submoduleLocation, String name, String gitUrl, String location) throws IOException, JSONException {
		String requestURI = toAbsoluteURI(submoduleLocation);
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_NAME, name);
		body.put(GitConstants.KEY_URL, gitUrl);
		body.put(ProtocolConstants.KEY_LOCATION, location);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private WebRequest putSubmoduleRequest(String submoduleLocation, String operation) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(submoduleLocation);
		JSONObject body = new JSONObject();
		body.put("Operation", operation);
		WebRequest request = new PutMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
	
	private WebRequest deleteSubmoduleRequest(String submoduleLocation) {
		String requestURI = toAbsoluteURI(submoduleLocation);
		WebRequest request = new DeleteMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
	
}
