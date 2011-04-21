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
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitDiffTest extends GitTest {
	@Test
	public void testNoDiff() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);

		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		//		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);
		assertEquals("", parts[1]);
	}

	@Test
	public void testDiffAlreadyModified() throws IOException, SAXException, JSONException {
		Writer w = new OutputStreamWriter(new FileOutputStream(testFile), "UTF-8");
		try {
			w.write("hello");
		} finally {
			w.close();
		}

		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);

		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);

		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..b6fc4c6 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+hello").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffModifiedByOrion() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "hello");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);

		request = getGetGitDiffRequest(gitDiffUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..b6fc4c6 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+hello").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		String[] parts = parseMultiPartResponse(response);
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffFilter() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "hi");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		request = getPutFileRequest(projectId + "/folder/folder.txt", "hello");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		request = getGetGitDiffRequest(gitDiffUri + "folder/");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/folder/folder.txt b/folder/folder.txt").append("\n");
		sb.append("index 0119635..b6fc4c6 100644").append("\n");
		sb.append("--- a/folder/folder.txt").append("\n");
		sb.append("+++ b/folder/folder.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-folder").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+hello").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		String[] parts = parseMultiPartResponse(response);
		assertEquals(sb.toString(), parts[1]);

		// TODO: don't create URIs out of thin air
		gitDiffUri += "test.txt";
		request = getGetGitDiffRequest(gitDiffUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		parts = parseMultiPartResponse(response);

		assertDiffUris(gitDiffUri, "test", "hi", new JSONObject(parts[0]));

		sb.setLength(0);
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..32f95c0 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+hi").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffCached() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "stage me");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// "git add test.txt"
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		gitDiffUri = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, GitConstants.KEY_DIFF_CACHED) + "test.txt";
		request = getGetGitDiffRequest(gitDiffUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);

		assertDiffUris(gitDiffUri, "test", "stage me", new JSONObject(parts[0]));

		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..b874aa3 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+stage me").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffCommits() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
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
		WebResponse response = webConversation.getResponse(request);
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

		String initialCommit = Constants.HEAD + "^^";
		String commit1 = Constants.HEAD + "^";
		String commit2 = Constants.HEAD;
		// TODO: don't create URIs out of thin air
		String enc = URLEncoder.encode(initialCommit + ".." + commit1, "UTF-8");
		gitDiffUri = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc);
		request = getGetGitDiffRequest(gitDiffUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..3c26ed4 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+first change").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		String[] parts = parseMultiPartResponse(response);
		assertEquals(sb.toString(), parts[1]);

		String initialCommitId = db.resolve(initialCommit).getName();
		String commit2Id = db.resolve(commit2).getName();
		gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		// TODO: don't create URIs out of thin air
		gitDiffUri = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, initialCommitId + ".." + commit2Id);
		request = getGetGitDiffRequest(gitDiffUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		sb.setLength(0);
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..58bcb48 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+second change").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		parts = parseMultiPartResponse(response);
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffCommitWithWorkingTree() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
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
		WebResponse response = webConversation.getResponse(request);
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

		// modify again and leave the change in the working tree only
		request = getPutFileRequest(projectId + "/test.txt", "third change (in tree only)");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		String commit1 = db.resolve(Constants.HEAD + "^").getName();
		String commit2 = db.resolve(Constants.HEAD).getName();

		// TODO: don't create URIs out of thin air
		String enc = URLEncoder.encode(commit1, "UTF-8");
		request = getGetGitDiffRequest(new String(gitDiffUri).replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc) + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 3c26ed4..4cb5d38 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-first change").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+third change (in tree only)").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		String[] parts = parseMultiPartResponse(response);
		assertEquals(sb.toString(), parts[1]);

		// TODO: don't create URIs out of thin air
		enc = URLEncoder.encode(commit2, "UTF-8");
		request = getGetGitDiffRequest(new String(gitDiffUri).replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc) + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 58bcb48..4cb5d38 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-second change").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+third change (in tree only)").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		parts = parseMultiPartResponse(response);
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffPost() throws JSONException, IOException, SAXException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
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
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit1", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// replace with REST API for git log when ready, see bug 339104
		String enc = URLEncoder.encode(Constants.HEAD + "^", "UTF-8");
		gitDiffUri = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc);
		request = getPostGitDiffRequest(gitDiffUri + "/test.txt", Constants.HEAD);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(location);
		gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		enc = URLEncoder.encode(Constants.HEAD + "^.." + Constants.HEAD, "UTF-8");
		// TODO: don't create URIs out of thin air
		String expectedLocation = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc);
		expectedLocation += "test.txt";
		assertEquals(expectedLocation, location);

		request = getGetFilesRequest(location);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);

		assertDiffUris(expectedLocation, "test", "change", new JSONObject(parts[0]));

		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..8013df8 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+change").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffParts() throws JSONException, IOException, SAXException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
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
		WebRequest request = getPutFileRequest(projectId + "/test.txt", "change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri, "commit1", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: don't create URIs out of thin air
		// replace with REST API for git log when ready, see bug 339104
		String initialCommit = Constants.HEAD + "^";
		String commit = Constants.HEAD;
		// TODO: don't create URIs out of thin air
		String enc = URLEncoder.encode(initialCommit + ".." + commit, "UTF-8");
		String location = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc);
		location += "test.txt";

		request = getGetFilesRequest(location + "?parts=uris,diff");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);

		assertDiffUris(location, "test", "change", new JSONObject(parts[0]));

		StringBuffer sb = new StringBuffer();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..8013df8 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+change").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertEquals(sb.toString(), parts[1]);

		request = getGetFilesRequest(location + "?parts=diff,uris");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		parseMultiPartResponse(response);

		assertDiffUris(location, "test", "change", new JSONObject(parts[0]));
		assertEquals(sb.toString(), parts[1]);

		request = getGetFilesRequest(location + "?parts=diff");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(sb.toString(), response.getText());

		request = getGetFilesRequest(location + "?parts=uris");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertDiffUris(location, "test", "change", new JSONObject(response.getText()));
	}

	@Test
	public void testDiffUntrackedUri() throws JSONException, IOException, SAXException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		String fileName = "new.txt";
		WebRequest request = getPostFilesRequest(projectId + "/", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitDiffUri = gitSection.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);
		// TODO: don't create URIs out of thin air
		gitDiffUri += "new.txt";

		request = getGetFilesRequest(gitDiffUri + "?parts=uris");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assertDiffUris(gitDiffUri, "test", "change", new JSONObject(response.getText()));
		gitSection = new JSONObject(response.getText()).getJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String fileOldUri = gitSection.getString(GitConstants.KEY_COMMIT_OLD);
		assertNotNull(fileOldUri);
		request = getGetFilesRequest(fileOldUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
		// assertEquals(expectedOld, response.getText());
		String fileNewUri = gitSection.getString(GitConstants.KEY_COMMIT_NEW);
		assertNotNull(fileNewUri);
		request = getGetFilesRequest(fileNewUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("", response.getText());

		String diffUri = gitSection.getString(GitConstants.KEY_DIFF);
		assertNotNull(diffUri);
		assertEquals(gitDiffUri, diffUri);
	}

	private void assertDiffUris(String expectedLocation, String expectedOld, String expectedNew, JSONObject jsonPart) throws JSONException, IOException, SAXException {
		JSONObject gitSection = jsonPart.getJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String fileOldUri = gitSection.getString(GitConstants.KEY_COMMIT_OLD);
		assertNotNull(fileOldUri);
		WebRequest request = getGetFilesRequest(fileOldUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(expectedOld, response.getText());
		String fileNewUri = gitSection.getString(GitConstants.KEY_COMMIT_NEW);
		assertNotNull(fileNewUri);
		request = getGetFilesRequest(fileNewUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(expectedNew, response.getText());

		String diffUri = gitSection.getString(GitConstants.KEY_DIFF);
		assertNotNull(diffUri);
		assertEquals(expectedLocation, diffUri);
	}

	/**
	 * Creates a request to get the diff result for the given location.
	 * @param location Either an absolute URI, or a workspace-relative URI
	 */
	static WebRequest getGetGitDiffRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.DIFF_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private static WebRequest getPostGitDiffRequest(String location, String right) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.DIFF_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_COMMIT_NEW, right);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static String[] parseMultiPartResponse(WebResponse response) throws IOException {
		String typeHeader = response.getHeaderField(ProtocolConstants.HEADER_CONTENT_TYPE);
		String boundary = typeHeader.substring(typeHeader.indexOf("boundary=\"") + 10, typeHeader.length() - 1); //$NON-NLS-1$
		BufferedReader reader = new BufferedReader(new StringReader(response.getText()));

		StringBuffer buf = new StringBuffer();
		String line;
		List<String> parts = new ArrayList<String>();
		while ((line = reader.readLine()) != null) {
			if (line.equals("--" + boundary)) {
				line = reader.readLine(); // Content-Type:{...}
				if (buf.length() > 0) {
					parts.add(buf.toString());
					buf.setLength(0);
				}
			} else {
				if (buf.length() > 0)
					buf.append("\n");
				buf.append(line);
			}
		}
		parts.add(buf.toString());

		assertEquals(2, parts.size());
		// JSON
		assertTrue(parts.get(0).startsWith("{"));
		// diff or empty when there is no difference
		assertTrue(parts.get(1).length() == 0 || parts.get(1).startsWith("diff"));

		return parts.toArray(new String[0]);
	}

}
