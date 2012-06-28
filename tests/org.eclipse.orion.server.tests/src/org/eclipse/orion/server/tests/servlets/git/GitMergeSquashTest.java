/*******************************************************************************
 * Copyright (c)  2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitMergeSquashTest extends GitTest {
	@Test
	public void testMergeSquashSelf() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// "git merge master"
		JSONObject merge = merge(gitHeadUri, Constants.MASTER, true);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.ALREADY_UP_TO_DATE, mergeResult);
	}

	@Test
	public void testMergeSquash() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// create branch 'a'
		branch(branchesLocation, "a");

		// checkout 'a'
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation);
		Git git = new Git(db1);
		assertBranchExist(git, "a");
		checkoutBranch(cloneLocation, "a");

		// modify while on 'a'
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in a");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

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

		// modify a different file on master
		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "change in master");

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

		// merge: "git merge a"
		JSONObject merge = merge(gitHeadUri, "a", true);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.MERGED_SQUASHED, mergeResult);

		// assert clean
		//assertStatus(StatusResult.CLEAN, gitStatusUri);

		request = getGetFilesRequest(testTxt.getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("change in a", response.getText());

		request = getGetFilesRequest(folderTxt.getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("change in master", response.getText());

		// TODO: check commits, bug 340051
	}

	@Test
	public void testMergeSquashAlreadyUpToDate() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in master");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// "git add ."
		WebRequest request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setChanged(1), gitStatusUri);

		// "git merge master"
		JSONObject merge = merge(gitHeadUri, Constants.MASTER, true);
		MergeStatus mergeResult = MergeResult.MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeResult.MergeStatus.ALREADY_UP_TO_DATE, mergeResult);

		// status hasn't changed
		assertStatus(new StatusResult().setChanged(1), gitStatusUri);
	}

	@Test
	public void testMergeSquashConflict() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		// create branch 'a'
		branch(branchesLocation, "a");

		// checkout 'a'
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation);
		Git git = new Git(db1);
		assertBranchExist(git, "a");
		checkoutBranch(cloneLocation, "a");

		// modify while on 'a'
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in a");

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

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
		modifyFile(testTxt, "change in master");

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

		// merge: "git merge a"
		JSONObject merge = merge(gitHeadUri, "a", true);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.CONFLICTING, mergeResult);

		// check status
		assertStatus(new StatusResult().setConflictingNames("test.txt"), gitStatusUri);

		request = getGetFilesRequest(testTxt.getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String[] responseLines = response.getText().split("\n");
		assertEquals(5, responseLines.length);
		assertEquals("<<<<<<< HEAD", responseLines[0]);
		assertEquals("change in master", responseLines[1]);
		assertEquals("=======", responseLines[2]);
		assertEquals("change in a", responseLines[3]);
		// ignore the last line since it's different each time
		// assertEquals(">>>>>>> c5ddb0e22e7e829683bb3b336ca6cb24a1b5bb2e", responseLines[4]);

		// TODO: check commits, bug 340051
	}

	@Test
	public void testMergeSquashIntoLocalFailedDirtyWorkTree() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		// add a parallel commit in secondary clone and push it to the remote
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		IPath clonePath2 = new Path("file").append(project2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath2);

		// get project2 metadata
		request = getGetFilesRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);

		JSONObject testTxt = getChild(project2, "test.txt");
		modifyFile(testTxt, "change in secondary");
		addFile(testTxt);
		commitFile(testTxt, "commit on branch", false);

		ServerStatus pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// modify on master and try to merge
		testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "dirty");

		JSONObject masterDetails = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		String masterLocation = masterDetails.getString(ProtocolConstants.KEY_LOCATION);
		fetch(masterLocation);
		JSONObject merge = merge(gitHeadUri, Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, true);

		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAILED, mergeResult);
		JSONObject failingPaths = merge.getJSONObject(GitConstants.KEY_FAILING_PATHS);
		assertEquals(1, failingPaths.length());
		assertEquals(MergeFailureReason.DIRTY_WORKTREE, MergeFailureReason.valueOf(failingPaths.getString("test.txt")));
	}

	@Test
	public void testMergeSquashFailedDirtyWorkTree() throws Exception {
		// clone a repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);

		final String branch = "branch";

		branch(branchesLocation, branch);
		checkoutBranch(cloneLocation, branch);

		// create commit on branch
		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change in a");
		addFile(testTxt);
		commitFile(testTxt, "commit on branch", false);

		// assert clean
		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		assertStatus(StatusResult.CLEAN, gitStatusUri);

		// checkout 'master'
		checkoutBranch(cloneLocation, Constants.MASTER);

		// modify the same file on master
		modifyFile(testTxt, "change in master");

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		addFile(testTxt);
		commitFile(testTxt, "commit on master", false);

		// modify again
		modifyFile(testTxt, "change in the working dir");

		// assert clean
		assertStatus(new StatusResult().setModified(1), gitStatusUri);

		// merge: "git merge branch"
		JSONObject merge = merge(gitHeadUri, branch, true);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAILED, mergeResult);
		JSONObject failingPaths = merge.getJSONObject(GitConstants.KEY_FAILING_PATHS);
		assertEquals(1, failingPaths.length());
		assertEquals(MergeFailureReason.DIRTY_WORKTREE, MergeFailureReason.valueOf(failingPaths.getString("test.txt")));
	}

	@Test
	public void testMergeSquashRemote() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1
		JSONObject project1 = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		IPath clonePath1 = new Path("file").append(project1.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath1);

		// get project1 metadata
		WebRequest request = getGetFilesRequest(project1.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone2
		JSONObject project2 = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		IPath clonePath2 = new Path("file").append(project2.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath2);

		// get project2 metadata
		request = getGetFilesRequest(project2.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project2 = new JSONObject(response.getText());
		JSONObject gitSection2 = project2.getJSONObject(GitConstants.KEY_GIT);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
		String gitHeadUri2 = gitSection2.getString(GitConstants.KEY_HEAD);

		// clone1: get remote details
		JSONObject details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String refId1 = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation1 = details.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: change
		JSONObject testTxt = getChild(project2, "test.txt");
		modifyFile(testTxt, "incoming change");

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri2, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		ServerStatus pushStatus = push(gitRemoteUri2, 1, 0, Constants.MASTER, Constants.HEAD, false);
		assertEquals(true, pushStatus.isOK());

		// clone1: fetch
		request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation1, true, false);
		waitForTaskCompletion(webConversation.getResponse(request));

		// clone1: get remote details again
		JSONObject remoteBranch = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String newRefId1 = remoteBranch.getString(ProtocolConstants.KEY_ID);
		// an incoming commit
		assertFalse(refId1.equals(newRefId1));

		// clone1: merge into HEAD, "git merge origin/master"
		//String gitCommitUri = remoteBranch.getString(GitConstants.KEY_COMMIT);
		// TODO: should fail when POSTing to the above URI, see bug 342845

		String gitHeadUri = remoteBranch.getString(GitConstants.KEY_HEAD);

		// merge
		JSONObject merge = merge(gitHeadUri, newRefId1, true);
		MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
		assertEquals(MergeStatus.FAST_FORWARD_SQUASHED, mergeResult);

		request = getGetFilesRequest(getChild(project1, "test.txt").getString(ProtocolConstants.KEY_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("incoming change", response.getText());
	}

	@Test
	public void testMergeSquashRemovingFolders() throws Exception {
		// see org.eclipse.jgit.api.MergeCommandTest.testMergeRemovingFolders()
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetFilesRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());
			String folderChildrenLocation = folder.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
			String folderLocation = folder.getString(ProtocolConstants.KEY_LOCATION);
			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			String folderName = "folder1";
			request = getPostFilesRequest(folderLocation + "/", getNewDirJSON(folderName).toString(), folderName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			JSONObject folder1 = getChild(folder, "folder1");

			String fileName = "file1.txt";
			request = getPostFilesRequest(folder1.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			fileName = "file2.txt";
			request = getPostFilesRequest(folder1.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			folderName = "folder2";
			request = getPostFilesRequest(folderLocation + "/", getNewDirJSON(folderName).toString(), folderName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			JSONObject folder2 = getChild(folder, "folder2");

			fileName = "file1.txt";
			request = getPostFilesRequest(folder2.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			fileName = "file2.txt";
			request = getPostFilesRequest(folder2.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "folders and files", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			deleteFile(folder1);

			deleteFile(folder2);

			request = GitAddTest.getPutGitIndexRequest(gitIndexUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, "removing folders", false);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			JSONArray commitsArray = log(gitHeadUri);
			assertEquals(3, commitsArray.length());
			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals("removing folders", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			String toMerge = commit.getString(ProtocolConstants.KEY_NAME);
			commit = commitsArray.getJSONObject(1);
			assertEquals("folders and files", commit.get(GitConstants.KEY_COMMIT_MESSAGE));
			String toCheckout = commit.getString(ProtocolConstants.KEY_NAME);

			Repository db1 = getRepositoryForContentLocation(cloneContentLocation);
			Git git = new Git(db1);
			git.checkout().setName(toCheckout).call();

			JSONObject merge = merge(gitHeadUri, toMerge, true);
			MergeStatus mergeResult = MergeStatus.valueOf(merge.getString(GitConstants.KEY_RESULT));
			assertEquals(MergeStatus.FAST_FORWARD_SQUASHED, mergeResult);

			request = getGetFilesRequest(folderChildrenLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
			assertNull(getChildByName(children, "folder1"));
			assertNull(getChildByName(children, "folder2"));
		}
	}
}