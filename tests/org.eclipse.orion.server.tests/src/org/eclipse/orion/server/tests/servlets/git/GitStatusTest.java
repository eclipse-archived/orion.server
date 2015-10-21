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

import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitStatusTest extends GitTest {

	@Test
	public void testStatusCleanClone() throws Exception {
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

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			assertStatus(StatusResult.CLEAN, gitStatusUri);
		}
	}

	// "status -s" > ""
	@Test
	public void testStatusCleanLink() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(StatusResult.CLEAN, gitStatusUri);
	}

	// "status -s" > "A  new.txt", staged
	@Test
	public void testStatusAdded() throws Exception {
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

			String fileName = "new.txt";
			request = getPostFilesRequest(folder.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			// git section for the new file
			JSONObject newFile = getChild(folder, "new.txt");

			// "git add {path}"
			addFile(newFile);

			// git section for the folder
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			gitSection.getString(GitConstants.KEY_INDEX);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			assertStatus(new StatusResult().setAddedNames(fileName), gitStatusUri);
		}
	}

	@Test
	@Ignore("not yet implemented")
	public void testStatusAssumeUnchanged() {
		// TODO: see bug 338913
	}

	// "status -s" > "M  test.txt", staged
	@Test
	public void testStatusChanged() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		addFile(testTxt);

		assertStatus(new StatusResult().setChangedNames("test.txt"), gitStatusUri);
	}

	// "status -s" > "MM test.txt", portions staged for commit
	@Test
	public void testStatusChangedAndModified() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in index");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		addFile(testTxt);

		modifyFile(testTxt, "second change, in working tree");

		assertStatus(new StatusResult().setChangedNames("test.txt").setModifiedNames("test.txt"), gitStatusUri);
	}

	// "status -s" > " D test.txt", not staged
	@Test
	public void testStatusMissing() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		deleteFile(testTxt);

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(new StatusResult().setMissingNames("test.txt"), gitStatusUri);
	}

	// "status -s" > " M test.txt", not staged
	@Test
	public void testStatusModified() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(new StatusResult().setModifiedNames("test.txt"), gitStatusUri);
	}

	// "status -s" > "D  test.txt", staged
	@Test
	public void testStatusRemoved() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");

		// delete the file (required until bug 349299 is fixed)
		deleteFile(testTxt);

		// stage the deletion: 'git add -u test.txt', should be 'git rm test.txt'
		addFile(testTxt);

		// check status of the project
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		assertStatus(new StatusResult().setRemovedNames("test.txt"), gitStatusUri);
	}

	// "status -s" > "?? new.txt", not staged
	@Test
	public void testStatusUntracked() throws Exception {
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

			String fileName = "new.txt";
			request = getPostFilesRequest(folder.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			assertStatus(new StatusResult().setUntrackedNames(fileName), gitStatusUri);
		}
	}

	@Test
	public void testStatusWithPath() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		String projectName = getMethodName().concat("Project");
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "file change");

		JSONObject folder = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder, "folder.txt");
		modifyFile(folderTxt, "folder change");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		// GET /git/status/file/{proj}/
		assertStatus(new StatusResult().setModifiedNames( "test.txt", "folder/folder.txt").setModifiedPaths("test.txt", "folder/folder.txt"), gitStatusUri);

		// GET /git/status/file/{proj}/test.txt
		WebRequest request = getGetGitStatusRequest(gitStatusUri + "test.txt");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

		gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
		gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

		// GET /git/status/file/{proj}/folder/
		assertStatus(new StatusResult().setModifiedNames("test.txt", "folder/folder.txt").setModifiedPaths("../test.txt", "folder.txt"), gitStatusUri);
	}

	@Test
	public void testStatusLocation() throws Exception {
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

			JSONObject folder1 = getChild(folder, "folder");
			JSONObject folderTxt = getChild(folder1, "folder.txt");
			modifyFile(folderTxt, "folder change");

			// git section for the folder
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			JSONObject statusResponse = assertStatus(new StatusResult().setModifiedNames("test.txt", "folder/folder.txt").setModifiedContents("file change", "folder change"), gitStatusUri);

			String stageAll = statusResponse.getString(GitConstants.KEY_INDEX);
			String commitAll = statusResponse.getString(GitConstants.KEY_COMMIT);

			request = GitAddTest.getPutGitIndexRequest(stageAll);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			assertStatus(new StatusResult().setChangedNames("test.txt", "folder/folder.txt").setChangedContents("file change", "folder change"), gitStatusUri);

			request = GitCommitTest.getPostGitCommitRequest(commitAll, "committing all changes", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			assertStatus(StatusResult.CLEAN, gitStatusUri);
		}
	}

	@Test
	public void testStatusDiff() throws Exception {
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
			modifyFile(testTxt, "in index");
			addFile(testTxt);

			modifyFile(testTxt, "in working tree");

			// git section for the folder
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

			StringBuilder changedDiff = new StringBuilder();
			changedDiff.append("diff --git a/test.txt b/test.txt").append("\n");
			changedDiff.append("index 30d74d2..0123892 100644").append("\n");
			changedDiff.append("--- a/test.txt").append("\n");
			changedDiff.append("+++ b/test.txt").append("\n");
			changedDiff.append("@@ -1 +1 @@").append("\n");
			changedDiff.append("-test").append("\n");
			changedDiff.append("\\ No newline at end of file").append("\n");
			changedDiff.append("+in index").append("\n");
			changedDiff.append("\\ No newline at end of file").append("\n");

			StringBuilder modifiedDiff = new StringBuilder();
			modifiedDiff.append("diff --git a/test.txt b/test.txt").append("\n");
			modifiedDiff.append("index 0123892..791a2b7 100644").append("\n");
			modifiedDiff.append("--- a/test.txt").append("\n");
			modifiedDiff.append("+++ b/test.txt").append("\n");
			modifiedDiff.append("@@ -1 +1 @@").append("\n");
			modifiedDiff.append("-in index").append("\n");
			modifiedDiff.append("\\ No newline at end of file").append("\n");
			modifiedDiff.append("+in working tree").append("\n");
			modifiedDiff.append("\\ No newline at end of file").append("\n");

			assertStatus(new StatusResult().setChangedNames("test.txt").setChangedDiffs(changedDiff.toString()).setModifiedNames("test.txt").setModifiedDiffs(modifiedDiff.toString()), gitStatusUri);
		}
	}

	@Test
	public void testStatusSubfolderDiff() throws Exception {
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

			// modify file
			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "hello");

			// git section for the subfolder
			JSONObject subfolder = getChild(folder, "folder");
			JSONObject gitSection = subfolder.getJSONObject(GitConstants.KEY_GIT);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);

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

			assertStatus(new StatusResult().setModifiedNames("test.txt").setModifiedDiffs(sb.toString()).setModifiedPaths("../test.txt"), gitStatusUri);
		}
	}

	@Test
	public void testStatusCommit() throws Exception {
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
			modifyFile(testTxt, "index");

			addFile(testTxt);

			modifyFile(testTxt, "working tree");

			// git section for the folder
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			assertStatus(new StatusResult().setChangedNames("test.txt").setChangedIndexContents("index").setChangedHeadContents("test").setModifiedNames("test.txt").setModifiedContents("working tree"), gitStatusUri);

			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "committing all changes", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			assertStatus(new StatusResult().setModifiedNames("test.txt").setModifiedContents("working tree"), gitStatusUri);
		}
	}

	// "status -s" > "UU test.txt", both modified
	@Test
	public void testConflict() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);

		// clone1: create
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		IPath clonePath1 = getClonePath(workspaceId, project1);
		String contentLocation1 = clone(clonePath1).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// get project metadata
		WebRequest request = getGetRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
		String gitHeadUri1 = gitSection1.getString(GitConstants.KEY_HEAD);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone2: create
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName().concat("Project2"), null);
		IPath clonePath2 = getClonePath(workspaceId, project2);
		String contentLocation2 = clone(clonePath2).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// overwrite user settings, do not rebase when pulling, see bug 372489
		StoredConfig cfg = getRepositoryForContentLocation(contentLocation2).getConfig();
		cfg.setBoolean(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_REBASE, false);
		cfg.save();

		// get project metadata
		request = getGetRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);
		String gitStatusUri2 = gitSection2.getString(GitConstants.KEY_STATUS);

		// clone1: change
		JSONObject testTxt1 = getChild(project1, "test.txt");
		modifyFile(testTxt1, "change from clone1");

		// clone1: add
		addFile(testTxt1);

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri1, "change from clone1", false);
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
		JSONObject testTxt2 = getChild(project2, "test.txt");
		modifyFile(testTxt2, "change from clone2");

		// clone2: add
		addFile(testTxt2);

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "change from clone2", false);
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

		assertStatus(new StatusResult().setConflictingNames("test.txt"), gitStatusUri2);
	}

	@Test
	public void testFileLogFromStatus() throws Exception {
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

			// add new files to the local repository so they can be deleted and removed in the test later on
			// missing
			String missingFileName = "missing.txt";
			request = getPutFileRequest(folder.getString(ProtocolConstants.KEY_LOCATION) + missingFileName, "you'll miss me");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			JSONObject missingTxt = getChild(folder, missingFileName);
			addFile(missingTxt);

			String removedFileName = "removed.txt";
			request = getPutFileRequest(folder.getString(ProtocolConstants.KEY_LOCATION) + removedFileName, "I'll be removed");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			JSONObject removedTxt = getChild(folder, removedFileName);
			addFile(removedTxt);

			// git section for the folder
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "committing all changes", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// make the file missing
			deleteFile(missingTxt);

			// remove the file
			Repository repository = getRepositoryForContentLocation(cloneContentLocation);
			Git git = new Git(repository);
			RmCommand rm = git.rm();
			rm.addFilepattern(removedFileName);
			rm.call();

			// untracked file
			String untrackedFileName = "untracked.txt";
			request = getPostFilesRequest(folder.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(untrackedFileName).toString(), untrackedFileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			// added file
			String addedFileName = "added.txt";
			request = getPostFilesRequest(folder.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(addedFileName).toString(), addedFileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			JSONObject addedTxt = getChild(folder, addedFileName);
			addFile(addedTxt);

			// changed file
			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "change");
			addFile(testTxt);

			// modified file
			modifyFile(testTxt, "second change, in working tree");

			// get status
			assertStatus(new StatusResult().setAddedNames(addedFileName).setAddedLogLengths(0).setChangedNames("test.txt").setChangedLogLengths(1).setMissingNames(missingFileName).setMissingLogLengths(1).setModifiedNames("test.txt").setModifiedLogLengths(1).setRemovedNames(removedFileName).setRemovedLogLengths(1).setUntrackedNames(untrackedFileName).setUntrackedLogLengths(0), folder.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STATUS));
		}
	}

	@Test
	public void testCloneAndBranchNameFromStatus() throws Exception {
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

			// get status
			request = getGetGitStatusRequest(folder.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STATUS));
			response = webConversation.getResponse(request);
			assertTrue(HttpURLConnection.HTTP_OK == response.getResponseCode() || HttpURLConnection.HTTP_ACCEPTED == response.getResponseCode());
			ServerStatus status = waitForTask(response);
			assertTrue(status.toString(), status.isOK());
			JSONObject statusResponse = status.getJsonData();
			String cloneLocation = statusResponse.getString(GitConstants.KEY_CLONE);
			assertCloneUri(cloneLocation);

			request = getGetRequest(cloneLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			clone = new JSONObject(response.getText());
			JSONArray clonesArray = clone.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(1, clonesArray.length());
			clone = clonesArray.getJSONObject(0);
			assertEquals(folder.getString(ProtocolConstants.KEY_NAME), clone.getString(ProtocolConstants.KEY_NAME));

			// get branch details
			String branchLocation = clone.getString(GitConstants.KEY_BRANCH);
			request = getGetRequest(branchLocation);
			response = webConversation.getResponse(request);
			status = waitForTask(response);
			assertTrue(status.toString(), status.isOK());
			JSONObject branches = status.getJsonData();
			assertEquals(Constants.MASTER, GitBranchTest.getCurrentBranch(branches).getString(ProtocolConstants.KEY_NAME));
		}
	}
}
