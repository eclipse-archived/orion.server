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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Diff;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitDiffTest extends GitTest {
	@Test
	public void testNoDiff() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		String[] parts = getDiff(gitDiffUri);
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

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		String[] parts = getDiff(gitDiffUri);

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
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hello");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		String[] parts = getDiff(gitDiffUri);
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
	public void testDiffFilter() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hi");

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "folder change");

		String[] parts = getDiff(folder1.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF));
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
		assertEquals(sb.toString(), parts[1]);

		String gitDiffUri = testTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF);
		parts = getDiff(gitDiffUri);

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
	public void testDiffPaths() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "hi");

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "folder change");

		WebRequest request = getGetGitDiffRequest(project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF) + "?parts=diff", new String[] {"folder/folder.txt"});
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuilder sb1 = new StringBuilder();
		sb1.append("diff --git a/folder/folder.txt b/folder/folder.txt").append("\n");
		sb1.append("index 0119635..95c4c65 100644").append("\n");
		sb1.append("--- a/folder/folder.txt").append("\n");
		sb1.append("+++ b/folder/folder.txt").append("\n");
		sb1.append("@@ -1 +1 @@").append("\n");
		sb1.append("-folder").append("\n");
		sb1.append("\\ No newline at end of file").append("\n");
		sb1.append("+folder change").append("\n");
		sb1.append("\\ No newline at end of file").append("\n");
		assertEquals(sb1.toString(), response.getText());

		request = getGetGitDiffRequest(project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF) + "?parts=diff", new String[] {"test.txt"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuilder sb2 = new StringBuilder();
		sb2.append("diff --git a/test.txt b/test.txt").append("\n");
		sb2.append("index 30d74d2..32f95c0 100644").append("\n");
		sb2.append("--- a/test.txt").append("\n");
		sb2.append("+++ b/test.txt").append("\n");
		sb2.append("@@ -1 +1 @@").append("\n");
		sb2.append("-test").append("\n");
		sb2.append("\\ No newline at end of file").append("\n");
		sb2.append("+hi").append("\n");
		sb2.append("\\ No newline at end of file").append("\n");
		assertEquals(sb2.toString(), response.getText());

		request = getGetGitDiffRequest(project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF) + "?parts=diff", new String[] {"folder/folder.txt", "test.txt"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		sb1.append(sb2);
		assertEquals(sb1.toString(), response.getText());
	}

	@Test
	public void testDiffCached() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "stage me");

		addFile(testTxt);

		String gitDiffUri = getDiffCachedLocation(testTxt);
		String[] parts = getDiff(gitDiffUri);

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
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "first change");
		addFile(testTxt);

		JSONObject commit1 = commitFile(testTxt, "commit1");

		// modify again
		modifyFile(testTxt, "second change");
		addFile(testTxt);

		// commit2
		JSONObject commit2 = commitFile(testTxt, "commit2");

		String testTxtHeadLocation = testTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_HEAD);
		JSONObject testTxtInitialCommit = findCommitByMessage(testTxtHeadLocation, "Initial commit");
		String gitDiffUri = getDiffLocation(testTxtInitialCommit.getString(GitConstants.KEY_DIFF), commit1.getString(ProtocolConstants.KEY_NAME));
		String[] parts = getDiff(gitDiffUri);
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
		assertEquals(sb.toString(), parts[1]);

		gitDiffUri = getDiffLocation(testTxtInitialCommit.getString(GitConstants.KEY_DIFF), commit2.getString(ProtocolConstants.KEY_NAME));
		parts = getDiff(gitDiffUri);
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
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffCommitWithWorkingTree() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "first change");
		addFile(testTxt);

		JSONObject commit1 = commitFile(testTxt, "commit1");

		modifyFile(testTxt, "second change");
		addFile(testTxt);

		JSONObject commit2 = commitFile(testTxt, "commit2");

		// modify again and leave the change in the working tree only
		modifyFile(testTxt, "third change (in tree only)");

		String commit1DiffUri = commit1.getString(GitConstants.KEY_DIFF);
		String[] parts = getDiff(commit1DiffUri);
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
		assertEquals(sb.toString(), parts[1]);

		String commit2DiffUri = commit2.getString(GitConstants.KEY_DIFF);
		parts = getDiff(commit2DiffUri);
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
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffBranchWithWorkingTree() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject clone = getCloneForGitResource(project);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in master");
		addFile(testTxt);

		commitFile(testTxt, "commit");

		modifyFile(testTxt, "change in the working tree");

		String[] parts = getDiff(findDiffLocationForBranchByName(branchesLocation, Constants.MASTER));
		StringBuilder sb = new StringBuilder();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 597c638..5ae4d45 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-change in master").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+change in the working tree").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertEquals(sb.toString(), parts[1]);
	}

	@Test
	public void testDiffPost() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");
		addFile(testTxt);

		commitFile(testTxt, "commit1", false);

		String testTxtHeadLocation = testTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_HEAD);
		JSONObject testTxtInitialCommit = findCommitByMessage(testTxtHeadLocation, "Initial commit");
		String location = getDiffLocation(testTxtInitialCommit.getString(GitConstants.KEY_DIFF), Constants.HEAD);

		String diffScope = URLEncoder.encode(testTxtInitialCommit.getString(ProtocolConstants.KEY_NAME) + ".." + Constants.HEAD, "UTF-8");
		String expectedLocation = gitSection.getString(GitConstants.KEY_DIFF).replaceAll(GitConstants.KEY_DIFF_DEFAULT, diffScope);
		expectedLocation += "test.txt";
		assertEquals(expectedLocation, location);

		WebRequest request = getGetRequest(location);
		WebResponse response = webConversation.getResponse(request);
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
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");
		addFile(testTxt);

		JSONObject commit = commitFile(testTxt, "commit");

		String testTxtHeadLocation = testTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_HEAD);
		JSONObject testTxtInitialCommit = findCommitByMessage(testTxtHeadLocation, "Initial commit");
		String location = getDiffLocation(testTxtInitialCommit.getString(GitConstants.KEY_DIFF), commit.getString(ProtocolConstants.KEY_NAME));

		String[] parts = getDiff(location + "?parts=uris,diff");

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

		parts = getDiff(location + "?parts=diff,uris");

		assertDiffUris(location, new String[] {"test", "change", "test"}, new JSONObject(parts[0]));
		assertEquals(sb.toString(), parts[1]);

		WebRequest request = getGetRequest(location + "?parts=diff");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(sb.toString(), response.getText());

		request = getGetRequest(location + "?parts=uris");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertDiffUris(location, new String[] {"test", "change", "test"}, new JSONObject(response.getText()));
	}

	@Test
	public void testDiffUntrackedUri() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		String fileName = "new.txt";
		WebRequest request = getPostFilesRequest("", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject newTxt = getChild(project, "new.txt");

		String gitDiffUri = newTxt.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF);

		request = getGetRequest(gitDiffUri + "?parts=uris");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// modified assertDiffUris(...);
		JSONObject jsonPart = new JSONObject(response.getText());
		assertEquals(Diff.TYPE, jsonPart.getString(ProtocolConstants.KEY_TYPE));

		String fileOldUri = jsonPart.getString(GitConstants.KEY_COMMIT_OLD);
		request = getGetRequest(fileOldUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		String fileNewUri = jsonPart.getString(GitConstants.KEY_COMMIT_NEW);
		request = getGetRequest(fileNewUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("", response.getText());

		String fileBaseUri = jsonPart.getString(GitConstants.KEY_COMMIT_BASE);
		request = getGetRequest(fileBaseUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		assertEquals(gitDiffUri, jsonPart.getString(ProtocolConstants.KEY_LOCATION));
	}

	@Test
	public void testDiffWithCommonAncestor() throws Exception {
		// clone: create
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		JSONObject clone = clone(clonePath);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		String branchName = "dev";
		response = branch(branchesLocation, branchName);
		assertEquals(branchName, new JSONObject(response.getText()).getString(ProtocolConstants.KEY_NAME));

		checkoutBranch(cloneLocation, branchName);

		// modify while on 'dev'
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in dev");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// "git add ."
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "commit on dev", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// assert clean
		assertStatus(StatusResult.CLEAN, gitStatusUri);

		// checkout 'master'
		checkoutBranch(cloneLocation, Constants.MASTER);

		// modify the same file on master
		request = getPutFileRequest(clonePath.append("test.txt").toString(), "change in master");
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

		String masterDiffLocation = findDiffLocationForBranchByName(branchesLocation, Constants.MASTER);
		String location = getDiffLocation(masterDiffLocation, branchName);
		// TODO: don't create URIs out of thin air
		location += "test.txt";

		request = getGetRequest(location + "?parts=uris,diff");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] parts = parseMultiPartResponse(response);

		assertDiffUris(location, new String[] {"change in master", "change in dev", "test"}, new JSONObject(parts[0]));
	}

	@Test
	public void testDiffForBranches() throws Exception {
		// clone
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		JSONObject clone = clone(clonePath);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);
		String remotesLocation = clone.getString(GitConstants.KEY_REMOTE);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// modify file on master
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in master");
		addFile(testTxt);
		commitFile(testTxt, "commit in master", false);

		String masterDiffLocation = findDiffLocationForBranchByName(branchesLocation, Constants.MASTER);
		JSONObject originMasterRemoteBranch = getRemoteBranch(remotesLocation, 1, 0, Constants.MASTER);

		// compare master vs origin/master
		String diffLocation = getDiffLocation(masterDiffLocation, originMasterRemoteBranch.getString(ProtocolConstants.KEY_NAME));
		String[] parts = getDiff(diffLocation);
		StringBuilder sb = new StringBuilder();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 597c638..30d74d2 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-change in master").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertEquals(sb.toString(), parts[1]);

		// compare origin/master vs master
		diffLocation = getDiffLocation(originMasterRemoteBranch.getString(GitConstants.KEY_DIFF), Constants.MASTER);
		parts = getDiff(diffLocation);
		sb.setLength(0);
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..597c638 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+change in master").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		assertEquals(sb.toString(), parts[1]);
	}

	private String findDiffLocationForBranchByName(String gitBranchUri, String branchName) throws IOException, SAXException, JSONException {
		JSONObject branches = listBranches(gitBranchUri);
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		for (int i = 0; i < branchesArray.length(); i++) {
			if (branchesArray.getJSONObject(i).getString(ProtocolConstants.KEY_NAME).equals(branchName))
				return branchesArray.getJSONObject(i).getString(GitConstants.KEY_DIFF);
		}
		fail(NLS.bind("Could not find branch: {0}", branchName));
		return null;
	}

	private void assertDiffUris(String expectedLocation, String[] expectedContent, JSONObject jsonPart) throws JSONException, IOException, SAXException {
		assertEquals(Diff.TYPE, jsonPart.getString(ProtocolConstants.KEY_TYPE));

		String fileOldUri = jsonPart.getString(GitConstants.KEY_COMMIT_OLD);
		WebRequest request = getGetRequest(fileOldUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(expectedContent[0], response.getText());

		String fileNewUri = jsonPart.getString(GitConstants.KEY_COMMIT_NEW);
		request = getGetRequest(fileNewUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(expectedContent[1], response.getText());

		String fileBaseUri = jsonPart.getString(GitConstants.KEY_COMMIT_BASE);
		request = getGetRequest(fileBaseUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(expectedContent[2], response.getText());

		assertEquals(expectedLocation, jsonPart.getString(ProtocolConstants.KEY_LOCATION));
	}

	static String[] getDiff(String location) throws IOException, SAXException {
		WebRequest request = getGetGitDiffRequest(location, new String[] {});
		WebConversation conversation = new WebConversation();
		conversation.setExceptionsThrownOnErrorStatus(false);
		WebResponse response = conversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return parseMultiPartResponse(response);
	}

	/**
	 * Creates a request to get the diff result for the given location.
	 * @param location Either an absolute URI, or a workspace-relative URI
	 * @param paths an array containing paths to be included in the diff, can be empty
	 */
	static WebRequest getGetGitDiffRequest(String location, String[] paths) {
		String requestURI = toAbsoluteURI(location);
		for (String path : paths) {
			requestURI = addParam(requestURI, "Path=" + path);
		}
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private static String addParam(String location, String param) {
		location += location.indexOf("?") != -1 ? "&" : "?";
		location += param;
		return location;
	}

	private String getDiffLocation(String oldCommitDiffLocation, String newCommitName) throws JSONException, IOException, SAXException {
		assertDiffUri(oldCommitDiffLocation);
		WebRequest request = getPostGitDiffRequest(oldCommitDiffLocation, newCommitName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertEquals(location, new JSONObject(response.getText()).getString(ProtocolConstants.KEY_LOCATION));
		return location;
	}

	private JSONObject findCommitByMessage(String gitHeadUri, String commitMessage) throws IOException, SAXException, JSONException {
		JSONArray commitsArray = log(gitHeadUri);
		for (int i = 0; i < commitsArray.length(); i++) {
			if (commitsArray.getJSONObject(i).getString(GitConstants.KEY_COMMIT_MESSAGE).equals(commitMessage))
				return commitsArray.getJSONObject(i);
		}
		fail(NLS.bind("Commit with message '{0}' could not be found.", commitMessage));
		return null;
	}

	private String getDiffCachedLocation(JSONObject fileObject) throws JSONException, IOException, SAXException {
		// cannot get status on a file

		// request the status on parent
		String fileLocation = fileObject.getString(ProtocolConstants.KEY_LOCATION);
		WebRequest request = getGetRequest(fileLocation + "?parts=meta");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject fileMetaObject = new JSONObject(response.getText());
		JSONArray parents = fileMetaObject.getJSONArray(ProtocolConstants.KEY_PARENTS);
		JSONObject parentObject = parents.getJSONObject(0);

		request = getGetRequest(parentObject.getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		parentObject = new JSONObject(response.getText());

		request = getGetGitStatusRequest(parentObject.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STATUS));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());

		// find entry for the file
		JSONArray changed = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		for (int i = 0; i < changed.length(); i++) {
			JSONObject changedEntry = changed.getJSONObject(i);
			if (changedEntry.getString(ProtocolConstants.KEY_NAME).equals(fileObject.getString(ProtocolConstants.KEY_NAME))) {
				return changedEntry.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_DIFF);
			}
		}
		fail(NLS.bind("Diff Cached Location for {0}, could not be found", fileObject.getString(ProtocolConstants.KEY_NAME)));
		return null;
	}

	private static WebRequest getPostGitDiffRequest(String location, String str) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_COMMIT_NEW, str);
		str = body.toString();

		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(str), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static String[] parseMultiPartResponse(WebResponse response) throws IOException {
		String typeHeader = response.getHeaderField(ProtocolConstants.HEADER_CONTENT_TYPE);
		String boundary = typeHeader.substring(typeHeader.indexOf("boundary=\"") + 10, typeHeader.length() - 1); //$NON-NLS-1$
		BufferedReader reader = new BufferedReader(new StringReader(response.getText()));
		StringBuilder buf = new StringBuilder();
		List<String> parts = new ArrayList<String>();
		try {
			String line;
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
		} finally {
			IOUtilities.safeClose(reader);
		}
		parts.add(buf.toString());

		assertEquals(2, parts.size());
		// JSON
		assertTrue(parts.get(0).startsWith("{"));
		// diff or empty when there is no difference
		assertTrue(parts.get(1).length() == 0 || parts.get(1).startsWith("diff"));

		return parts.toArray(new String[0]);
	}

	private static void assertDiffUri(String diffUri) {
		URI uri = URI.create(toRelativeURI(diffUri));
		IPath path = new Path(uri.getPath());
		// /gitapi/diff/{scope}/file/{filePath}
		assertTrue(path.segmentCount() > 4);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(Diff.RESOURCE, path.segment(1));
		assertTrue("file".equals(path.segment(3)));
	}
}
