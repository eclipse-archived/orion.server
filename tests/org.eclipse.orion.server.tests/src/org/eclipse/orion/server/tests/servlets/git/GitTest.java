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

import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.core.ServerConstants;
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
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public abstract class GitTest extends FileSystemTest {

	static final String GIT_SERVLET_LOCATION = GitServlet.GIT_URI + '/';

	static WebConversation webConversation;
	File gitDir;
	File testFile;
	protected FileRepository db;

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	protected static String sshRepo;
	protected static String sshRepo2;
	protected static char[] password;
	protected static String knownHosts;
	protected static String knownHosts2;
	protected static byte[] privateKey;
	protected static byte[] publicKey;
	protected static byte[] passphrase;

	protected static void readSshProperties() {
		String propertiesFile = System.getProperty("orion.tests.ssh");
		if (propertiesFile == null)
			return;
		Map<String, String> properties = new HashMap<String, String>();
		try {
			File file = new File(propertiesFile);
			if (file.isDirectory())
				file = new File(file, "sshtest.properties");
			BufferedReader reader = new BufferedReader(new FileReader(file));
			try {
				for (String line; (line = reader.readLine()) != null;) {
					if (line.startsWith("#"))
						continue;
					int sep = line.indexOf("=");
					String property = line.substring(0, sep).trim();
					String value = line.substring(sep + 1).trim();
					properties.put(property, value);
				}
			} finally {
				reader.close();
			}
			// initialize constants
			sshRepo = properties.get("host");
			sshRepo2 = properties.get("host2");
			password = properties.get("password").toCharArray();
			knownHosts = properties.get("knownHosts");
			knownHosts2 = properties.get("knownHosts2");
			privateKey = loadFileContents(properties.get("privateKeyPath")).getBytes();
			publicKey = loadFileContents(properties.get("publicKeyPath")).getBytes();
			passphrase = properties.get("passphrase").getBytes();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Could not read ssh properties file: " + propertiesFile);
		}
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
	public void tearDown() throws Exception {
		db.close();
		FileUtils.delete(gitDir, FileUtils.RECURSIVE);
	}

	protected JSONObject linkProject(String contentLocation, String projectName) throws JSONException, IOException, SAXException {
		// TODO: remove me
		URI workspaceLocation = createWorkspace(getMethodName());
		return createProjectOrLink(workspaceLocation, projectName, contentLocation);
	}

	protected JSONObject createProjectOrLink(URI workspaceLocation, String projectName, String contentLocation) throws JSONException, IOException, SAXException {
		JSONObject body = new JSONObject();
		if (contentLocation != null) {
			body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
			ServletTestingSupport.allowedPrefixes = contentLocation;
		}
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), getJsonAsStream(body.toString()), "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);
		return project;
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
			String text = response.getText();
			status = new JSONObject(text);
			if (status.isNull("Running"))
				Assert.fail("Unexpected task format: " + text);
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
		return status.getString(ProtocolConstants.KEY_LOCATION);
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

	protected static Repository getRepositoryForContentLocation(String fileLocation) throws CoreException, IOException {
		assertFileUri(fileLocation);

		URI uri = URI.create(fileLocation);
		IPath path = new Path(uri.getPath());

		WebProject.exists(path.segment(1));
		WebProject wp = WebProject.fromId(path.segment(1));
		IFileStore fsStore = getProjectStore(wp, "test");
		fsStore = fsStore.getFileStore(path.removeFirstSegments(2));

		File file = new File(fsStore.toURI());
		if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
			// 'file' is already what we're looking for
		} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
			file = new File(file, Constants.DOT_GIT);
		} else {
			fail(fileLocation + " is not a repository");
		}
		return new FileRepository(file);
	}

	// see org.eclipse.orion.internal.server.servlets.workspace.WorkspaceResourceHandler.generateProjectLocation(WebProject, String)
	private static IFileStore getProjectStore(WebProject project, String user) throws CoreException {
		URI platformLocationURI = Activator.getDefault().getRootLocationURI();
		IFileStore root = EFS.getStore(platformLocationURI);

		//consult layout preference
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode("org.eclipse.orion.server.configurator"); //$NON-NLS-1$
		String layout = preferences.get(ServerConstants.CONFIG_FILE_LAYOUT, "flat").toLowerCase(); //$NON-NLS-1$

		IFileStore projectStore;
		if ("usertree".equals(layout) && user != null) { //$NON-NLS-1$
			//the user-tree layout organises projects by the user who created it
			String userPrefix = user.substring(0, Math.min(2, user.length()));
			projectStore = root.getChild(userPrefix).getChild(user).getChild(project.getId());
		} else {
			//default layout is a flat list of projects at the root
			projectStore = root.getChild(project.getId());
		}
		projectStore.mkdir(EFS.NONE, null);
		return projectStore;
	}

	// clone

	protected JSONObject clone(URIish uri, IPath workspacePath, IPath filePath, String name, String kh, char[] p) throws JSONException, IOException, SAXException, CoreException {
		// clone
		WebRequest request = getPostGitCloneRequest(uri, workspacePath, filePath, name, kh, p);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// validate the clone metadata
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length());
		JSONObject clone = clonesArray.getJSONObject(0);
		String n = clone.getString(ProtocolConstants.KEY_NAME);
		if (filePath != null)
			assertTrue(filePath.segmentCount() == 2 && n.equals(WebProject.fromId(filePath.segment(1)).getName()) || n.equals(filePath.lastSegment()));
		if (workspacePath != null)
			if (name != null)
				assertEquals(name, n);
			else
				assertEquals(uri.getHumanishName(), n);
		assertCloneUri(clone.getString(ProtocolConstants.KEY_LOCATION));
		assertFileUri(clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		assertRemoteUri(clone.getString(GitConstants.KEY_REMOTE));
		assertBranchUri(clone.getString(GitConstants.KEY_BRANCH));
		return clone;
	}

	// init

	protected JSONObject init(IPath workspacePath, IPath filePath, String name) throws JSONException, IOException, SAXException, CoreException {
		// no Git URL for init
		WebRequest request = getPostGitCloneRequest(null, workspacePath, filePath, name, null, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// validate the clone metadata
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length());
		JSONObject clone = clonesArray.getJSONObject(0);
		String n = clone.getString(ProtocolConstants.KEY_NAME);
		if (filePath != null)
			assertTrue(filePath.segmentCount() == 2 && n.equals(WebProject.fromId(filePath.segment(1)).getName()) || n.equals(filePath.lastSegment()));
		if (workspacePath != null && name != null)
			assertEquals(name, n);
		assertCloneUri(clone.getString(ProtocolConstants.KEY_LOCATION));
		assertFileUri(clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		assertRemoteUri(clone.getString(GitConstants.KEY_REMOTE));
		assertBranchUri(clone.getString(GitConstants.KEY_BRANCH));
		return clone;
	}

	protected JSONObject clone(IPath workspacePath, IPath filePath, String name) throws JSONException, IOException, SAXException, CoreException {
		URIish uri = new URIish(gitDir.toURL());
		return clone(uri, workspacePath, filePath, name, null, null);
	}

	protected JSONObject clone(IPath filePath) throws JSONException, IOException, SAXException, CoreException {
		URIish uri = new URIish(gitDir.toURL());
		return clone(uri, null, filePath, null, null, null);
	}

	// remotes
	protected JSONObject getRemote(String gitRemoteUri, int size, int i, String name) throws IOException, SAXException, JSONException {
		assertRemoteUri(gitRemoteUri);
		WebRequest request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(size, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(i);
		assertNotNull(remote);
		assertEquals(name, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteLocation);
		return remote;
	}

	protected WebResponse addRemote(String remotesLocation, String name, String uri) throws JSONException, IOException, SAXException {
		return addRemote(remotesLocation, name, uri, null, null, null);
	}

	protected WebResponse addRemote(String remotesLocation, String name, String uri, String fetchRefSpec, String pushUri, String pushRefSpec) throws JSONException, IOException, SAXException {
		assertRemoteUri(remotesLocation);
		WebRequest request = GitRemoteTest.getPostGitRemoteRequest(remotesLocation, name, uri, fetchRefSpec, pushUri, pushRefSpec);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		return response;
	}

	protected JSONObject getRemoteBranch(String gitRemoteUri, int size, int i, String name) throws IOException, SAXException, JSONException {
		assertRemoteUri(gitRemoteUri);
		JSONObject remote = getRemote(gitRemoteUri, 1, 0, Constants.DEFAULT_REMOTE_NAME);
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);

		WebRequest request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		remote = new JSONObject(response.getText());
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		assertNotNull(remote.getString(ProtocolConstants.KEY_LOCATION));
		JSONArray refsArray = remote.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(size, refsArray.length());
		JSONObject ref = refsArray.getJSONObject(i);
		assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + name, ref.getString(ProtocolConstants.KEY_FULL_NAME));
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

	protected JSONObject merge(String gitHeadUri, String commit) throws JSONException, IOException, SAXException {
		assertCommitUri(gitHeadUri);
		WebRequest request = getPostGitMergeRequest(gitHeadUri, commit);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	// rebase 

	protected JSONObject rebase(String gitHeadUri, Operation operation) throws IOException, SAXException, JSONException {
		assertCommitUri(gitHeadUri);
		WebRequest request = GitRebaseTest.getPostGitRebaseRequest(gitHeadUri, "", operation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	protected JSONObject rebase(String gitHeadUri, String commit) throws IOException, SAXException, JSONException {
		assertCommitUri(gitHeadUri);
		WebRequest request = GitRebaseTest.getPostGitRebaseRequest(gitHeadUri, commit, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	// push

	protected ServerStatus push(String gitRemoteUri, int size, int i, String name, String srcRef, boolean tags) throws IOException, SAXException, JSONException {
		return push(gitRemoteUri, size, i, name, srcRef, tags, false);
	}

	protected ServerStatus push(String gitRemoteUri, int size, int i, String name, String srcRef, boolean tags, boolean force) throws IOException, SAXException, JSONException {
		assertRemoteUri(gitRemoteUri);
		String remoteBranchLocation = getRemoteBranch(gitRemoteUri, size, i, name).getString(ProtocolConstants.KEY_LOCATION);
		return push(remoteBranchLocation, srcRef, tags, force);
	}

	/**
	 * Pushes the changes from the the source ref to the given remote branch.
	 * 
	 * @param gitRemoteBranchUri remote branch URI
	 * @param srcRef the source ref to push
	 * @param tags <code>true</code> to push tags
	 * @return JSON object representing response
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	protected ServerStatus push(String gitRemoteBranchUri, String srcRef, boolean tags) throws IOException, SAXException, JSONException {
		return push(gitRemoteBranchUri, srcRef, tags, false);
	}

	/**
	 * Pushes the changes from the the source ref to the given remote branch.
	 * 
	 * @param gitRemoteBranchUri remote branch URI
	 * @param srcRef the source ref to push
	 * @param tags <code>true</code> to push tags
	 * @param force <code>true</code> to force push
	 * @return JSON object representing response
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	protected ServerStatus push(String gitRemoteBranchUri, String srcRef, boolean tags, boolean force) throws IOException, SAXException, JSONException {
		assertRemoteOrRemoteBranchLocation(gitRemoteBranchUri);
		WebRequest request = GitPushTest.getPostGitRemoteRequest(gitRemoteBranchUri, srcRef, tags, force);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
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

	protected ServerStatus push(String gitRemoteUri, int size, int i, String name, String srcRef, boolean tags, String userName, String kh, byte[] privk, byte[] pubk, byte[] p, boolean shouldBeOK) throws IOException, SAXException, JSONException {
		return push(gitRemoteUri, size, i, name, srcRef, tags, false, userName, kh, privk, pubk, p, shouldBeOK);
	}

	protected ServerStatus push(String gitRemoteUri, int size, int i, String name, String srcRef, boolean tags, boolean force, String userName, String kh, byte[] privk, byte[] pubk, byte[] p, boolean shouldBeOK) throws IOException, SAXException, JSONException {
		assertRemoteUri(gitRemoteUri);
		String remoteBranchLocation = getRemoteBranch(gitRemoteUri, size, i, name).getString(ProtocolConstants.KEY_LOCATION);
		WebRequest request = GitPushTest.getPostGitRemoteRequest(remoteBranchLocation, srcRef, tags, force, userName, kh, privk, pubk, p);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String location = waitForTaskCompletion(taskLocation);

		if (shouldBeOK) {
			request = new GetMethodWebRequest(location);
			response = webConversation.getResponse(request);
			JSONObject status = new JSONObject(response.getText());

			assertFalse(status.getBoolean("Running"));
			assertEquals(HttpURLConnection.HTTP_OK, status.getJSONObject("Result").getInt("HttpCode"));
		}

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
	protected JSONObject fetch(String remoteBranchLocation, String userName, String kh, byte[] privk, byte[] pubk, byte[] p, boolean shouldBeOK) throws JSONException, IOException, SAXException {
		return fetch(remoteBranchLocation, false, userName, kh, privk, pubk, p, shouldBeOK);
	}

	/**
	 * Fetch objects and refs from the given remote branch. 
	 * 
	 * @param remoteBranchLocation remote branch URI
	 * @param force <code>true</code> to force fetch
	 * @return JSONObject representing remote branch after the fetch is done
	 * @throws JSONException
	 * @throws IOException
	 * @throws SAXException
	 */
	protected JSONObject fetch(String remoteBranchLocation, boolean force, String userName, String kh, byte[] privk, byte[] pubk, byte[] p, boolean shouldBeOK) throws JSONException, IOException, SAXException {
		assertRemoteOrRemoteBranchLocation(remoteBranchLocation);

		// fetch
		WebRequest request = GitFetchTest.getPostGitRemoteRequest(remoteBranchLocation, true, force, userName, kh, privk, pubk, p);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String location = waitForTaskCompletion(taskLocation);

		if (shouldBeOK) {
			request = new GetMethodWebRequest(location);
			response = webConversation.getResponse(request);
			JSONObject status = new JSONObject(response.getText());

			assertFalse(status.getBoolean("Running"));
			assertEquals(HttpURLConnection.HTTP_OK, status.getJSONObject("Result").getInt("HttpCode"));
		}

		// get remote branch details again
		request = GitRemoteTest.getGetGitRemoteRequest(remoteBranchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	/**
	 * Fetch objects and refs from the given remote or remote branch.
	 * 
	 * @param remoteLocation remote (branch) URI
	 * @return JSONObject representing remote (branch) after the fetch is done
	 * @throws JSONException
	 * @throws IOException
	 * @throws SAXException
	 */
	protected JSONObject fetch(String remoteLocation) throws JSONException, IOException, SAXException {
		return fetch(remoteLocation, false);
	}

	/**
	 * Fetch objects and refs from the given remote or remote branch.
	 * 
	 * @param remoteLocation remote (branch) URI
	 * @param force <code>true</code> to force fetch
	 * @return JSONObject representing remote (branch) after the fetch is done
	 * @throws JSONException
	 * @throws IOException
	 * @throws SAXException
	 */
	protected JSONObject fetch(String remoteLocation, boolean force) throws JSONException, IOException, SAXException {
		assertRemoteOrRemoteBranchLocation(remoteLocation);

		// fetch
		WebRequest request = GitFetchTest.getPostGitRemoteRequest(remoteLocation, true, force);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String location = waitForTaskCompletion(taskLocation);

		// validate task completed successfully
		request = getGetRequest(location);
		response = webConversation.getResponse(request);
		JSONObject status = new JSONObject(response.getText());
		assertFalse(status.getBoolean("Running"));
		assertEquals(HttpURLConnection.HTTP_OK, status.getJSONObject("Result").getInt("HttpCode"));

		// get remote (branch) details again
		request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	// tag
	/**
	 * Tag the commit with given tag name 
	 * 
	 * @param gitTagUri tags URI
	 * @param tagName tag name to use
	 * @param commitId commit to tag
	 * @return created tag object
	 * @throws JSONException
	 * @throws IOException
	 * @throws SAXException
	 * @throws URISyntaxException
	 */
	protected JSONObject tag(String gitTagUri, String tagName, String commitId) throws JSONException, IOException, SAXException, URISyntaxException {
		assertTagUri(gitTagUri);
		WebRequest request = getPostGitTagRequest(gitTagUri, tagName, commitId);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	/**
	 * Tag the selected commit with the given name.
	 * 
	 * @param gitCommitUri selected commit URI
	 * @param tagName tag name to use
	 * @return an updated commit object
	 * @throws JSONException
	 * @throws IOException
	 * @throws SAXException
	 */
	protected JSONObject tag(String gitCommitUri, String tagName) throws JSONException, IOException, SAXException {
		assertCommitUri(gitCommitUri);
		WebRequest request = getPutGitCommitRequest(gitCommitUri, tagName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	protected JSONArray listTags(String gitTagUri) throws IOException, SAXException, JSONException {
		assertTagUri(gitTagUri);
		WebRequest request = getGetGitTagRequest(gitTagUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject tags = new JSONObject(response.getText());
		return tags.getJSONArray(ProtocolConstants.KEY_CHILDREN);
	}

	/**
	 * Return commits for the given URI.
	 * 
	 * @param gitCommitUri commit URI
	 * @param remote <code>true</code> if remote location is expected
	 * @return
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	protected JSONArray log(String gitCommitUri, boolean remote) throws IOException, SAXException, JSONException {
		return log(gitCommitUri, remote, null, null);
	}

	/**
	 * Return commits for the given URI.
	 * 
	 * @param gitCommitUri commit URI
	 * @param remote <code>true</code> if remote location is expected
	 * @param page number of page or <code>null</code> if all results
	 * @param pageSize size of page or <code>null</code> if default
	 * @return
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	protected JSONArray log(String gitCommitUri, boolean remote, Integer page, Integer pageSize) throws IOException, SAXException, JSONException {
		assertCommitUri(gitCommitUri);
		WebRequest request = GitCommitTest.getGetGitCommitRequest(gitCommitUri, false, page, pageSize);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject log = new JSONObject(response.getText());
		if (remote) // will fail if the key is not found
			log.getString(GitConstants.KEY_REMOTE);
		return log.getJSONArray(ProtocolConstants.KEY_CHILDREN);
	}

	protected WebResponse branch(String branchesLocation, String branchName) throws JGitInternalException, IOException, JSONException, SAXException {
		return branch(branchesLocation, branchName, null);
	}

	// branch
	protected WebResponse branch(String branchesLocation, String branchName, String startPoint) throws JGitInternalException, IOException, JSONException, SAXException {
		assertBranchUri(branchesLocation);
		WebRequest request = getPostGitBranchRequest(branchesLocation, branchName, startPoint);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		return response;
	}

	static void assertBranchExist(Git git, String branch) {
		List<Ref> list = git.branchList().call();
		for (Ref ref : list) {
			if (ref.getName().equals(Constants.R_HEADS + branch)) {
				return; // found
			}
		}
		fail("branch '" + branch + "' doesn't exist locally");
	}

	protected WebResponse checkoutBranch(String cloneLocation, String branchName) throws JSONException, IOException, SAXException {
		String requestURI;
		if (cloneLocation.startsWith("http://")) {
			// assume the caller knows what he's doing
			// assertCloneUri(location);
			requestURI = cloneLocation;
		} else {
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CLONE_RESOURCE + cloneLocation;
		}
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_BRANCH_NAME, branchName);
		WebRequest request = new PutMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return webConversation.getResponse(request);
	}

	protected String getWorkspaceId(URI uri) throws IOException, SAXException, JSONException {
		assertWorkspaceUri(uri);
		WebRequest request = new GetMethodWebRequest(uri.toString());
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		JSONObject workspace = new JSONObject(response.getText());
		return workspace.getString(ProtocolConstants.KEY_ID);
	}

	// assertions for URIs

	private static void assertRemoteUri(String remoteUri) {
		URI uri = URI.create(remoteUri);
		IPath path = new Path(uri.getPath());
		// /git/remote/file/{path}
		assertTrue(path.segmentCount() > 3);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.REMOTE_RESOURCE, path.segment(1));
		assertEquals("file", path.segment(2));
	}

	protected static void assertCommitUri(String commitUri) {
		URI uri = URI.create(commitUri);
		IPath path = new Path(uri.getPath());
		AssertionError error = null;
		// /gitapi/commit/{ref}/file/{path}
		try {
			assertTrue(path.segmentCount() > 4);
			assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
			assertEquals(GitConstants.COMMIT_RESOURCE, path.segment(1));
			assertEquals("file", path.segment(3));
		} catch (AssertionError e) {
			error = e;
		}

		// /gitapi/commit/file/{path}
		try {
			assertTrue(path.segmentCount() > 3);
			assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
			assertEquals(GitConstants.COMMIT_RESOURCE, path.segment(1));
			assertEquals("file", path.segment(2));
		} catch (AssertionError e) {
			if (error != null) {
				throw error; // rethrow the first exception
			} // otherwise it's a commit location, ignore this exception
		}
	}

	static void assertStatusUri(String statusUri) {
		URI uri = URI.create(statusUri);
		IPath path = new Path(uri.getPath());
		// /git/status/file/{path}
		assertTrue(path.segmentCount() > 3);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.STATUS_RESOURCE, path.segment(1));
		assertEquals("file", path.segment(2));
	}

	private static void assertRemoteOrRemoteBranchLocation(String remoteLocation) {
		URI uri = URI.create(remoteLocation);
		IPath path = new Path(uri.getPath());
		AssertionError error = null;
		// /git/remote/{remote}/file/{path}
		try {
			assertTrue(path.segmentCount() > 4);
			assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
			assertEquals(GitConstants.REMOTE_RESOURCE, path.segment(1));
			assertEquals("file", path.segment(3));
		} catch (AssertionError e) {
			error = e;
		}

		// /git/remote/{remote}/{branch}/file/{path}
		try {
			assertTrue(path.segmentCount() > 5);
			assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
			assertEquals(GitConstants.REMOTE_RESOURCE, path.segment(1));
			assertEquals("file", path.segment(4));
		} catch (AssertionError e) {
			if (error != null) {
				throw error; // rethrow the first exception
			} // otherwise it's a remote location, ignore this exception
		}
	}

	private static void assertTagUri(String tagUri) {
		URI uri = URI.create(tagUri);
		IPath path = new Path(uri.getPath());
		// /git/tag/file/{path}
		assertTrue(path.segmentCount() > 3);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.TAG_RESOURCE, path.segment(1));
		assertEquals("file", path.segment(2));
	}

	static void assertCloneUri(String cloneUri) throws CoreException, IOException {
		URI uri = URI.create(cloneUri);
		IPath path = new Path(uri.getPath());
		// /git/clone/workspace/{id} or /git/clone/file/{id}[/{path}]
		assertTrue(path.segmentCount() > 3);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.CLONE_RESOURCE, path.segment(1));
		assertTrue("workspace".equals(path.segment(2)) || "file".equals(path.segment(2)));
		if ("workspace".equals(path.segment(2)))
			assertEquals(4, path.segmentCount());
		// TODO: check if clone
		if ("file".equals(path.segment(2)))
			getRepositoryForContentLocation(SERVER_LOCATION + path.removeFirstSegments(2).makeAbsolute());
	}

	private static void assertWorkspaceUri(URI uri) {
		IPath path = new Path(uri.getPath());
		// /workspace/{id}
		assertTrue(path.segmentCount() == 2);
		assertEquals("workspace", path.segment(0));
	}

	private static void assertFileUri(String fileUri) {
		URI uri = URI.create(fileUri);
		IPath path = new Path(uri.getPath());
		// /file/{id}[/{path}]
		assertTrue(path.segmentCount() > 1);
		assertEquals("file", path.segment(0));
	}

	protected static void assertBranchUri(String branchUri) {
		URI uri = URI.create(branchUri);
		IPath path = new Path(uri.getPath());
		// /git/branch/[{name}/]file/{path}
		assertTrue(path.segmentCount() > 3);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.BRANCH_RESOURCE, path.segment(1));
		assertTrue("file".equals(path.segment(2)) || "file".equals(path.segment(3)));
	}

	protected static void assertConfigUri(String configUri) {
		URI uri = URI.create(configUri);
		IPath path = new Path(uri.getPath());
		// /gitapi/config/[{key}/]clone/file/{id}
		assertTrue(path.segmentCount() > 4);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(GitConstants.CONFIG_RESOURCE, path.segment(1));
		assertTrue(GitConstants.CLONE_RESOURCE.equals(path.segment(2)) || GitConstants.CLONE_RESOURCE.equals(path.segment(3)));
		if (GitConstants.CLONE_RESOURCE.equals(path.segment(2)))
			assertTrue("file".equals(path.segment(3)));
		else
			assertTrue("file".equals(path.segment(4)));
	}

	protected static void assertGitSectionExists(JSONObject json) {
		JSONObject gitSection = json.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		assertNotNull(gitSection.optString(GitConstants.KEY_STATUS, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_DIFF, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_INDEX, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_HEAD, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_REMOTE, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_TAG, null));
		assertNotNull(gitSection.optString(GitConstants.KEY_CLONE, null));
	}

	// web requests

	/**
	 * Creates a request to create a git clone for the given URI.
	 * @param uri Git URL to clone
	 * @param workspacePath workspace path (/workspace/{workspaceId}), required when no filePath is given
	 * @param filePath project/folder path
	 * @param name new project name
	 * @param kh known hosts
	 * @param p password
	 * @return the request
	 * @throws JSONException
	 * @throws UnsupportedEncodingException 
	 * @throws URISyntaxException 
	 */
	protected static WebRequest getPostGitCloneRequest(URIish uri, IPath workspacePath, IPath filePath, String name, String kh, char[] p) throws JSONException, UnsupportedEncodingException {
		return new PostGitCloneRequest().setURIish(uri).setWorkspacePath(workspacePath).setFilePath(filePath).setName(name).setKnownHosts(kh).setPassword(p).getWebRequest();
	}

	private static WebRequest getPostGitMergeRequest(String location, String commit) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_MERGE, commit);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private static WebRequest getPostGitTagRequest(String location, String tagName, String commitId) throws JSONException, UnsupportedEncodingException {
		assertTagUri(location);
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.TAG_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_NAME, tagName);
		body.put(GitConstants.KEY_TAG_COMMIT, commitId);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private static WebRequest getPutGitCommitRequest(String location, String tagName) throws UnsupportedEncodingException, JSONException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_NAME, tagName);
		WebRequest request = new PutMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private static WebRequest getGetGitTagRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.TAG_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	/**
	 * Returns a GET request for the given location.
	 */
	protected WebRequest getGetRequest(String location) {
		WebRequest request = new GetMethodWebRequest(location);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private WebRequest getPostGitBranchRequest(String location, String branchName, String startPoint) throws IOException, JSONException {
		String requestURI;
		if (location.startsWith("http://")) {
			requestURI = location;
		} else {
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.BRANCH_RESOURCE + location;
		}
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_NAME, branchName);
		body.put(GitConstants.KEY_BRANCH_NAME, startPoint);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected String clone(WebRequest request) throws JSONException, IOException, SAXException {
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForTaskCompletion(taskLocation);

		// validate the clone metadata
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length());
		JSONObject clone = clonesArray.getJSONObject(0);
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);
		return contentLocation;
	}

	// Utility methods

	protected static String loadFileContents(String path) throws IOException {
		File file = new File(path);
		InputStream is = new FileInputStream(file);
		return toString(is);
	}

	private static String toString(InputStream is) throws IOException {
		if (is != null) {
			Writer writer = new StringWriter();
			char[] buffer = new char[1024];
			try {
				Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				int n;
				while ((n = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, n);
				}
			} finally {
				is.close();
			}
			return writer.toString();
		}
		return "";
	}

	protected void assertRepositoryInfo(URIish uri, JSONObject result) throws JSONException {
		assertEquals(uri.toString(), result.getJSONObject("ErrorData").get("Url"));
		if (uri.getUser() != null)
			assertEquals(uri.getUser(), result.getJSONObject("ErrorData").get("User"));
		if (uri.getHost() != null)
			assertEquals(uri.getHost(), result.getJSONObject("ErrorData").get("Host"));
		if (uri.getHumanishName() != null)
			assertEquals(uri.getHumanishName(), result.getJSONObject("ErrorData").get("HumanishName"));
		if (uri.getPass() != null)
			assertEquals(uri.getPass(), result.getJSONObject("ErrorData").get("Password"));
		if (uri.getPort() > 0)
			assertEquals(uri.getPort(), result.getJSONObject("ErrorData").get("Port"));
		if (uri.getScheme() != null)
			assertEquals(uri.getScheme(), result.getJSONObject("ErrorData").get("Scheme"));
	}
}
