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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
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
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		String contentLocation = clone(workspaceId, project).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		Repository repository = getRepositoryForContentLocation(contentLocation);
		assertNotNull(repository);
	}

	@Test
	public void testGetCloneEmpty() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONArray clonesArray = listClones(workspaceId, null);
		assertEquals(0, clonesArray.length());
	}

	@Test
	public void testGetClone() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		List<String> locations = new ArrayList<String>();

		// 1st clone
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project1"), null);
		String contentLocation = clone(workspaceId, project).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		locations.add(contentLocation);

		// 2nd clone
		project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project2"), null);
		contentLocation = clone(workspaceId, project).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		locations.add(contentLocation);

		// 3rd clone
		project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project3"), null);
		contentLocation = clone(workspaceId, project).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		locations.add(contentLocation);

		// get clones for workspace
		JSONArray clonesArray = listClones(workspaceId, null);
		assertEquals(locations.size(), clonesArray.length());
		for (int i = 0; i < clonesArray.length(); i++) {
			JSONObject clone = clonesArray.getJSONObject(i);
			assertNotNull(clone.get(ProtocolConstants.KEY_LOCATION));
			assertCloneUri(clone.getString(ProtocolConstants.KEY_LOCATION));
			assertNotNull(clone.get(ProtocolConstants.KEY_CONTENT_LOCATION));
			assertNotNull(clone.get(ProtocolConstants.KEY_NAME));
			assertNotNull(clone.get(GitConstants.KEY_URL));
			assertNotNull(clone.get(GitConstants.KEY_BRANCH));
			assertNotNull(clone.get(GitConstants.KEY_TAG));
			assertNotNull(clone.get(GitConstants.KEY_DIFF));
		}
	}

	@Test
	public void testCloneAndCreateProjectByName() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath clonePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		//AuthorizationService.removeUserRight(testUserId, "/");
		//AuthorizationService.removeUserRight(testUserId, "/*");

		// /workspace/{id} + {methodName}
		JSONObject clone = clone(clonePath, null, getMethodName());

		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetRequest(cloneContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(getMethodName(), project.getString(ProtocolConstants.KEY_NAME));
		assertGitSectionExists(project);
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testCloneIntoNewProjectWithDuplicateCloneName() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath clonePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		// /workspace/{id} + {methodName}
		String cloneName = getMethodName();
		JSONObject cloneOne = clone(clonePath, null, cloneName);
		assertEquals(cloneName, cloneOne.get("Name"));

		//now try to clone again with the same clone name
		URIish uri = new URIish(gitDir.toURI().toURL());
		WebRequest request = getPostGitCloneRequest(uri, clonePath, null, cloneName, null, null);
		WebResponse response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);
		assertTrue(status.toString(), status.isOK());
		String cloneLocation = status.getJsonData().getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(cloneLocation);

		// validate the clone metadata
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		assertTrue("Clone doesn't have children at " + cloneLocation, clones.has(ProtocolConstants.KEY_CHILDREN));
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length());
		JSONObject cloneTwo = clonesArray.getJSONObject(0);
		//the server should assign a different name to the second clone (slug is just a hint)
		String nameTwo = cloneTwo.getString("Name");
		assertNotNull(nameTwo);
		assertFalse(cloneName.equals(nameTwo));

	}

	@Test
	public void testCloneAndCreateFolderByPath() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = new Path("file").append(workspaceId).append(project.getString(ProtocolConstants.KEY_NAME)).append("clones").append("clone1").makeAbsolute();

		// /file/{id}/clones/clone1, folders: 'clones' and 'clone1' don't exist
		JSONObject clone = clone(clonePath);

		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetRequest(cloneContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject folder = new JSONObject(response.getText());
		assertGitSectionExists(folder);
	}

	@Test
	public void testCloneEmptyPath() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath clonePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		//AuthorizationService.removeUserRight(testUserId, "/");
		//AuthorizationService.removeUserRight(testUserId, "/*");

		// /workspace/{id}
		JSONObject clone = clone(clonePath, null, null);

		String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		WebRequest request = getGetRequest(cloneContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(gitDir.getName(), project.getString(ProtocolConstants.KEY_NAME));
		assertGitSectionExists(project);

		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testCloneEmptyPathBadUrl() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath workspacePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();

		AuthorizationService.removeUserRight(testUserId, "/");
		AuthorizationService.removeUserRight(testUserId, "/*");

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
		testUriCheck("I'm//bad!", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	@Test
	public void testCloneBadUrlScheme() throws Exception {
		testUriCheck("file:///path/to/other/users.git", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	@Test
	/**
	 * Test for a bad scp-like git repository URI. 
	 * @throws Exception
	 */
	public void testCloneBadUrlBadScpUri() throws Exception {
		testUriCheck("host.xz/path/to/repo.git", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	@Test
	/**
	 * Test for a local file path as the git repository URI.
	 * @throws Exception
	 */
	public void testCloneLocalFilePath() throws Exception {
		testUriCheck("c:/path/to/repo.git", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	@Test
	/**
	 * Test for a git repository URI with an empty scheme and host.
	 * @throws Exception
	 */
	public void testCloneEmptySchemeAndHost() throws Exception {
		testUriCheck(":path/to/repo.git/", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	@Test
	/**
	 * Test for a git repository URI with an empty scheme and path.
	 * @throws Exception
	 */
	public void testCloneEmptySchemeAndPath() throws Exception {
		testUriCheck("host.xz:", HttpURLConnection.HTTP_BAD_REQUEST);
	}

	@Test
	/**
	 * Test for a valid scp-like ssh URI.
	 * @throws Exception
	 */
	public void testCloneValidScpSshUri() throws Exception {
		testUriCheck("git@github.com:eclipse/orion.server.git", HttpURLConnection.HTTP_ACCEPTED);
	}

	@Test
	public void testCloneMissingUserInfo() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);

		// see bug 369282
		WebRequest request = new PostGitCloneRequest().setURIish("ssh://git.eclipse.org/gitroot/www.eclipse.org/orion.git").setFilePath(clonePath).getWebRequest();
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);
		assertFalse(status.toString(), status.isOK());

		assertEquals(status.toString(), HttpURLConnection.HTTP_BAD_REQUEST, status.getHttpCode());
	}

	@Test
	public void testCloneNotGitRepository() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = new Path("file").append(workspaceId).append(project.getString(ProtocolConstants.KEY_NAME)).makeAbsolute();

		// clone
		File notAGitRepository = createTempDir().toFile();
		assertTrue(notAGitRepository.isDirectory());
		assertTrue(notAGitRepository.list().length == 0);
		WebRequest request = getPostGitCloneRequest(notAGitRepository.toURI().toString(), clonePath);
		WebResponse response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);
		assertFalse(status.toString(), status.isOK());

		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, status.getHttpCode());
		assertEquals("Error cloning git repository", status.getMessage());
		assertNotNull(status.getJsonData());
		assertEquals(status.toString(), "Invalid remote: origin", status.getException().getMessage());

		// cleanup the tempDir
		FileUtils.delete(notAGitRepository, FileUtils.RECURSIVE);
	}

	@Test
	public void testCloneAndLink() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		String contentLocation = clone(workspaceId, project).getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		File contentFile = getRepositoryForContentLocation(contentLocation).getDirectory().getParentFile();

		JSONObject newProject = createProjectOrLink(workspaceLocation, getMethodName().concat("-link"), contentFile.toString());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// http://<host>/file/<projectId>/
		WebRequest request = getGetRequest(projectContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject link = new JSONObject(response.getText());
		String childrenLocation = link.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetRequest(childrenLocation);
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
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		String contentLocation = clone(workspaceId, project).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		File folder = new File(getRepositoryForContentLocation(contentLocation).getDirectory().getParentFile(), "folder");

		JSONObject newProject = createProjectOrLink(workspaceLocation, getMethodName().concat("-link"), folder.toURI().toString());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// http://<host>/file/<projectId>/
		WebRequest request = getGetRequest(projectContentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject link = new JSONObject(response.getText());
		String childrenLocation = link.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetRequest(childrenLocation);
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

			createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

			String contentLocation = new File(gitDir, "folder").getAbsolutePath();

			JSONObject newProject = createProjectOrLink(workspaceLocation, getMethodName().concat("-link"), contentLocation);
			String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// http://<host>/file/<projectId>/
			WebRequest request = getGetRequest(projectContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
			assertNotNull(childrenLocation);

			// http://<host>/file/<projectId>/?depth=1
			request = getGetRequest(childrenLocation);
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

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath workspacePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();
		URIish uri = new URIish(sshRepo);
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setWorkspacePath(workspacePath).setName(getMethodName()).getWebRequest();

		// cloning
		WebResponse response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);
		assertFalse(status.toString(), status.isOK());

		assertTrue(status.getMessage(), status.getMessage().startsWith("The authenticity of host "));
		assertTrue(status.getMessage(), status.getMessage().endsWith(" can't be established"));
		assertTrue(status.getJsonData().toString(), status.getJsonData().getString("DetailedMessage").startsWith("The authenticity of host "));
		assertTrue(status.getJsonData().toString(), status.getJsonData().getString("DetailedMessage").endsWith(" can't be established"));

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

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath workspacePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();
		URIish uri = new URIish(sshRepo);
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setWorkspacePath(workspacePath).setName(getMethodName()).setKnownHosts(knownHosts).getWebRequest();

		// cloning
		WebResponse response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);
		assertFalse(status.toString(), status.isOK());

		// task completed, but cloning failed
		assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, status.getHttpCode());
		assertEquals("Auth fail", status.getMessage());
		assertEquals("Auth fail", status.getJsonData().getString("DetailedMessage"));

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

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		URIish uri = new URIish(sshRepo);
		IPath workspacePath = new Path("workspace").append(getWorkspaceId(workspaceLocation)).makeAbsolute();
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setWorkspacePath(workspacePath).setKnownHosts(knownHosts).setPassword("I'm bad".toCharArray()).getWebRequest();

		// cloning
		WebResponse response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);
		assertFalse(status.toString(), status.isOK());

		// task completed, but cloning failed
		assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, status.getHttpCode());
		assertEquals("Auth fail", status.getMessage());
		assertEquals("Auth fail", status.getJsonData().getString("DetailedMessage"));

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

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		IPath clonePath = getClonePath(workspaceId, project);
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

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
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
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		/* assume there are no projects in the workspace */
		WebRequest request = getGetRequest(workspaceLocation.toString());
		WebResponse response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		JSONArray projects = workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		Assume.assumeTrue(projects.length() == 0);

		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);

		JSONObject clone = clone(workspaceId, project);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String projectLocation = project.getString(ProtocolConstants.KEY_LOCATION);

		// make sure there's one project in the workspace
		request = getGetRequest(workspaceLocation.toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		workspace = new JSONObject(response.getText());
		projects = workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, projects.length());

		// delete clone
		request = getDeleteCloneRequest(cloneLocation);
		response = webConversation.getResponse(request);
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
		request = getGetRequest(projectLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testDeleteInFolder() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);

		IPath clonePath = getClonePath(workspaceId, project).append("clone").makeAbsolute();
		JSONObject clone = clone(clonePath);
		String cloneLocation = clone.getString(ProtocolConstants.KEY_LOCATION);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		String projectLocation = project.getString(ProtocolConstants.KEY_LOCATION);

		// delete clone
		WebRequest request = getDeleteCloneRequest(cloneLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// the clone is gone
		request = getGetRequest(cloneLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		// so is the folder
		request = getGetRequest(contentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		// but the project is still there
		request = getGetRequest(projectLocation);
		response = webConversation.getResponse(request);

		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testDeleteInWorkspace() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		JSONObject clone = clone(workspaceId, project);
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
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		String contentLocation = clone(workspaceId, project).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		JSONArray clonesArray = listClones(workspaceId, null);
		assertEquals(1, clonesArray.length());

		Repository r = getRepositoryForContentLocation(contentLocation);

		// overwrite user settings, do not rebase when pulling, see bug 372489
		StoredConfig cfg = r.getConfig();
		cfg.setBoolean(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_REBASE, false);
		cfg.save();

		// TODO: replace with RESTful API when ready, see bug 339114
		Git git = new Git(r);
		PullResult pullResult = git.pull().call();
		assertEquals(pullResult.getMergeResult().getMergeStatus(), MergeStatus.ALREADY_UP_TO_DATE);
		assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
	}

	@Test
	public void testGetNonExistingClone() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONArray clonesArray = listClones(workspaceId, null);

		String dummyId = "dummyId";

		ensureCloneIdDoesntExist(clonesArray, dummyId);

		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + "/workspace/" + dummyId;
		WebRequest request = getGetRequest(requestURI);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + "/file/" + dummyId;
		request = getGetRequest(requestURI);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testGetEmptyPath() throws Exception {
		// see bug 369909
		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + "/";
		WebRequest request = getGetRequest(requestURI);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testGetOthersClones() throws Exception {
		// my clone
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		clone(workspaceId, project);

		JSONArray clonesArray = listClones(workspaceId, null);
		assertEquals(1, clonesArray.length());

		createUser("bob", "bob");
		// URI bobWorkspaceLocation = createWorkspace(getMethodName() + "bob");
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WebRequest request = new PostMethodWebRequest(SERVER_LOCATION + "/workspace");
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, workspaceName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request, "bob", "bob");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		URI bobWorkspaceLocation = SERVER_URI.resolve(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));
		String bobWorkspaceId = workspaceIdFromLocation(bobWorkspaceLocation);

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

		IPath bobClonePath = getClonePath(bobWorkspaceId, bobProject);

		// bob's clone
		URIish uri = new URIish(gitDir.toURI().toURL());
		request = getPostGitCloneRequest(uri, null, bobClonePath, null, null, null);
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response, "bob", "bob");
		String cloneLocation = status.getJsonData().getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(cloneLocation);

		// validate the clone metadata
		request = getGetRequest(cloneLocation);
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// list my clones again
		clonesArray = listClones(workspaceId, null);
		assertEquals(1, clonesArray.length()); // nothing has been added

		// try to get Bob's clone
		request = getGetRequest(cloneLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testCloneAlreadyExists() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		clone(workspaceId, project);

		// clone again into the same path
		IPath clonePath = getClonePath(workspaceId, project);
		WebRequest request = getPostGitCloneRequest(new URIish(gitDir.toURI().toURL()).toString(), clonePath);
		WebResponse response = webConversation.getResponse(request);
		ServerStatus status = waitForTask(response);

		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, status.getHttpCode());

		assertEquals("Error cloning git repository", status.getMessage());
		assertTrue(status.toString(), status.getException().getMessage().contains("not an empty directory"));

		// no project should be created
		request = new GetMethodWebRequest(workspaceLocation.toString());
		setAuthentication(request);
		response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		assertEquals(1, workspace.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
	}

	public void testUriCheck(String uri, int expectedResult) throws Exception {
		GitUtils._testAllowFileScheme(false);
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);

		WebRequest request = new PostGitCloneRequest().setURIish(uri).setFilePath(clonePath).getWebRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(expectedResult, response.getResponseCode());
	}

	/**
	 * Get list of clones for the given workspace or path. When <code>path</code> is
	 * not <code>null</code> the result is narrowed to clones under the <code>path</code>.
	 *
	 * @param workspaceId the workspace ID. Must be null if path is provided.
	 * @param path path under the workspace starting with project ID. Must be null if workspaceId is provided.
	 * @return the request
	 */
	static WebRequest listGitClonesRequest(String workspaceId, IPath path) {
		assertTrue(workspaceId == null && path != null || workspaceId != null && path == null);
		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + '/';
		if (workspaceId != null) {
			requestURI += "workspace/" + workspaceId;
		} else if (path != null) {
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
		WebRequest request = new DeleteMethodWebRequest(toAbsoluteURI(requestURI));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static void ensureCloneIdDoesntExist(JSONArray clonesArray, String id) throws JSONException {
		for (int i = 0; i < clonesArray.length(); i++) {
			JSONObject clone = clonesArray.getJSONObject(i);
			assertFalse(id.equals(clone.get(ProtocolConstants.KEY_ID)));
		}
	}
}
