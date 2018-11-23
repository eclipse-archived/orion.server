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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitCheckoutTest extends GitTest {

	// modified + checkout = clean
	@Test
	public void testCheckoutAllPaths() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folder1Txt = getChild(folder1, "folder.txt");
		modifyFile(folder1Txt, "change");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri, new String[] {"test.txt", "folder/folder.txt"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(StatusResult.CLEAN, gitStatusUri);
	}

	// modified + checkout = clean
	@Test
	@Ignore("not supported yet")
	public void testCheckoutDotPath() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "change");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri, new String[] {"."});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(StatusResult.CLEAN, gitStatusUri);
	}

	// modified + checkout = clean
	@Test
	public void testCheckoutFolderPath() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "change");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri, new String[] {"folder"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// 'test.txt' is still modified
		assertStatus(new StatusResult().setModifiedNames("test.txt"), gitStatusUri);
	}

	@Test
	public void testCheckoutEmptyPaths() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitCloneUri = gitSection.getString(GitConstants.KEY_CLONE);

		request = getCheckoutRequest(gitCloneUri, new String[] {});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCheckoutPathInUri() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = getCloneUri(gitStatusUri);

		// TODO: don't create URIs out of thin air
		request = getCheckoutRequest(gitCloneUri + "test.txt", new String[] {});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCheckoutWrongPath() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = getCloneUri(gitStatusUri);

		// 'notthere.txt' doesn't exist
		request = getCheckoutRequest(gitCloneUri, new String[] {"notthere.txt"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// nothing has changed
		assertStatus(new StatusResult().setModifiedNames("test.txt"), gitStatusUri);
	}

	@Test
	public void testCheckoutUntrackedFile() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		String fileName = "new.txt";
		request = getPostFilesRequest(project.getString(ProtocolConstants.KEY_LOCATION), getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		String gitCloneUri = getCloneUri(gitStatusUri);

		// checkout the new file
		request = getCheckoutRequest(gitCloneUri, new String[] {fileName});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// nothing has changed, checkout doesn't touch untracked files
		assertStatus(new StatusResult().setUntrackedNames(fileName), gitStatusUri);

		// discard the new file
		request = getCheckoutRequest(gitCloneUri, new String[] {fileName}, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(StatusResult.CLEAN, gitStatusUri);
	}

	@Test
	public void testCheckoutAfterResetByPath() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
			String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
			String gitCloneUri = getCloneUri(gitStatusUri);

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "change");

			assertStatus(new StatusResult().setModified(1), gitStatusUri);

			addFile(testTxt);

			assertStatus(new StatusResult().setChanged(1), gitStatusUri);

			// unstage
			request = GitResetTest.getPostGitIndexRequest(gitIndexUri, new String[] {"test.txt"}, null, (String) null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status again
			assertStatus(new StatusResult().setModified(1), gitStatusUri);

			// checkout
			request = getCheckoutRequest(gitCloneUri, new String[] {"test.txt"});
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// check status one more time
			assertStatus(StatusResult.CLEAN, gitStatusUri);
		}
	}

	// modified + checkout = clean
	@Test
	public void testCheckoutInFolder() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		JSONObject testTxt = getChild(project, "test.txt");
		modifyFile(testTxt, "change");

		JSONObject folder1 = getChild(project, "folder");
		JSONObject folderTxt = getChild(folder1, "folder.txt");
		modifyFile(folderTxt, "change");

		JSONObject gitSection = folder1.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		// we should get a proper clone URI here: /git/clone/file/{projectId}/
		String gitCloneUri = getCloneUri(gitStatusUri);

		request = getCheckoutRequest(gitCloneUri, new String[] {"folder/folder.txt"});
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// 'test.txt' is still modified
		assertStatus(new StatusResult().setModifiedNames("test.txt"), gitStatusUri);
	}

	@Test
	public void testCheckoutFileOutsideCurrentFolder() throws Exception {
		// see bug 347847
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
			modifyFile(testTxt, "change in file.txt");

			JSONObject folder1 = getChild(folder, "folder");
			JSONObject folderTxt = getChild(folder1, "folder.txt");
			modifyFile(folderTxt, "change folder/folder.txt");

			// check status
			JSONObject folder1GitSection = folder1.getJSONObject(GitConstants.KEY_GIT);
			String folder1GitStatusUri = folder1GitSection.getString(GitConstants.KEY_STATUS);
			String folder1GitCloneUri = getCloneUri(folder1GitStatusUri);

			request = getGetGitStatusRequest(folder1GitStatusUri);
			assertStatus(new StatusResult().setModifiedNames("folder/folder.txt", "test.txt").setModifiedPaths("folder.txt", "../test.txt"), folder1GitStatusUri);

			// use KEY_NAME not KEY_PATH
			// request = getCheckoutRequest(gitCloneUri, new String[] {testTxt.getString(ProtocolConstants.KEY_PATH)});
			request = getCheckoutRequest(folder1GitCloneUri, new String[] {testTxt.getString(ProtocolConstants.KEY_NAME)});
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// 'folder/folder.txt' is still modified
			assertStatus(new StatusResult().setModifiedNames("folder/folder.txt"), folder1GitStatusUri);
		}
	}

	@Test
	public void testCheckoutBranch() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		JSONObject clone = clone(clonePath);
		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// create branch 'a'
		branch(branchesLocation, "a");

		// checkout 'a'
		response = checkoutBranch(cloneLocation, "a");
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		Repository db1 = getRepositoryForContentLocation(cloneContentLocation);
		Git git = new Git(db1);
		GitRemoteTest.assertOnBranch(git, "a");
	}

	@Test
	public void testCheckoutEmptyBranchName() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		JSONObject clone = clone(clonePath);
		String location = clone.getString(ProtocolConstants.KEY_LOCATION);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// checkout
		response = checkoutBranch(location, "");
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testCheckoutInvalidBranchName() throws Exception {
		// clone a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		JSONObject clone = clone(clonePath);
		String location = clone.getString(ProtocolConstants.KEY_LOCATION);

		// get project metadata
		WebRequest request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());

		// checkout 'a', which hasn't been created
		response = checkoutBranch(location, "a");
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testCheckoutAborted() throws Exception {
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

			// create branch
			clone = getCloneForGitResource(testTxt);
			response = branch(clone.getString(GitConstants.KEY_BRANCH), "branch");

			// change on the branch
			modifyFile(testTxt, "master change");

			addFile(testTxt);

			commitFile(testTxt, "commit on master", false);

			// local change, not committed
			modifyFile(testTxt, "working tree change");

			// checkout
			response = checkoutBranch(clone.getString(ProtocolConstants.KEY_LOCATION), "branch");
			assertEquals(HttpURLConnection.HTTP_CONFLICT, response.getResponseCode());
			JSONObject result = new JSONObject(response.getText());
			assertEquals(HttpURLConnection.HTTP_CONFLICT, result.getInt("HttpCode"));
			assertEquals("Error", result.getString("Severity"));
			assertEquals("Checkout aborted.", result.getString("Message"));
			assertEquals("Checkout conflict with files: \ntest.txt", result.getString("DetailedMessage"));
		}
	}

	@Test
	public void testCheckoutBranchFromSecondaryRemote() throws Exception {

		// dummy commit to start off a new branch on origin
		createFile(testFile.toURI(), "origin-test");
		Git git = new Git(db);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("test commit").call();
		git.branchCreate().setName("test").setStartPoint(Constants.HEAD).call();

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);
			String remotesLocation = clone.getString(GitConstants.KEY_REMOTE);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// expect only origin
			getRemote(remotesLocation, 1, 0, Constants.DEFAULT_REMOTE_NAME);

			// create secondary repository
			IPath randomLocation = createTempDir();
			randomLocation = randomLocation.addTrailingSeparator().append(Constants.DOT_GIT);
			File dotGitDir = randomLocation.toFile().getCanonicalFile();
			Repository db2 = FileRepositoryBuilder.create(dotGitDir);
			toClose.add(db2);
			assertFalse(dotGitDir.exists());
			db2.create(false /* non bare */);

			Git git2 = new Git(db2);
			// dummy commit to start off new branch
			File branchFile = new File(dotGitDir.getParentFile(), "branch.txt");
			branchFile.createNewFile();
			createFile(branchFile.toURI(), "secondary-branch");
			git2.add().addFilepattern(".").call();
			git2.commit().setMessage("branch commit").call();
			git2.branchCreate().setName("branch").setStartPoint(Constants.HEAD).call();

			// create remote
			response = addRemote(remotesLocation, "secondary", dotGitDir.getParentFile().toURI().toURL().toString());
			String secondaryRemoteLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
			assertNotNull(secondaryRemoteLocation);

			// list remotes
			request = getGetRequest(remotesLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject remotes = new JSONObject(response.getText());
			JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			// expect origin and new remote
			assertEquals(2, remotesArray.length());

			// fetch both remotes
			fetch(remotesArray.getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION));
			fetch(remotesArray.getJSONObject(1).getString(ProtocolConstants.KEY_LOCATION));

			// secondary
			request = getGetRequest(remotesArray.getJSONObject(1).getString(ProtocolConstants.KEY_LOCATION));
			response = webConversation.getResponse(request);
			ServerStatus status = waitForTask(response);
			assertTrue(status.toString(), status.isOK());
			JSONObject remote = status.getJsonData();

			// checkout remote branch: secondary/branch
			String remoteBranchName = remote.getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_NAME);
			if (!remoteBranchName.equals("secondary/branch"))
				remoteBranchName = remote.getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(1).getString(ProtocolConstants.KEY_NAME);
			assertEquals("secondary/branch", remoteBranchName);
			response = branch(branchesLocation, "branch", remoteBranchName);
			JSONObject branch = new JSONObject(response.getText());
			JSONArray remoteBranchLocations = branch.getJSONArray(GitConstants.KEY_REMOTE);
			assertEquals(1, remoteBranchLocations.length());
			assertEquals("secondary", remoteBranchLocations.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
			assertEquals("secondary/branch", remoteBranchLocations.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_NAME));

			// origin
			request = getGetRequest(remotesArray.getJSONObject(0).getString(ProtocolConstants.KEY_LOCATION));
			response = webConversation.getResponse(request);
			status = waitForTask(response);
			assertTrue(status.toString(), status.isOK());
			remote = status.getJsonData();

			// checkout remote branch: origin/test
			JSONArray remoteChildren = remote.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(2, remoteChildren.length());
			remoteBranchName = remoteChildren.getJSONObject(0).getString(ProtocolConstants.KEY_NAME);
			if (!remoteBranchName.equals("origin/test"))
				remoteBranchName = remoteChildren.getJSONObject(1).getString(ProtocolConstants.KEY_NAME);
			assertEquals("origin/test", remoteBranchName);
			response = branch(branchesLocation, "test", remoteBranchName);
			branch = new JSONObject(response.getText());
			remoteBranchLocations = branch.getJSONArray(GitConstants.KEY_REMOTE);
			assertEquals(1, remoteBranchLocations.length());
			assertEquals("origin", remoteBranchLocations.getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
			assertEquals("origin/test", remoteBranchLocations.getJSONObject(0).getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0).getString(ProtocolConstants.KEY_NAME));
		}
	}

	private WebRequest getCheckoutRequest(String location, String[] paths) throws IOException, JSONException {
		return getCheckoutRequest(location, paths, false);
	}

	private WebRequest getCheckoutRequest(String location, String[] paths, boolean removeUntracked) throws IOException, JSONException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		JSONArray jsonPaths = new JSONArray();
		for (String path : paths)
			jsonPaths.put(path);
		body.put(ProtocolConstants.KEY_PATH, jsonPaths);
		if (removeUntracked)
			body.put(GitConstants.KEY_REMOVE_UNTRACKED, removeUntracked);
		WebRequest request = new PutMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
