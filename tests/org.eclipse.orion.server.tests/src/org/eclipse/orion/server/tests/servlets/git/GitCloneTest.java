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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitCloneTest extends GitTest {

	@Test
	public void testClone() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		Repository repository = getRepositoryForContentLocation(contentLocation);
		assertNotNull(repository);
	}

	@Test
	public void testGetCloneEmpty() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);

		// get clones for workspace
		WebRequest request = listGitClonesRequest(workspaceId, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(0, clonesArray.length());
	}

	@Test
	public void testGetClone() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);

		List<String> locations = new ArrayList<String>();

		// 1st clone
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		String contentLocation = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute()).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		locations.add(contentLocation);

		// 2nd clone
		project = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		contentLocation = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute()).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		locations.add(contentLocation);

		// 3rd clone
		project = createProjectOrLink(workspaceLocation, getMethodName() + "3", null);
		contentLocation = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute()).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		locations.add(contentLocation);

		// get clones for workspace
		WebRequest request = listGitClonesRequest(workspaceId, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(locations.size(), clonesArray.length());
		for (int i = 0; i < clonesArray.length(); i++) {
			JSONObject clone = clonesArray.getJSONObject(i);
			assertNotNull(clone.get(ProtocolConstants.KEY_LOCATION));
			assertCloneUri(clone.getString(ProtocolConstants.KEY_LOCATION));
			assertNotNull(clone.get(ProtocolConstants.KEY_CONTENT_LOCATION));
			assertNotNull(clone.get(ProtocolConstants.KEY_ID));
			assertNotNull(clone.get(ProtocolConstants.KEY_NAME));
			assertNotNull(clone.get(GitConstants.KEY_URL));
			assertNotNull(clone.get(GitConstants.KEY_BRANCH));
			assertNotNull(clone.get(GitConstants.KEY_TAG));
			assertNotNull(clone.get(GitConstants.KEY_DIFF));
		}
	}

	@Test
	public void testCloneAndCreateProjectByName() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		IPath clonePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		AuthorizationService.removeUserRight("test", "/");
		AuthorizationService.removeUserRight("test", "/*");

		// /workspace/{id} + {methodName}
		JSONObject clone = clone(clonePath, null, getMethodName());

		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetFilesRequest(cloneContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(getMethodName(), project.getString(ProtocolConstants.KEY_NAME));
		assertGitSectionExists(project);
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetFilesRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testCloneAndCreateFolderByPath() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).append("clones").append("clone1").makeAbsolute();

		// /file/{id}/clones/clone1, folders: 'clones' and 'clone1' don't exist
		JSONObject clone = clone(clonePath);

		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetFilesRequest(cloneContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject folder = new JSONObject(response.getText());
		assertGitSectionExists(folder);
	}

	@Test
	public void testCloneEmptyPath() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		IPath clonePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		AuthorizationService.removeUserRight("test", "/");
		AuthorizationService.removeUserRight("test", "/*");

		// /workspace/{id}
		JSONObject clone = clone(clonePath, null, null);

		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetFilesRequest(cloneContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(gitDir.getName(), project.getString(ProtocolConstants.KEY_NAME));
		assertGitSectionExists(project);

		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetFilesRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

	}

	@Test
	public void testCloneEmptyPathBadUrl() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		IPath workspacePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		AuthorizationService.removeUserRight("test", "/");
		AuthorizationService.removeUserRight("test", "/*");

		// /workspace/{id} + {methodName}
		WebRequest request = new PostGitCloneRequest().setURIish("I'm//bad!").setWorkspacePath(workspacePath).setName(getMethodName()).getWebRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

		// no project should be created
		request = new GetMethodWebRequest(workspaceLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertEquals(0, workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
	}

	@Test
	public void testCloneBadUrl() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		WebRequest request = new PostGitCloneRequest().setURIish("I'm//bad!").setFilePath(clonePath).getWebRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCloneNotGitRepository() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		// clone
		IPath randomLocation = getRandomLocation();
		assertNull(GitUtils.getGitDir(randomLocation.toFile()));
		WebRequest request = getPostGitCloneRequest(randomLocation.toString(), clonePath);
		WebResponse response = waitForTaskCompletionObjectResponse(webConversation.getResponse(request));

		// task completed, but cloning failed
		if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
			JSONObject completedTask = new JSONObject(response.getText());
			assertEquals(false, completedTask.getBoolean("Running"));
			assertEquals(100, completedTask.getInt("PercentComplete"));
			JSONObject result = completedTask.getJSONObject("Result");
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, result.getInt("HttpCode"));
			assertEquals("Error", result.getString("Severity"));
			assertEquals("An internal git error cloning git repository", result.getString("Message"));
			assertEquals("Invalid remote: origin", result.getString("DetailedMessage"));
		} else {
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
			JSONObject result = new JSONObject(response.getText());
			assertEquals("An internal git error cloning git repository", result.getString("Message"));
			assertEquals("Invalid remote: origin", result.getString("DetailedMessage"));
		}

		// we don't know ID of the clone that failed to be created, so we're checking if none has been added
		request = listGitClonesRequest(workspaceId, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(0, clonesArray.length());
	}

	@Test
	public void testCloneAndLink() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		File contentFile = getRepositoryForContentLocation(contentLocation).getDirectory().getParentFile();

		JSONObject newProject = createProjectOrLink(workspaceLocation, getMethodName() + "-link", contentFile.toString());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// http://<host>/file/<projectId>/
		WebRequest request = getGetFilesRequest(projectContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject link = new JSONObject(response.getText());
		String childrenLocation = link.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetFilesRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
		String[] expectedChildren = new String[] {Constants.DOT_GIT, "folder", "test.txt"};
		assertEquals("Wrong number of directory children", expectedChildren.length, children.size());
		assertNotNull(getChildByName(children, expectedChildren[0]));
		assertNotNull(getChildByName(children, expectedChildren[1]));
		assertNotNull(getChildByName(children, expectedChildren[2]));
	}

	@Test
	public void testCloneAndLinkToFolder() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		File folder = new File(getRepositoryForContentLocation(contentLocation).getDirectory().getParentFile(), "folder");

		JSONObject newProject = createProjectOrLink(workspaceLocation, getMethodName() + "-link", folder.toURI().toString());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// http://<host>/file/<projectId>/
		WebRequest request = getGetFilesRequest(projectContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject link = new JSONObject(response.getText());
		String childrenLocation = link.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetFilesRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
		String[] expectedChildren = new String[] {"folder.txt"};
		assertEquals("Wrong number of directory children", expectedChildren.length, children.size());
		assertEquals(expectedChildren[0], children.get(0).getString(ProtocolConstants.KEY_NAME));
	}

	@Test
	public void testLinkToFolderWithDefaultSCM() throws Exception {
		// enable git autoinit for new projects
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(ServerConstants.PREFERENCE_SCOPE);
		String oldValue = prefs.get(ServerConstants.CONFIG_FILE_DEFAULT_SCM, null);
		prefs.put(ServerConstants.CONFIG_FILE_DEFAULT_SCM, "git");
		prefs.flush();

		try {
			// the same check as in org.eclipse.orion.server.git.GitFileDecorator.initGitRepository(HttpServletRequest, IPath, JSONObject)
			String scm = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_DEFAULT_SCM, "");
			Assume.assumeTrue("git".equals(scm)); //$NON-NLS-1$

			URI workspaceLocation = createWorkspace(getMethodName());

			String contentLocation = new File(gitDir, "folder").getAbsolutePath();

			JSONObject newProject = createProjectOrLink(workspaceLocation, getMethodName() + "-link", contentLocation);
			String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// http://<host>/file/<projectId>/
			WebRequest request = getGetFilesRequest(projectContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
			assertNotNull(childrenLocation);

			// http://<host>/file/<projectId>/?depth=1
			request = getGetFilesRequest(childrenLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
			String[] expectedChildren = new String[] {"folder.txt"}; // no .git even though auto-git is on
			assertEquals("Wrong number of directory children", expectedChildren.length, children.size());
			assertEquals(expectedChildren[0], children.get(0).getString(ProtocolConstants.KEY_NAME));
		} finally {
			// reset the preference we messed with for the test
			if (oldValue == null)
				prefs.remove(ServerConstants.CONFIG_FILE_DEFAULT_SCM);
			else
				prefs.put(ServerConstants.CONFIG_FILE_DEFAULT_SCM, oldValue);
			prefs.flush();
		}
	}

	@BeforeClass
	public static void prepareSsh() {
		readSshProperties();
	}

	@Test
	public void testCloneOverSshWithNoKnownHosts() throws Exception {
		Assume.assumeTrue(sshRepo != null);

		URI workspaceLocation = createWorkspace(getMethodName());
		IPath workspacePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();
		URIish uri = new URIish(sshRepo);
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setWorkspacePath(workspacePath).setName(getMethodName()).getWebRequest();

		// cloning
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// task completed, but cloning failed
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject completedTask = new JSONObject(response.getText());
		assertEquals(false, completedTask.getBoolean("Running"));
		assertEquals(100, completedTask.getInt("PercentComplete"));
		JSONObject result = completedTask.getJSONObject("Result");
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getInt("HttpCode"));
		assertEquals("Error", result.getString("Severity"));
		assertRepositoryInfo(uri, result);
		assertTrue(result.getString("Message").startsWith("The authenticity of host "));
		assertTrue(result.getString("Message").endsWith(" can't be established"));
		assertTrue(result.getString("DetailedMessage").startsWith("The authenticity of host "));
		assertTrue(result.getString("DetailedMessage").endsWith(" can't be established"));

		// no project should be created
		request = new GetMethodWebRequest(workspaceLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertEquals(0, workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());

	}

	@Test
	public void testCloneOverSshWithNoPassword() throws Exception {
		Assume.assumeTrue(sshRepo != null);
		Assume.assumeTrue(knownHosts != null);

		URI workspaceLocation = createWorkspace(getMethodName());
		IPath workspacePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();
		URIish uri = new URIish(sshRepo);
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setWorkspacePath(workspacePath).setName(getMethodName()).setKnownHosts(knownHosts).getWebRequest();

		// cloning
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// task completed, but cloning failed
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject completedTask = new JSONObject(response.getText());
		assertEquals(false, completedTask.getBoolean("Running"));
		assertEquals(100, completedTask.getInt("PercentComplete"));
		JSONObject result = completedTask.getJSONObject("Result");
		assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, result.getInt("HttpCode"));
		assertRepositoryInfo(uri, result);
		assertEquals("Error", result.getString("Severity"));
		assertEquals("Auth fail", result.getString("Message"));
		assertEquals("Auth fail", result.getString("DetailedMessage"));

		// no project should be created
		request = new GetMethodWebRequest(workspaceLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertEquals(0, workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
	}

	@Test
	public void testCloneOverSshWithBadPassword() throws Exception {
		Assume.assumeTrue(sshRepo != null);
		Assume.assumeTrue(knownHosts != null);

		URI workspaceLocation = createWorkspace(getMethodName());
		URIish uri = new URIish(sshRepo);
		IPath workspacePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setWorkspacePath(workspacePath).setKnownHosts(knownHosts).setPassword("I'm bad".toCharArray()).getWebRequest();

		// cloning
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// task completed, but cloning failed
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject completedTask = new JSONObject(response.getText());
		assertEquals(false, completedTask.getBoolean("Running"));
		assertEquals(100, completedTask.getInt("PercentComplete"));
		JSONObject result = completedTask.getJSONObject("Result");
		assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, result.getInt("HttpCode"));
		assertRepositoryInfo(uri, result);
		assertEquals("Error", result.getString("Severity"));
		assertEquals("Auth fail", result.getString("Message"));
		assertEquals("Auth fail", result.getString("DetailedMessage"));

		// no project should be created
		request = new GetMethodWebRequest(workspaceLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertEquals(0, workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
	}

	@Test
	public void testCloneOverSshWithPassword() throws Exception {
		Assume.assumeTrue(sshRepo != null);
		Assume.assumeTrue(password != null);
		Assume.assumeTrue(knownHosts != null);

		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		URIish uri = new URIish(sshRepo);
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setFilePath(clonePath).setKnownHosts(knownHosts).setPassword(password).getWebRequest();
		String contentLocation = clone(request);

		File file = getRepositoryForContentLocation(contentLocation).getDirectory().getParentFile();
		assertTrue(file.exists());
		assertTrue(file.isDirectory());
		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED));
	}

	@Test
	public void testCloneOverSshWithPassphraseProtectedKey() throws Exception {
		Assume.assumeTrue(sshRepo2 != null);
		Assume.assumeTrue(privateKey != null);
		Assume.assumeTrue(passphrase != null);

		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		URIish uri = new URIish(sshRepo2);
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setFilePath(clonePath).setKnownHosts(knownHosts2).setPrivateKey(privateKey).setPublicKey(publicKey).setPassphrase(passphrase).getWebRequest();
		String contentLocation = clone(request);

		File file = getRepositoryForContentLocation(contentLocation).getDirectory().getParentFile();
		assertTrue(file.exists());
		assertTrue(file.isDirectory());
		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED));
	}

	@Test
	public void testDeleteInProject() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// delete clone
		WebRequest request = getDeleteCloneRequest(cloneLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// the clone is gone
		request = getGetRequest(cloneLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		// so it's the folder (top-level)
		request = getGetRequest(contentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		// make sure the project doesn't exist
		request = getGetRequest(workspaceLocation.toString());
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		JSONArray projects = workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		for (int i = 0; i < projects.length(); i++) {
			JSONObject p = projects.getJSONObject(i);
			assertFalse(projectId.equals(p.getString(ProtocolConstants.KEY_ID)));
		}
	}

	@Test
	public void testDeleteInFolder() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).append("clone").makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// delete clone
		WebRequest request = getDeleteCloneRequest(cloneLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// the clone is gone
		request = getGetRequest(cloneLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		// so it's the folder
		request = getGetRequest(contentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		// but the project is still there
		request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_ID));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testDeleteInWorkspace() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		Repository repository = getRepositoryForContentLocation(contentLocation);
		assertNotNull(repository);

		// delete folder with cloned repository in it
		WebRequest request = getDeleteFilesRequest(contentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// the clone is gone
		request = getGetRequest(cloneLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		assertFalse(repository.getDirectory().exists());
	}

	@Test
	public void testGetCloneAndPull() throws Exception {
		// see bug 339254
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// get clones for workspace
		WebRequest request = listGitClonesRequest(workspaceId, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length());

		Git git = new Git(getRepositoryForContentLocation(contentLocation));
		// TODO: replace with RESTful API when ready, see bug 339114
		PullResult pullResult = git.pull().call();
		assertEquals(pullResult.getMergeResult().getMergeStatus(), MergeStatus.ALREADY_UP_TO_DATE);
		assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
	}

	@Test
	public void testGetNonexistingClone() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);

		WebRequest request = listGitClonesRequest(workspaceId, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);

		String dummyId = "dummyId";

		ensureCloneIdDoesntExist(clonesArray, dummyId);

		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + "/workspace/" + dummyId;
		request = getGetRequest(requestURI);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + "/file/" + dummyId;
		request = getGetRequest(requestURI);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

	}

	@Test
	public void testGetOthersClones() throws Exception {
		// my clone
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		WebRequest request = listGitClonesRequest(workspaceId, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length());

		createUser("bob", "bob");
		// URI bobWorkspaceLocation = createWorkspace(getMethodName() + "bob");
		String workspaceName = getClass().getName() + "#" + getMethodName() + "bob";
		request = new PostMethodWebRequest(SERVER_LOCATION + "/workspace");
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, workspaceName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		URI bobWorkspaceLocation = URI.create(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));

		// String bobWorkspaceId = getWorkspaceId(bobWorkspaceLocation);
		request = new GetMethodWebRequest(bobWorkspaceLocation.toString());
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);

		// JSONObject bobProject = createProjectOrLink(bobWorkspaceLocation, getMethodName() + "bob", null);
		JSONObject body = new JSONObject();
		request = new PostMethodWebRequest(bobWorkspaceLocation.toString(), IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, getMethodName() + "bob");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject bobProject = new JSONObject(response.getText());
		assertEquals(getMethodName() + "bob", bobProject.getString(ProtocolConstants.KEY_NAME));
		String bobProjectId = bobProject.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(bobProjectId);

		IPath bobClonePath = new Path("file").append(bobProjectId).makeAbsolute();

		// bob's clone
		URIish uri = new URIish(gitDir.toURI().toURL());
		request = getPostGitCloneRequest(uri, null, bobClonePath, null, null, null);
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation, "bob", "bob");

		// validate the clone metadata
		request = getGetRequest(cloneLocation);
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// list my clones again
		request = listGitClonesRequest(workspaceId, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		clones = new JSONObject(response.getText());
		clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length()); // nothing has been added

		// try to get Bob's clone
		request = getGetRequest(cloneLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testCloneAlreadyExists() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// clone again into the same path
		WebRequest request = getPostGitCloneRequest(new URIish(gitDir.toURI().toURL()).toString(), clonePath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String completedTaskLocation = waitForTaskCompletion(taskLocation);

		// task completed, but cloning failed
		request = new GetMethodWebRequest(completedTaskLocation);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject completedTask = new JSONObject(response.getText());
		assertEquals(false, completedTask.getBoolean("Running"));
		assertEquals(100, completedTask.getInt("PercentComplete"));
		JSONObject result = completedTask.getJSONObject("Result");
		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, result.getInt("HttpCode"));
		assertEquals("Error", result.getString("Severity"));
		assertEquals("An internal git error cloning git repository", result.getString("Message"));
		assertTrue(result.getString("DetailedMessage").contains("not an empty directory"));

		// no project should be created
		request = new GetMethodWebRequest(workspaceLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertEquals(1, workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
	}

	/**
	 * Get list of clones for the given workspace or path. When <code>path</code> is
	 * not <code>null</code> the result is narrowed to clones under the <code>path</code>.
	 * 
	 * @param workspaceId the workspace ID. Must be null if path is provided.
	 * @param path path under the workspace starting with project ID. Must be null if workspaceId is provided.
	 * @return the request
	 */
	private WebRequest listGitClonesRequest(String workspaceId, IPath path) {
		assertTrue(workspaceId == null && path != null || workspaceId != null && path == null);
		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + '/';
		if (workspaceId != null) {
			requestURI += "workspace/" + workspaceId;
		} else {
			requestURI += "path" + (path.isAbsolute() ? path : path.makeAbsolute());
		}
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	// Convenience methods for creating requests

	private WebRequest getPostGitCloneRequest(String uri, IPath path) throws JSONException, UnsupportedEncodingException, URISyntaxException {
		return getPostGitCloneRequest(new URIish(uri), null, path, null, null, null);
	}

	private WebRequest getDeleteCloneRequest(String requestURI) throws CoreException, IOException {
		assertCloneUri(requestURI);
		WebRequest request = new DeleteMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private void ensureCloneIdDoesntExist(JSONArray clonesArray, String id) throws JSONException {
		for (int i = 0; i < clonesArray.length(); i++) {
			JSONObject clone = clonesArray.getJSONObject(i);
			assertFalse(id.equals(clone.get(ProtocolConstants.KEY_ID)));
		}
	}
}
