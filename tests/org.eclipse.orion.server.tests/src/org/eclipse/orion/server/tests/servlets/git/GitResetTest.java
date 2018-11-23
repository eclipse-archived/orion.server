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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitResetTest extends GitTest {

	// modified + add = changed, changed + reset = modified
	@Test
	public void testResetChanged() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject folder = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder, "folder.txt");
		modifyFile(folderTxt, "hello");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		addFile(testTxt);
		addFile(folderTxt);

		assertStatus(new StatusResult().setChanged(2), gitStatusUri);

		WebRequest request = getPostGitIndexRequest(testTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_INDEX), null, null, (String) null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setChanged(1).setModified(1), gitStatusUri);
	}

	@Test
	public void testResetChangedWithPath() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject folder = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder, "folder.txt");
		modifyFile(folderTxt, "hello");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		addFile(testTxt);
		addFile(folderTxt);

		assertStatus(new StatusResult().setChanged(2), gitStatusUri);

		WebRequest request = getPostGitIndexRequest(gitIndexUri, new String[] {"test.txt"}, null, (String) null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setChanged(1).setModified(1), gitStatusUri);
	}

	@Test
	public void testResetNull() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);

		WebRequest request = getPostGitIndexRequest(gitIndexUri, null, null, (String) null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testResetNotImplemented() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);

		WebRequest request = getPostGitIndexRequest(gitIndexUri, ResetType.KEEP);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_IMPLEMENTED, response.getResponseCode());

		request = getPostGitIndexRequest(gitIndexUri, ResetType.MERGE);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_IMPLEMENTED, response.getResponseCode());
	}

	@Test
	public void testResetBadType() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);

		WebRequest request = getPostGitIndexRequest(gitIndexUri, null, null, "BAD");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testResetMixedAll() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
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

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "hello");

			String fileName = "new.txt";
			request = getPostFilesRequest(folder.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			JSONObject folder1 = getChild(folder, "folder");
			JSONObject folderTxt = getChild(folder1, "folder.txt");
			deleteFile(folderTxt);

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			// "git status"
			assertStatus(new StatusResult().setMissing(1).setModified(1).setUntracked(1), gitStatusUri);

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri /* add all */);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git status"
			assertStatus(new StatusResult().setAdded(1).setChanged(1).setRemoved(1), gitStatusUri);

			// "git reset --mixed HEAD"
			request = getPostGitIndexRequest(gitIndexUri /* reset all */, ResetType.MIXED);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git status", should be the same result as called for the first time
			assertStatus(new StatusResult().setMissing(1).setModified(1).setUntracked(1), gitStatusUri);
		}
	}

	@Test
	public void testResetHardAll() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
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

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "hello");

			String fileName = "new.txt";
			request = getPostFilesRequest(folder.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			JSONObject folder1 = getChild(folder, "folder");
			JSONObject folderTxt = getChild(folder1, "folder.txt");
			deleteFile(folderTxt);

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			// "git status"
			assertStatus(new StatusResult().setMissing(1).setModified(1).setUntracked(1), gitStatusUri);

			// "git add ."
			request = GitAddTest.getPutGitIndexRequest(gitIndexUri /* add all */);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git status"
			assertStatus(new StatusResult().setAdded(1).setChanged(1).setRemoved(1), gitStatusUri);

			// "git reset --hard HEAD"
			request = getPostGitIndexRequest(gitIndexUri /* reset all */, ResetType.HARD);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// "git status", should be clean, nothing to commit
			assertStatus(StatusResult.CLEAN, gitStatusUri);
		}
	}

	@Test
	@Ignore("see bug 339397")
	public void testResetAutocrlfTrue() throws Exception {

		// "git config core.autocrlf true"
		Git git = new Git(db);
		StoredConfig config = git.getRepository().getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, Boolean.TRUE);
		config.save();

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// CRLF
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "f" + "\r\n" + "older");
		addFile(testTxt);

		// commit
		WebRequest request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "added new line - crlf", false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert there is nothing to commit
		assertStatus(StatusResult.CLEAN, gitStatusUri);

		// create new file
		String fileName = "new.txt";
		// TODO: don't create URIs out of thin air
		request = getPostFilesRequest(projectId + "/", getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject file = new JSONObject(response.getText());
		String location = file.optString(ProtocolConstants.KEY_LOCATION, null);
		assertNotNull(location);

		// LF
		JSONObject newTxt = getChild(project, "new.txt");
		modifyFile(newTxt, "i'm" + "\n" + "new");

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri /* stage all */);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// reset
		request = getPostGitIndexRequest(gitIndexUri /* reset all */, ResetType.MIXED);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setUntracked(1), gitStatusUri);
	}

	@Test
	public void testResetToRemoteBranch() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
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

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "file change");
			addFile(testTxt);
			commitFile(testTxt, "message", false);

			// git section for the folder
			JSONObject folderGitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String folderGitStatusUri = folderGitSection.getString(GitConstants.KEY_STATUS);
			String folderGitRemoteUri = folderGitSection.getString(GitConstants.KEY_REMOTE);

			assertStatus(StatusResult.CLEAN, folderGitStatusUri);

			JSONArray commitsArray = log(testTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_HEAD));
			assertEquals(2, commitsArray.length());

			JSONObject remoteBranch = getRemoteBranch(folderGitRemoteUri, 1, 0, Constants.MASTER);
			String remoteBranchIndexUri = remoteBranch.getString(GitConstants.KEY_INDEX);
			String remoteBranchName = remoteBranch.getString(ProtocolConstants.KEY_NAME);
			request = getPostGitIndexRequest(remoteBranchIndexUri, remoteBranchName, ResetType.HARD);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			assertStatus(StatusResult.CLEAN, folderGitStatusUri);

			commitsArray = log(testTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_HEAD));
			assertEquals(1, commitsArray.length());
		}
	}

	@Test
	public void testResetPathsAndRef() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);

		WebRequest request = getPostGitIndexRequest(gitIndexUri, new String[] {"test.txt"}, "origin/master", null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	/**
	 * Creates a request to reset HEAD to the given commit.
	 * @param location
	 * @param resetType 
	 * @throws JSONException 
	 * @throws UnsupportedEncodingException 
	 */
	private WebRequest getPostGitIndexRequest(String location, String commit, ResetType resetType) throws JSONException, UnsupportedEncodingException {
		return getPostGitIndexRequest(location, null, commit, resetType.toString());
	}

	/**
	 * Creates a request to reset index.
	 * @param location
	 * @param resetType 
	 * @throws JSONException 
	 * @throws UnsupportedEncodingException 
	 */
	static WebRequest getPostGitIndexRequest(String location, ResetType resetType) throws JSONException, UnsupportedEncodingException {
		return getPostGitIndexRequest(location, null, null, resetType.toString());
	}

	static WebRequest getPostGitIndexRequest(String location, String[] paths, String commit, String resetType) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		if (resetType != null)
			body.put(GitConstants.KEY_RESET_TYPE, resetType);
		if (paths != null) {
			//			assertNull("Cannot mix paths and commit", commit);
			JSONArray jsonPaths = new JSONArray();
			for (String path : paths)
				jsonPaths.put(path);
			body.put(ProtocolConstants.KEY_PATH, jsonPaths);
		}
		if (commit != null)
			body.put(GitConstants.KEY_TAG_COMMIT, commit);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
