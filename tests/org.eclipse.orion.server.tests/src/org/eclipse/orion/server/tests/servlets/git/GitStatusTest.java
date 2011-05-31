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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
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

	// "status -s" > ""
	@Test
	public void testStatusClean() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		WebRequest request = getGetGitStatusRequest(gitStatusUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		assertStatusClean(statusResponse);
	}

	// "status -s" > "A  new.txt", staged
	@Test
	public void testStatusAdded() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		String fileName = "new.txt";
		WebRequest request = getPostFilesRequest(projectId + "/", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "new.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(1, statusArray.length());
		assertEquals(fileName, statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	@Ignore("not yet implemented")
	public void testStatusAssumeUnchanged() {
		// TODO: see bug 338913
	}

	// "status -s" > "M  test.txt", staged
	@Test
	public void testStatusChanged() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	// "status -s" > "MM test.txt", portions staged for commit
	@Test
	public void testStatusChangedAndModified() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change in index");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getPutFileRequest(projectId + "/test.txt", "second change, in working tree");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	// "status -s" > " D test.txt", not staged
	@Test
	public void testStatusMissing() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		WebRequest request = getDeleteFilesRequest(projectId + "/test.txt");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());

		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	// "status -s" > " M test.txt", not staged
	@Test
	public void testStatusModified() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	// "status -s" > "D  test.txt", staged
	@Test
	public void testStatusRemoved() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: replace with REST API for git rm
		Git git = new Git(db);
		RmCommand rm = git.rm();
		rm.addFilepattern("test.txt");
		rm.call();

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	// "status -s" > "?? new.txt", not staged
	@Test
	public void testStatusUntracked() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		String fileName = "new.txt";
		WebRequest request = getPostFilesRequest(projectId + "/", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(1, statusArray.length());
		assertEquals(fileName, statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
	}

	@Test
	public void testStatusWithPath() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		String contentLocation = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		WebRequest request = getGetFilesRequest(contentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		String childrenLocation = project.optString(ProtocolConstants.KEY_CHILDREN_LOCATION, null);
		assertNotNull(childrenLocation);

		request = getGetFilesRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		List<JSONObject> children = getDirectoryChildren(project);
		JSONObject folder = getChildByName(children, "folder");

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/test.txt", "file change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/folder/folder.txt", "folder change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		// GET /git/status/file/{proj}/
		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(2, statusArray.length());
		assertNotNull(getChildByName(statusArray, "test.txt"));
		assertNotNull(getChildByKey(statusArray, GitConstants.KEY_PATH, "test.txt"));
		assertNotNull(getChildByName(statusArray, "folder/folder.txt"));
		assertNotNull(getChildByKey(statusArray, GitConstants.KEY_PATH, "folder/folder.txt"));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		// GET /git/status/file/{proj}/test.txt
		// TODO: don't create URIs out of thin air
		request = getGetGitStatusRequest(gitStatusUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

		gitSection = folder.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		// GET /git/status/file/{proj}/folder/
		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(2, statusArray.length());
		assertNotNull(getChildByName(statusArray, "test.txt"));
		assertNotNull(getChildByKey(statusArray, GitConstants.KEY_PATH, "../test.txt"));
		assertNotNull(getChildByName(statusArray, "folder/folder.txt"));
		assertNotNull(getChildByKey(statusArray, GitConstants.KEY_PATH, "folder.txt"));
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	public void testStatusLocation() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "file change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getPutFileRequest(projectId + "/folder/folder.txt", "folder change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		// GET /git/status/file/{proj}/
		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(2, statusArray.length());
		JSONObject child = getChildByName(statusArray, "test.txt");
		assertChildLocation(child, "file change");
		child = getChildByName(statusArray, "folder/folder.txt");
		assertNotNull(child);
		assertChildLocation(child, "folder change");
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		String stageAll = statusResponse.getString(GitConstants.KEY_INDEX);
		assertNotNull(stageAll);
		String commitAll = statusResponse.getString(GitConstants.KEY_COMMIT);
		assertNotNull(commitAll);

		request = GitAddTest.getPutGitIndexRequest(stageAll);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(2, statusArray.length());
		child = getChildByName(statusArray, "test.txt");
		assertChildLocation(child, "file change");
		child = getChildByName(statusArray, "folder/folder.txt");
		assertNotNull(child);
		assertChildLocation(child, "folder change");
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		request = GitCommitTest.getPostGitCommitRequest(commitAll, "committing all changes", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		assertStatusClean(statusResponse);
	}

	@Test
	public void testStatusDiff() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "in index");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// "git add {path}"
		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getPutFileRequest(projectId + "/test.txt", "in working tree");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// GET /git/status/file/{proj}/
		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(1, statusArray.length());
		JSONObject child = getChildByName(statusArray, "test.txt");
		child = getChildByName(statusArray, "test.txt");
		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..0123892 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+in index").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertChildDiff(child, sb.toString());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		child = getChildByName(statusArray, "test.txt");
		sb.setLength(0);
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 0123892..791a2b7 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-in index").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+in working tree").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertChildDiff(child, sb.toString());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
	}

	@Test
	public void testStatusCommit() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "index");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// "git add {path}"
		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getPutFileRequest(projectId + "/test.txt", "working tree");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// GET /git/status/file/{proj}/
		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(1, statusArray.length());
		JSONObject child = getChildByName(statusArray, "test.txt");
		assertChildIndex(child, "index");
		assertChildHead(child, "test");
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		// TODO: check content
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

		// TODO: commit and check status
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "committing all changes", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		statusResponse = new JSONObject(response.getText());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		// TODO: check content
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());

	}

	// "status -s" > "UU test.txt", both modified
	@Test
	public void testConfilct() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1: create
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		IPath clonePath1 = new Path("file").append(project1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation1 = clone(clonePath1).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// get project metadata
		WebRequest request = getGetFilesRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
		String gitCommitUri1 = gitSection1.getString(GitConstants.KEY_COMMIT);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone2: create
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		IPath clonePath2 = new Path("file").append(project2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation2 = clone(clonePath2).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// get project metadata
		request = getGetFilesRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
		String gitCommitUri2 = gitSection2.getString(GitConstants.KEY_COMMIT);
		String gitStatusUri2 = gitSection2.getString(GitConstants.KEY_STATUS);

		// clone1: change
		request = getPutFileRequest(projectId1 + "/test.txt", "change from clone1");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri1, "change from clone1", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		ServerStatus pushStatus = push(gitRemoteUri1, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// this is how EGit checks for conflicts
		Repository db1 = getRepositoryForContentLocation(contentLocation1);
		Git git = new Git(db1);
		DirCache cache = db1.readDirCache();
		DirCacheEntry entry = cache.getEntry("test.txt");
		assertTrue(entry.getStage() == 0);

		// clone2: change
		request = getPutFileRequest(projectId2 + "/test.txt", "change from clone2");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri2, "change from clone2", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: pull
		// TODO: replace with REST API for git pull once bug 339114 is fixed
		Repository db2 = getRepositoryForContentLocation(contentLocation2);
		git = new Git(db2);
		PullResult pullResult = git.pull().call();
		assertEquals(pullResult.getMergeResult().getMergeStatus(), MergeStatus.CONFLICTING);

		// this is how EGit checks for conflicts
		cache = db2.readDirCache();
		entry = cache.getEntry("test.txt");
		assertTrue(entry.getStage() > 0);

		request = getGetGitStatusRequest(gitStatusUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING);
		assertEquals(1, statusArray.length());
		assertNotNull(getChildByName(statusArray, "test.txt"));
	}

	@Test
	public void testFileLogFromStatus() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);

		// add new files to the local repository so they can be deleted and removed in the test later on
		// missing
		String missingFile = "missing.txt";
		WebRequest request = getPutFileRequest(projectId + "/" + missingFile, "you'll miss me");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// "git add {path}"
		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + missingFile);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		String removedFile = "removed.txt";
		request = getPutFileRequest(projectId + "/" + removedFile, "I'll be removed");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// "git add {path}"
		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + removedFile);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "committing all changes", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// make the file missing
		request = getDeleteFilesRequest(projectId + "/" + missingFile);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// remove the file
		Git git = new Git(db);
		RmCommand rm = git.rm();
		rm.addFilepattern(removedFile);
		rm.call();

		// untracked file
		String untrackedFile = "untracked.txt";
		request = getPostFilesRequest(projectId + "/", getNewFileJSON(untrackedFile).toString(), untrackedFile);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		// added file
		String addedFile = "added.txt";
		request = getPostFilesRequest(projectId + "/", getNewFileJSON(addedFile).toString(), addedFile);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + addedFile);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// changed file
		request = getPutFileRequest(projectId + "/test.txt", "change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modified file
		request = getPutFileRequest(projectId + "/test.txt", "second change, in working tree");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// get status
		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(1, statusArray.length());
		assertEquals(addedFile, statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
		gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
		JSONArray log = log(gitCommitUri, false);
		assertEquals(0, log.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
		gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
		log = log(gitCommitUri, false);
		assertEquals(1, log.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(1, statusArray.length());
		assertEquals(missingFile, statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
		gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
		log = log(gitCommitUri, false);
		assertEquals(1, log.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(1, statusArray.length());
		assertEquals("test.txt", statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
		gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
		log = log(gitCommitUri, false);
		assertEquals(1, log.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(1, statusArray.length());
		assertEquals(removedFile, statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
		gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
		log = log(gitCommitUri, false);
		assertEquals(1, log.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(1, statusArray.length());
		assertEquals(untrackedFile, statusArray.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
		gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
		log = log(gitCommitUri, false);
		assertEquals(0, log.length());
	}

	@Test
	public void testStatusForRepositoryInFolder() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).append("myClone").makeAbsolute();

		// /file/{id}/myClone
		JSONObject clone = clone(clonePath);

		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetFilesRequest(cloneContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNull(gitSection);

		request = getGetFilesRequest(cloneContentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject folder = new JSONObject(response.getText());
		gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());
		assertStatusClean(statusResponse);
	}

	private void assertChildLocation(JSONObject child, String expectedFileContent) throws JSONException, IOException, SAXException {
		assertNotNull(child);
		String location = child.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(location);
		WebRequest request = getGetFilesRequest(location);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", expectedFileContent, response.getText());
	}

	private void assertChildDiff(JSONObject child, String expectedDiff) throws IOException, SAXException {
		assertNotNull(child);
		JSONObject gitSection = child.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);
		WebRequest request = GitDiffTest.getGetGitDiffRequest(gitDiffUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", expectedDiff, GitDiffTest.parseMultiPartResponse(response)[1]);
	}

	private void assertChildHead(JSONObject child, String expectedFileContent) throws JSONException, IOException, SAXException {
		assertNotNull(child);
		JSONObject gitSection = child.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String commit = gitSection.getString(GitConstants.KEY_COMMIT);
		assertNotNull(commit);
		WebRequest request = GitCommitTest.getGetGitCommitRequest(commit, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", expectedFileContent, response.getText());
	}

	private void assertChildIndex(JSONObject child, String expectedFileContent) throws JSONException, IOException, SAXException {
		assertNotNull(child);
		JSONObject gitSection = child.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String index = gitSection.getString(GitConstants.KEY_INDEX);
		assertNotNull(index);
		WebRequest request = GitIndexTest.getGetGitIndexRequest(index);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", expectedFileContent, response.getText());
	}

	static JSONObject getChildByName(JSONArray array, String value) throws JSONException {
		return getChildByKey(array, ProtocolConstants.KEY_NAME, value);
	}

	static JSONObject getChildByKey(JSONArray array, String key, String value) throws JSONException {
		List<JSONObject> children = new ArrayList<JSONObject>();
		for (int i = 0; i < array.length(); i++) {
			children.add(array.getJSONObject(i));
		}
		return getChildByKey(children, key, value);
	}

	static void assertStatusClean(JSONObject statusResponse) throws JSONException {
		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(0, statusArray.length());
		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING);
		assertEquals(0, statusArray.length());
	}

	/**
	 * Creates a request to get the status result for the given location.
	 * @param location Either an absolute URI, or a workspace-relative URI
	 */
	static WebRequest getGetGitStatusRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.STATUS_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	public static String getCloneUri(String statusUri) throws JSONException, IOException, SAXException, CoreException {
		assertStatusUri(statusUri);
		WebRequest request = getGetGitStatusRequest(statusUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject status = new JSONObject(response.getText());
		String cloneUri = status.getString(GitConstants.KEY_CLONE);
		assertCloneUri(cloneUri);
		return cloneUri;
	}
}
