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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitCloneTest extends GitTest {

	@Test
	public void testClone() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath);

		// Repository repository = getRepositoryForContentLocation(fileUri.toString());
		// assertNotNull(repository);
		File file = new File(contentLocation);
		assertTrue(file.exists());
		assertTrue(file.isDirectory());
		assertTrue(RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED));
	}

	@Test
	public void testGetCloneEmpty() throws IOException, SAXException, JSONException {
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
	public void testGetClone() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);

		List<String> locations = new ArrayList<String>();

		// 1st clone
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		String contentLocation = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		locations.add(contentLocation);

		// 2nd clone
		project = createProjectOrLink(workspaceLocation, getMethodName() + "2", null);
		contentLocation = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		locations.add(contentLocation);

		// 3rd clone
		project = createProjectOrLink(workspaceLocation, getMethodName() + "3", null);
		contentLocation = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
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
			assertNotNull(clone.get(ProtocolConstants.KEY_CONTENT_LOCATION));
			assertNotNull(clone.get(ProtocolConstants.KEY_ID));
			assertNotNull(clone.get(ProtocolConstants.KEY_NAME));
		}
	}

	@Test
	public void testCloneEmptyUrl() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		WebRequest request = getPostGitCloneRequest("", clonePath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCloneBadUrl() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName() + "1", null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		WebRequest request = getPostGitCloneRequest("I'm//bad!", clonePath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCloneNotGitRepository() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		// clone
		IPath randomLocation = getRandomLocation();
		assertNull(GitUtils.getGitDir(randomLocation.toFile()));
		WebRequest request = getPostGitCloneRequest(randomLocation.toString(), clonePath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
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
		assertEquals("An internal git error cloning git remote", result.getString("Message"));
		assertEquals("Invalid remote: origin", result.getString("DetailedMessage"));

		// we don't know ID of the clone that failed to be created, so we're checking if none has been added
		request = listGitClonesRequest(workspaceId, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(0, clonesArray.length());
	}

	@Test
	public void testCloneAndLink() throws IOException, SAXException, JSONException, URISyntaxException {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath);
		File contentFile = new File(contentLocation).getParentFile();

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
	public void testCloneAndLinkToFolder() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath);

		File folder = new File(new File(contentLocation).getParentFile(), "folder");

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
	public void testLinkToFolderWithDefaultSCM() throws IOException, SAXException, JSONException, URISyntaxException {
		URI workspaceLocation = createWorkspace(getMethodName());

		IPreferencesService preferences = GitActivator.getDefault().getPreferenceService();
		String scm = preferences.getString("org.eclipse.orion.server.configurator", "orion.project.defaultSCM", "", null).toLowerCase(); //$NON-NLS-1$ //$NON-NLS-2$
		Assume.assumeTrue("git".equals(scm)); //$NON-NLS-1$
		// FIXME: we never get here

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
	}

	@BeforeClass
	public static void prepareSsh() {
		readSshProperties();
	}

	@Test
	public void testCloneOverSshWithNoKnownHosts() throws IOException, SAXException, JSONException, URISyntaxException {
		Assume.assumeTrue(sshRepo != null);

		URIish uri = new URIish(sshRepo);
		IPath path = new Path(getMethodName());
		WebRequest request = getPostGitCloneRequest(uri.toString(), path, null, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
	}

	@Test
	public void testCloneOverSshWithNoPassword() throws IOException, SAXException, JSONException, URISyntaxException {
		Assume.assumeTrue(sshRepo != null);
		Assume.assumeTrue(knownHosts != null);

		URIish uri = new URIish(sshRepo);
		IPath path = new Path(getMethodName());
		WebRequest request = getPostGitCloneRequest(uri.toString(), path, knownHosts, (char[]) null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
	}

	@Test
	public void testCloneOverSshWithBadPassword() throws IOException, SAXException, JSONException, URISyntaxException {
		Assume.assumeTrue(sshRepo != null);
		Assume.assumeTrue(knownHosts != null);

		URIish uri = new URIish(sshRepo);
		IPath path = new Path(getMethodName());
		WebRequest request = getPostGitCloneRequest(uri.toString(), path, knownHosts, "I'm bad".toCharArray());
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
	}

	@Test
	public void testCloneOverSshWithPassword() throws IOException, SAXException, JSONException, URISyntaxException {
		Assume.assumeTrue(sshRepo != null);
		Assume.assumeTrue(password != null);
		Assume.assumeTrue(knownHosts != null);

		URIish uri = new URIish(sshRepo);
		String contentLocation = clone(uri, null, knownHosts, password);

		File file = URIUtil.toFile(new URI(contentLocation));
		assertTrue(file.exists());
		assertTrue(file.isDirectory());
		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED));
	}

	@Test
	public void testCloneOverSshWithPassphraseProtectedKey() throws IOException, SAXException, JSONException, URISyntaxException {
		Assume.assumeTrue(sshRepo2 != null);
		Assume.assumeTrue(privateKey != null);
		Assume.assumeTrue(passphrase != null);
		knownHosts = "github.com,207.97.227.239 ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAq2A7hRGmdnm9tUDbO9IDSwBK6TbQa+PXYPCPy6rbTrTtw7PHkccKrpp0yVhp5HdEIcKr6pLlVDBfOLX9QUsyCOV0wzfjIJNlGEYsdlLJizHhbn2mUjvSAHQqZETYP81eFzLQNnPHt4EVVUh7VfDESU84KezmD5QlWpXLmvU31/yMf+Se8xhHTvKSCZIFImWwoG6mbUoWf9nzpIoaSjB+weqqUUmpaaasXVal72J+UX2B+2RPW3RcT0eOzQgqlJL3RKrTJvdsjE3JEAvGq3lGHSZXy28G3skua2SmVi/w4yCE6gbODqnTWlg7+wC604ydGXA8VJiS5ap43JXiUFFAaQ==";

		URIish uri = new URIish(sshRepo2);
		String contentLocation = clone(uri, null, knownHosts, privateKey, publicKey, passphrase);

		File file = URIUtil.toFile(new URI(contentLocation));
		assertTrue(file.exists());
		assertTrue(file.isDirectory());
		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED));
	}

	@Test
	@Ignore("see bug 340553")
	public void testCloneAndDelete() throws IOException, SAXException, JSONException, URISyntaxException {
		String contentLocation = clone(null);

		File file = URIUtil.toFile(new URI(contentLocation));
		assertTrue(file.exists());
		assertTrue(file.isDirectory());
		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED));

		URI workspaceLocation = createWorkspace(getMethodName());

		// link
		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		// http://localhost:8080/workspace/{workspaceId}
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), getJsonAsStream(body.toString()), "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject newProject = new JSONObject(response.getText());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(projectContentLocation);

		// delete
		JSONObject data = new JSONObject();
		data.put("Remove", "true");
		data.put("ProjectURL", projectContentLocation);
		request = new PostMethodWebRequest(workspaceLocation.toString(), new ByteArrayInputStream(data.toString().getBytes()), "UTF8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertFalse(file.exists());
	}

	@Test
	public void testGetCloneAndPull() throws IOException, SAXException, JSONException, GitAPIException {
		// see bug 339254
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath);

		// get clones for workspace
		WebRequest request = listGitClonesRequest(workspaceId, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length());

		Git git = new Git(new FileRepository(new File(contentLocation)));
		// TODO: replace with RESTful API when ready, see bug 339114
		PullResult pullResult = git.pull().call();
		assertEquals(pullResult.getMergeResult().getMergeStatus(), MergeStatus.ALREADY_UP_TO_DATE);
		assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
	}

	@Test
	public void testGetNonexistingClone() throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());
		String workspaceId = getWorkspaceId(workspaceLocation);

		WebRequest request = listGitClonesRequest(workspaceId, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);

		String dummyId = "dummyId";

		ensureCloneIdDoesntExist(clonesArray, dummyId);

		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CLONE_RESOURCE + "/workspace/" + dummyId;
		request = getGetGitCloneRequest(requestURI);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CLONE_RESOURCE + "/file/" + dummyId;
		request = getGetGitCloneRequest(requestURI);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

	}

	@Test
	public void testGetOthersClones() throws IOException, SAXException, JSONException {
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
		JSONObject workspace = new JSONObject(response.getText());
		String bobWorkspaceId = workspace.getString(ProtocolConstants.KEY_ID);

		// JSONObject bobProject = createProjectOrLink(bobWorkspaceLocation, getMethodName() + "bob", null);
		JSONObject body = new JSONObject();
		request = new PostMethodWebRequest(bobWorkspaceLocation.toString(), getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, getMethodName() + "bob");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject bobProject = new JSONObject(response.getText());
		assertEquals(getMethodName() + "bob", bobProject.getString(ProtocolConstants.KEY_NAME));
		String bobProjectId = bobProject.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(bobProjectId);

		IPath bobClonePath = new Path(bobWorkspaceId).append(bobProjectId).makeAbsolute();

		// bob's clone
		URIish uri = new URIish(gitDir.toURL());
		request = getPostGitCloneRequest(uri.toString(), bobClonePath, null, null);
		setAuthentication(request, "bob", "bob");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// validate the clone metadata
		request = getGetGitCloneRequest(cloneLocation);
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
		request = getGetGitCloneRequest(cloneLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
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
		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CLONE_RESOURCE + '/';
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

	private WebRequest getPostGitCloneRequest(String uri, IPath path) throws JSONException, UnsupportedEncodingException {
		return getPostGitCloneRequest(uri, path, null, null);
	}

	private void ensureCloneIdDoesntExist(JSONArray clonesArray, String id) throws JSONException {
		for (int i = 0; i < clonesArray.length(); i++) {
			JSONObject clone = clonesArray.getJSONObject(i);
			assertFalse(id.equals(clone.get(ProtocolConstants.KEY_ID)));
		}
	}
}
