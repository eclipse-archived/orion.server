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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public abstract class GitTest extends FileSystemTest {

	protected static final String GIT_SERVLET_LOCATION = GitServlet.GIT_URI + '/';

	static WebConversation webConversation;
	File gitDir;
	File testFile;
	protected FileRepository db;

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	@Before
	public void setUp() throws CoreException, IOException, GitAPIException {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		createRepository();
		ServletTestingSupport.allowedPrefixes = gitDir.toString();
	}

	@After
	public void tearDown() throws IOException {
		db.close();
		FileUtils.delete(gitDir, FileUtils.RECURSIVE);
	}

	protected JSONObject createProjectWithContentLocation(URI workspaceLocation, String projectName, String location) throws JSONException, IOException, SAXException {
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, location);
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	protected URI createWorkspace(String suffix) throws IOException, SAXException {
		String workspaceName = getClass().getName() + "#" + suffix;
		WebRequest request = getCreateWorkspaceRequest(workspaceName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return URI.create(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));
	}

	protected void createRepository() throws IOException, GitAPIException, CoreException {
		IPath randomLocation = getRandomLocation();
		gitDir = randomLocation.toFile();
		randomLocation = randomLocation.addTrailingSeparator().append(Constants.DOT_GIT);
		File dotGitDir = randomLocation.toFile().getCanonicalFile();
		db = new FileRepository(dotGitDir);
		assertFalse(dotGitDir.exists());
		db.create(false /* non bare */);

		testFile = new File(gitDir, "test.txt");
		testFile.createNewFile();
		createFile(testFile.toURI(), "test");
		File folder = new File(gitDir, "folder");
		folder.mkdir();
		File folderFile = new File(folder, "folder.txt");
		folderFile.createNewFile();
		createFile(folderFile.toURI(), "folder");

		Git git = new Git(db);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Initial commit").call();
	}

	protected IPath getRandomLocation() {
		return FileSystemHelper.getRandomLocation(FileSystemHelper.getTempDir());
	}

	/**
	 * A clone or fetch are long running operations. This method waits until the
	 * given task has completed, and then returns the location from the status object.
	 */
	protected String waitForTaskCompletion(String taskLocation) throws IOException, SAXException, JSONException {
		JSONObject status = null;
		long start = System.currentTimeMillis();
		while (true) {
			WebRequest request = new GetMethodWebRequest(taskLocation);
			WebResponse response = webConversation.getResponse(request);
			status = new JSONObject(response.getText());
			boolean running = status.getBoolean("Running");
			if (!running)
				break;
			//timeout after reasonable time to avoid hanging tests
			if (System.currentTimeMillis() - start > 10000)
				assertTrue("The operation took too long", false);
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				//ignore
			}
		}
		assertNotNull(status);
		return status.getString("Location");
	}

	/**
	 * Returns a request for obtaining metadata about a single git clone.
	 */
	protected WebRequest getCloneRequest(String cloneLocation) {
		WebRequest request = new GetMethodWebRequest(cloneLocation);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected static String getMethodName() {
		final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		return ste[3].getMethodName();
	}

	protected static JSONObject getChildByKey(List<JSONObject> children, String key, String value) throws JSONException {
		for (JSONObject child : children) {
			if (value.equals(child.getString(key)))
				return child;
		}
		return null;
	}

	protected static JSONObject getChildByName(List<JSONObject> children, String name) throws JSONException {
		return getChildByKey(children, ProtocolConstants.KEY_NAME, name);
	}

	protected Repository getRepositoryForContentLocation(String location) throws URISyntaxException, IOException {
		File file = new File(URIUtil.toFile(new URI(location)), Constants.DOT_GIT);
		assertTrue(file.exists());
		assertTrue(RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED));
		return new FileRepository(file);
	}

	// clone

	protected String clone(URIish uri, String name, String kh, char[] p) throws JSONException, IOException, SAXException {
		WebRequest request = getPostGitCloneRequest(uri.toString(), name, kh, p);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// validate the clone metadata
		response = webConversation.getResponse(getCloneRequest(cloneLocation));
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);
		return contentLocation;
	}

	protected String clone(URIish uri, String name) throws JSONException, IOException, SAXException {
		return clone(uri, name, null, null);
	}

	protected String clone(String name) throws JSONException, IOException, SAXException {
		URIish uri = new URIish(gitDir.toURL());
		return clone(uri, name);
	}

	protected static WebRequest getPostGitCloneRequest(URIish uri, String name) throws JSONException {
		return getPostGitCloneRequest(uri.toString(), name, null, null);
	}

	// link

	protected JSONObject linkProject(String contentLocation, String projectName) throws IOException, SAXException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		ServletTestingSupport.allowedPrefixes = contentLocation;
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	// remotes

	protected JSONObject getRemoteBranch(String gitRemoteUri, int size, int i, String name) throws IOException, SAXException, JSONException {
		assertRemoteUri(gitRemoteUri);
		WebRequest request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);

		request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		remote = new JSONObject(response.getText());
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		assertNotNull(remote.getString(ProtocolConstants.KEY_LOCATION));
		JSONArray refsArray = remote.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(size, refsArray.length());
		JSONObject ref = refsArray.getJSONObject(i);
		assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + name, ref.getString(ProtocolConstants.KEY_NAME));
		String newRefId = ref.getString(ProtocolConstants.KEY_ID);
		assertNotNull(newRefId);
		assertTrue(ObjectId.isId(newRefId));
		String remoteBranchLocation = ref.getString(ProtocolConstants.KEY_LOCATION);
		ref.getString(GitConstants.KEY_COMMIT);

		request = GitRemoteTest.getGetGitRemoteRequest(remoteBranchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remoteBranch = new JSONObject(response.getText());
		remoteBranch.getString(GitConstants.KEY_COMMIT);
		remoteBranch.getString(GitConstants.KEY_HEAD);

		return remoteBranch;
	}

	protected JSONObject merge(String gitCommitUri, String commit) throws JSONException, IOException, SAXException {
		assertCommitUri(gitCommitUri);
		WebRequest request = getPostGitMergeRequest(gitCommitUri, commit);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	// push

	protected void pushAll(String contentLocation) throws IOException, URISyntaxException, JGitInternalException, GitAPIException {
		FileRepository db1 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation)), Constants.DOT_GIT));
		Git git = new Git(db1);
		// TODO: replace with REST API when bug 339115 is fixed
		git.push().setPushAll().call();
	}

	/**
	 * Pushes the changes from the the source ref to "origin/master". 
	 * The implementation assumes there is only single remote branch i.e. "master".
	 * 
	 * @param gitRemoteUri remote URI
	 * @param srcRef the source ref to push
	 * @return JSON object representing response
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	protected ServerStatus push(String gitRemoteUri, String srcRef) throws IOException, SAXException, JSONException {
		return push(gitRemoteUri, 1, 0, Constants.MASTER, srcRef);
	}

	protected ServerStatus push(String gitRemoteUri, int size, int i, String name, String srcRef) throws IOException, SAXException, JSONException {
		assertRemoteUri(gitRemoteUri);
		String remoteBranchLocation = getRemoteBranch(gitRemoteUri, size, i, name).getString(ProtocolConstants.KEY_LOCATION);
		WebRequest request = GitPushTest.getPostGitRemoteRequest(remoteBranchLocation, srcRef);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		waitForTaskCompletion(taskLocation);

		// get task details again
		request = new GetMethodWebRequest(taskLocation);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		return ServerStatus.fromJSON(new JSONObject(response.getText()).optJSONObject("Result"/*TaskInfo.KEY_RESULT*/).toString());
	}

	/**
	 * Fetch objects and refs from the given remote branch. 
	 * 
	 * @param remoteBranchLocation remote branch URI
	 * @return JSONObject representing remote branch after the fetch is done
	 * @throws JSONException
	 * @throws IOException
	 * @throws SAXException
	 */
	protected JSONObject fetch(String remoteBranchLocation) throws JSONException, IOException, SAXException {
		assertRemoteBranchLocation(remoteBranchLocation);

		// fetch
		WebRequest request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		waitForTaskCompletion(taskLocation);

		// get remote branch details again
		request = GitRemoteTest.getGetGitRemoteRequest(remoteBranchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	private void assertRemoteUri(String remoteUri) {
		URI uri = URI.create(remoteUri);
		IPath path = new Path(uri.getPath());
		// /git/remote/file/{path}
		assertTrue(path.segmentCount() > 3);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.REMOTE_RESOURCE, path.segment(1));
		assertEquals("file", path.segment(2));
	}

	private void assertCommitUri(String commitUri) {
		URI uri = URI.create(commitUri);
		IPath path = new Path(uri.getPath());
		// /git/commit/{ref}/file/{path}
		assertTrue(path.segmentCount() > 4);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.COMMIT_RESOURCE, path.segment(1));
		assertEquals("file", path.segment(3));
	}

	private void assertRemoteBranchLocation(String remoteBranchLocation) {
		URI uri = URI.create(remoteBranchLocation);
		IPath path = new Path(uri.getPath());
		// /git/remote/{remote}/{branch}/file/{path}
		assertTrue(path.segmentCount() > 5);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.REMOTE_RESOURCE, path.segment(1));
		assertEquals("file", path.segment(4));
	}

	/**
	 * Creates a request to create a git clone for the given URI.
	 * @param uri Git URL to clone
	 * @param name project name
	 * @param kh known hosts
	 * @param p password
	 * @return the request
	 * @throws JSONException
	 */
	protected static WebRequest getPostGitCloneRequest(String uri, String name, String kh, char[] p) throws JSONException {
		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CLONE_RESOURCE + '/';
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_URL, uri);
		body.put(ProtocolConstants.KEY_NAME, name);
		if (kh != null)
			body.put(GitConstants.KEY_KNOWN_HOSTS, kh);
		if (p != null)
			body.put(GitConstants.KEY_PASSWORD, new String(p));
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private static WebRequest getPostGitMergeRequest(String location, String commit) throws JSONException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_MERGE, commit);
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
