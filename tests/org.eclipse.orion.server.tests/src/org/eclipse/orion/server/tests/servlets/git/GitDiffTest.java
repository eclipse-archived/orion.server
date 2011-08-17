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

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
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
	public void testNoDiff() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		//		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);
		assertEquals("", parts[1]);
	}

	@Test
	public void testDiffAlreadyModified() throws Exception {
		Writer w = new OutputStreamWriter(new FileOutputStream(testFile), "UTF-8");
		try {
			w.write("hello");
		} finally {
			w.close();
		}

		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);

		StringBuilder sb = new StringBuilder();
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
	public void testDiffModifiedByOrion() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuilder sb = new StringBuilder();
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
	public void testDiffFilter() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hi");

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "folder change");

		WebRequest request = getGetGitDiffRequest(folder1.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuilder sb = new StringBuilder();
		sb.append("diff --git a/folder/folder.txt b/folder/folder.txt").append("\n");
		sb.append("index 0119635..95c4c65 100644").append("\n");
		sb.append("--- a/folder/folder.txt").append("\n");
		sb.append("+++ b/folder/folder.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-folder").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+folder change").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		String[] parts = parseMultiPartResponse(response);
		assertEquals(sb.toString(), parts[1]);

		String gitDiffUri = testTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF);
		request = getGetGitDiffRequest(gitDiffUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		parts = parseMultiPartResponse(response);

		assertDiffUris(gitDiffUri, new String[] {"test", "hi", "test"}, new JSONObject(parts[0]));

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
	public void testDiffCached() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "stage me");

		addFile(testTxt);

		// TODO: don't create URIs out of thin air
		gitDiffUri = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, GitConstants.KEY_DIFF_CACHED) + "test.txt";
		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);

		assertDiffUris(gitDiffUri, new String[] {"test", "stage me", "test"}, new JSONObject(parts[0]));

		StringBuilder sb = new StringBuilder();
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
	public void testDiffCommits() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "first change");
		addFile(testTxt);

		// commit1
		WebRequest request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modify again
		modifyFile(testTxt, "second change");
		addFile(testTxt);

		// commit2
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit2", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		final String initialCommit = Constants.HEAD + "^^";
		final String commit1 = Constants.HEAD + "^";
		final String commit2 = Constants.HEAD;
		// TODO: don't create URIs out of thin air
		String enc = URLEncoder.encode(initialCommit + ".." + commit1, "UTF-8");
		gitDiffUri = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc);
		request = getGetGitDiffRequest(gitDiffUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuilder sb = new StringBuilder();
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
		gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);
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
	public void testDiffCommitWithWorkingTree() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "first change");
		addFile(testTxt);

		// commit1
		WebRequest request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		modifyFile(testTxt, "second change");
		addFile(testTxt);

		// commit2
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit2", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modify again and leave the change in the working tree only
		modifyFile(testTxt, "third change (in tree only)");

		String commit1 = db.resolve(Constants.HEAD + "^").getName();
		String commit2 = db.resolve(Constants.HEAD).getName();

		// TODO: don't create URIs out of thin air
		String enc = URLEncoder.encode(commit1, "UTF-8");
		request = getGetGitDiffRequest(new String(gitDiffUri).replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc) + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuilder sb = new StringBuilder();
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
		sb = new StringBuilder();
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
	public void testDiffPost() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");
		addFile(testTxt);

		// commit
		WebRequest request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
		WebResponse response = webConversation.getResponse(request);
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

		assertDiffUris(expectedLocation, new String[] {"test", "change", "test"}, new JSONObject(parts[0]));

		StringBuilder sb = new StringBuilder();
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
	public void testDiffParts() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");
		addFile(testTxt);

		// commit
		WebRequest request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit1", false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// TODO: replace with REST API for git log when ready, see bug 339104
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

		assertDiffUris(location, new String[] {"test", "change", "test"}, new JSONObject(parts[0]));

		StringBuilder sb = new StringBuilder();
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

		assertDiffUris(location, new String[] {"test", "change", "test"}, new JSONObject(parts[0]));
		assertEquals(sb.toString(), parts[1]);

		request = getGetFilesRequest(location + "?parts=diff");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(sb.toString(), response.getText());

		request = getGetFilesRequest(location + "?parts=uris");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertDiffUris(location, new String[] {"test", "change", "test"}, new JSONObject(response.getText()));
	}

	@Test
	public void testDiffUntrackedUri() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		String fileName = "new.txt";
		WebRequest request = getPostFilesRequest(projectId + "/", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject newTxt = getChild(project, "new.txt");

		JSONObject gitSection = newTxt.getJSONObject(GitConstants.KEY_GIT);
		// TODO: don't create URIs out of thin air
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);
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

	@Test
	public void testDiffWithCommonAncestor() throws Exception {
		// clone: create
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String a = "a";
		branch(branchesLocation, a);

		// checkout 'a'
		checkoutBranch(cloneLocation, a);

		// modify while on 'a'
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in a");

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit on a", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		assertStatus(StatusResult.CLEAN, gitStatusUri);

		// checkout 'master'
		checkoutBranch(cloneLocation, Constants.MASTER);

		// modify the same file on master
		request = getPutFileRequest(projectId + "/test.txt", "change in master");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit on master", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		assertStatus(StatusResult.CLEAN, gitStatusUri);

		// TODO: replace with REST API for git log when ready, see bug 339104
		// TODO: don't create URIs out of thin air
		String enc = URLEncoder.encode(Constants.MASTER + ".." + a, "UTF-8");
		String location = gitDiffUri.replaceAll(GitConstants.KEY_DIFF_DEFAULT, enc);
		location += "test.txt";

		request = getGetFilesRequest(location + "?parts=uris,diff");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);

		assertDiffUris(location, new String[] {"change in master", "change in a", "test"}, new JSONObject(parts[0]));
	}

	private void assertDiffUris(String expectedLocation, String[] expectedContent, JSONObject jsonPart) throws JSONException, IOException, SAXException {
		JSONObject gitSection = jsonPart.getJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		String fileOldUri = gitSection.getString(GitConstants.KEY_COMMIT_OLD);
		assertNotNull(fileOldUri);
		WebRequest request = getGetFilesRequest(fileOldUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(expectedContent[0], response.getText());

		String fileNewUri = gitSection.getString(GitConstants.KEY_COMMIT_NEW);
		assertNotNull(fileNewUri);
		request = getGetFilesRequest(fileNewUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(expectedContent[1], response.getText());

		String fileBaseUri = gitSection.getString(GitConstants.KEY_COMMIT_BASE);
		assertNotNull(fileBaseUri);
		request = getGetFilesRequest(fileBaseUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(expectedContent[2], response.getText());

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
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
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
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
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

		StringBuilder buf = new StringBuilder();
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
