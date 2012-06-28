/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others 
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Branch;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.objects.Commit;
import org.eclipse.orion.server.git.objects.ConfigOption;
import org.eclipse.orion.server.git.objects.Remote;
import org.eclipse.orion.server.git.objects.RemoteBranch;
import org.eclipse.orion.server.git.objects.Status;
import org.eclipse.orion.server.git.objects.Tag;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
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
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), IOUtilities.toInputStream(body.toString()), "UTF-8");
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
		IPath randomLocation = AllGitTests.getRandomLocation();
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

		// The system settings on eclipse.org was changed to receive.denyNonFastForward=true, see bug 343150.
		// Imitate the same setup when running tests locally, see bug 371881.
		FileBasedConfig cfg = db.getConfig();
		cfg.setBoolean("receive", null, "denyNonFastforwards", true);
		cfg.save();
	}

	/**
	 * Some git commands are long running operations. This method waits until the
	 * given task has completed, and then returns the location from the status object.
	 */
	protected String waitForTaskCompletion(String taskLocation) throws IOException, SAXException, JSONException {
		return waitForTaskCompletionObject(taskLocation, testUserLogin, testUserPassword).getString(ProtocolConstants.KEY_LOCATION);
	}

	protected String waitForTaskCompletion(String taskLocation, String userName, String userPassword) throws IOException, SAXException, JSONException {
		return waitForTaskCompletionObject(taskLocation, userName, userPassword).getString(ProtocolConstants.KEY_LOCATION);
	}

	private JSONObject waitForTaskCompletionObject(String taskLocation, String userName, String userPassword) throws IOException, SAXException, JSONException {
		JSONObject status = null;
		long start = System.currentTimeMillis();
		while (true) {
			WebRequest request = new GetMethodWebRequest(taskLocation);
			setAuthentication(request, userName, userPassword);
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
		return status;
	}

	protected JSONObject getResult(WebResponse response) throws JSONException, IOException {
		JSONObject jsonResponse = new JSONObject(response.getText());
		if (jsonResponse.has("Result"))
			return jsonResponse.getJSONObject("Result");
		return jsonResponse;
	}

	protected WebResponse waitForTaskCompletionObjectResponse(WebResponse response) throws IOException, SAXException, JSONException {
		return waitForTaskCompletionObjectResponse(response, testUserLogin, testUserPassword);
	}

	protected WebResponse waitForTaskCompletionObjectResponse(WebResponse response, String userName, String userPassword) throws IOException, SAXException, JSONException {
		if (response.getResponseCode() == HttpURLConnection.HTTP_ACCEPTED) {
			JSONObject status = null;
			long start = System.currentTimeMillis();
			while (true) {
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
				WebRequest request = new GetMethodWebRequest(status.getString("Location"));
				setAuthentication(request, userName, userPassword);
				response = webConversation.getResponse(request);
			}
			return response;
		}
		return response;
	}

	protected JSONObject waitForTaskCompletion(WebResponse response) throws IOException, JSONException, SAXException {
		if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
			String text = response.getText();
			return new JSONObject(text);
		} else if (response.getResponseCode() == HttpURLConnection.HTTP_ACCEPTED) {
			JSONObject taskResp = new JSONObject(response.getText());
			JSONObject status = waitForTaskCompletionObject(taskResp.getString(ProtocolConstants.KEY_LOCATION), testUserLogin, testUserPassword);
			assertTrue(status.has("Result"));
			assertFalse(status.getString("Result"), status.has("Failed") && status.getBoolean("Failed"));
			JSONObject result = status.getJSONObject("Result");
			return result.has("JsonData") ? result.getJSONObject("JsonData") : result;
		}
		fail("Task failed with code " + response.getResponseCode() + ", result: " + response.getText());
		return null;
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
		WebResponse response = waitForTaskCompletionObjectResponse(webConversation.getResponse(request));
		String cloneLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		if (cloneLocation == null) {
			JSONObject taskResp = new JSONObject(response.getText());
			assertTrue(taskResp.toString(), taskResp.has(ProtocolConstants.KEY_LOCATION));
			assertFalse(taskResp.getString(TaskInfo.KEY_RESULT), taskResp.has(TaskInfo.KEY_FAILED) && taskResp.getBoolean(TaskInfo.KEY_FAILED));
			cloneLocation = taskResp.getString(ProtocolConstants.KEY_LOCATION);
		}
		assertNotNull(cloneLocation);

		// validate the clone metadata
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		assertTrue("Clone doesn't have children at " + cloneLocation, clones.has(ProtocolConstants.KEY_CHILDREN));
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
		WebResponse response = waitForTaskCompletionObjectResponse(webConversation.getResponse(request));

		String cloneLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		if (cloneLocation == null) {
			JSONObject taskResp = new JSONObject(response.getText());
			assertTrue(taskResp.has(ProtocolConstants.KEY_LOCATION));
			assertFalse(taskResp.getString(TaskInfo.KEY_RESULT), taskResp.has(TaskInfo.KEY_FAILED) && taskResp.getBoolean(TaskInfo.KEY_FAILED));
			cloneLocation = taskResp.getString(ProtocolConstants.KEY_LOCATION);
		}
		assertNotNull(cloneLocation);

		// validate the clone metadata
		response = webConversation.getResponse(getGetRequest(cloneLocation));
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		assertTrue("Clone doesn't have children at " + cloneLocation, clones.has(ProtocolConstants.KEY_CHILDREN));
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
		URIish uri = new URIish(gitDir.toURI().toURL());
		return clone(uri, workspacePath, filePath, name, null, null);
	}

	protected JSONObject clone(IPath filePath) throws JSONException, IOException, SAXException, CoreException {
		URIish uri = new URIish(gitDir.toURI().toURL());
		return clone(uri, null, filePath, null, null, null);
	}

	/**
	 * Returns <code>JSONObject</code> representing clone. Assume there is single clone for the given <code>gitResource</code>.
	 *
	 * @param gitResource a project, folder or file with {@link GitConstants.KEY_GIT} property
	 * @return
	 * @throws JSONException
	 * @throws IOException
	 * @throws SAXException
	 */
	protected JSONObject getCloneForGitResource(JSONObject gitResource) throws JSONException, IOException, SAXException {
		String cloneLocation = gitResource.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_CLONE);
		WebRequest request = getGetRequest(cloneLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, clonesArray.length());
		return clonesArray.getJSONObject(0);
	}

	// remotes
	protected JSONObject getRemote(String gitRemoteUri, int size, int i, String name) throws IOException, SAXException, JSONException {
		assertRemoteUri(gitRemoteUri);
		WebRequest request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		assertEquals(Remote.TYPE, remotes.getString(ProtocolConstants.KEY_TYPE));
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
		remote = waitForTaskCompletion(response);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		assertNotNull(remote.getString(ProtocolConstants.KEY_LOCATION));
		assertEquals(Remote.TYPE, remote.getString(ProtocolConstants.KEY_TYPE));
		JSONArray refsArray = remote.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(size, refsArray.length());
		JSONObject ref = refsArray.getJSONObject(i);
		assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + name, ref.getString(ProtocolConstants.KEY_FULL_NAME));
		String newRefId = ref.getString(ProtocolConstants.KEY_ID);
		assertTrue(ObjectId.isId(newRefId));
		String remoteBranchLocation = ref.getString(ProtocolConstants.KEY_LOCATION);
		ref.getString(GitConstants.KEY_COMMIT);

		request = GitRemoteTest.getGetGitRemoteRequest(remoteBranchLocation);
		response = webConversation.getResponse(request);
		JSONObject remoteBranch = waitForTaskCompletion(response);
		assertEquals(RemoteBranch.TYPE, remoteBranch.getString(ProtocolConstants.KEY_TYPE));
		assertNotNull(remoteBranch.optString(GitConstants.KEY_COMMIT));
		assertNotNull(remoteBranch.optString(GitConstants.KEY_HEAD));
		assertNotNull(remoteBranch.optString(GitConstants.KEY_DIFF));
		return remoteBranch;
	}

	protected JSONObject merge(String gitHeadUri, String commit, boolean squash) throws JSONException, IOException, SAXException {
		assertCommitUri(gitHeadUri);
		WebRequest request = getPostGitMergeRequest(gitHeadUri, commit, squash);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	protected JSONObject merge(String gitHeadUri, String commit) throws JSONException, IOException, SAXException {
		return merge(gitHeadUri, commit, false);
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
		if (HttpURLConnection.HTTP_ACCEPTED == response.getResponseCode()) {
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
		JSONObject jsonResp = new JSONObject(response.getText());
		int status = jsonResp.has("Severity") ? parseSeverity(jsonResp) : (response.getResponseCode() == HttpURLConnection.HTTP_OK ? IStatus.OK : IStatus.ERROR);
		String messsage = jsonResp.has("Message") ? jsonResp.getString("Message") : null;
		JSONObject jsonData = jsonResp.has("JsonData") ? jsonResp.getJSONObject("JsonData") : jsonResp;
		return new ServerStatus(status, response.getResponseCode(), messsage, jsonData, null);
	}

	private int parseSeverity(JSONObject status) throws JSONException {
		if (!status.has("Severity")) {
			return IStatus.OK;
		}
		if ("Error".equals(status.getString("Severity"))) {
			return IStatus.ERROR;
		}
		if ("Warning".equals(status.getString("Severity"))) {
			return IStatus.WARNING;
		}
		if ("Info".equals(status.getString("Severity"))) {
			return IStatus.INFO;
		}
		return IStatus.OK;
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
		waitForTaskCompletion(webConversation.getResponse(request));

		// get remote (branch) details again
		request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation);
		WebResponse response = webConversation.getResponse(request);
		return waitForTaskCompletion(response);
	}

	/**
	 * Pulls objects and refs for the given repository and merges them into the current branch.
	 * 
	 * @param cloneLocation clone URI
	 * @return status object
	 * @throws JSONException
	 * @throws IOException
	 * @throws SAXException
	 * @throws CoreException
	 */
	protected JSONObject pull(String cloneLocation) throws JSONException, IOException, SAXException, CoreException {
		assertCloneUri(cloneLocation);

		// pull
		WebRequest request = GitPullTest.getPostGitRemoteRequest(cloneLocation, false);
		return waitForTaskCompletion(webConversation.getResponse(request));
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
		assertTagListUri(gitTagUri);
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
		assertTagListUri(gitTagUri);
		WebRequest request = getGetGitTagRequest(gitTagUri);
		WebResponse response = webConversation.getResponse(request);
		JSONObject tags = waitForTaskCompletion(response);
		assertEquals(Tag.TYPE, tags.getString(ProtocolConstants.KEY_TYPE));
		return tags.getJSONArray(ProtocolConstants.KEY_CHILDREN);
	}

	protected void deleteTag(String gitTagUri) throws IOException, SAXException {
		assertTagUri(gitTagUri);
		WebRequest request = getDeleteGitTagRequest(gitTagUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	protected WebResponse checkoutTag(String cloneLocation, String tagName) throws JSONException, IOException, SAXException {
		String requestURI;
		if (cloneLocation.startsWith("http://")) {
			requestURI = cloneLocation;
		} else {
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + cloneLocation;
		}
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_TAG_NAME, tagName);
		// checkout the tag into a new local branch
		// TODO: temporary workaround, JGit fails to checkout a new branch named as the tag
		body.put(GitConstants.KEY_BRANCH_NAME, "tag_" + tagName);
		WebRequest request = new PutMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return webConversation.getResponse(request);
	}

	/**
	 * Return commits for the given URI.
	 * 
	 * @param gitCommitUri commit URI
	 * @return
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	protected JSONArray log(String gitCommitUri) throws IOException, SAXException, JSONException {
		return log(gitCommitUri, null, null, false, false);
	}

	/**
	 * Return commits for the given URI.
	 * 
	 * @param gitCommitUri commit URI
	 * @param page number of page or <code>null</code> if all results
	 * @param pageSize size of page or <code>null</code> if default
	 * @return
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	private JSONObject logObject(String gitCommitUri, Integer page, Integer pageSize) throws IOException, SAXException, JSONException {
		assertCommitUri(gitCommitUri);
		WebRequest request = GitCommitTest.getGetGitCommitRequest(gitCommitUri, false, page, pageSize);
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		switch (response.getResponseCode()) {
			case HttpURLConnection.HTTP_OK :
				assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
				return new JSONObject(response.getText());
			case HttpURLConnection.HTTP_ACCEPTED :
				String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
				assertNotNull(taskLocation);
				assertEquals(taskLocation, new JSONObject(response.getText()).getString(ProtocolConstants.KEY_LOCATION));
				JSONObject logObject = waitForTaskCompletionObject(taskLocation, testUserLogin, testUserPassword);
				assertEquals("Generating git log completed.", logObject.getString("Message"));
				return logObject.getJSONObject("Result").getJSONObject("JsonData");
		}
		fail("Unexpected response code: " + response.getResponseCode());
		return null;
	}

	protected JSONArray log(String gitCommitUri, Integer page, Integer pageSize, boolean prevPage, boolean nextPage) throws IOException, SAXException, JSONException {
		JSONObject logObject = logObject(gitCommitUri, page, pageSize);
		assertEquals(prevPage, logObject.has(ProtocolConstants.KEY_PREVIOUS_LOCATION));
		assertEquals(nextPage, logObject.has(ProtocolConstants.KEY_NEXT_LOCATION));
		// see org.eclipse.orion.server.git.objects.Log.getType()
		assertEquals(Commit.TYPE, logObject.getString(ProtocolConstants.KEY_TYPE));
		return logObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
	}

	protected JSONObject logObject(String gitCommitUri) throws IOException, SAXException, JSONException {
		return logObject(gitCommitUri, null, null);
	}

	protected WebResponse branch(String branchesLocation, String branchName) throws IOException, JSONException, SAXException {
		return branch(branchesLocation, branchName, null);
	}

	// branch
	protected WebResponse branch(String branchesLocation, String branchName, String startPoint) throws IOException, JSONException, SAXException {
		assertBranchUri(branchesLocation);
		WebRequest request = getPostGitBranchRequest(branchesLocation, branchName, startPoint);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		return response;
	}

	static void assertBranchExist(Git git, String branch) throws GitAPIException {
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
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Clone.RESOURCE + cloneLocation;
		}
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_BRANCH_NAME, branchName);
		WebRequest request = new PutMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return webConversation.getResponse(request);
	}

	JSONObject listBranches(final String branchesLocation) throws IOException, SAXException, JSONException {
		WebRequest request = getGetRequest(branchesLocation);
		WebResponse response = webConversation.getResponse(request);
		JSONObject branches = waitForTaskCompletion(response);
		assertEquals(Branch.TYPE, branches.getString(ProtocolConstants.KEY_TYPE));
		return branches;
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
		assertEquals(Remote.RESOURCE, path.segment(1));
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
			assertEquals(Commit.RESOURCE, path.segment(1));
			assertEquals("file", path.segment(3));
		} catch (AssertionError e) {
			error = e;
		}

		// /gitapi/commit/file/{path}
		try {
			assertTrue(path.segmentCount() > 3);
			assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
			assertEquals(Commit.RESOURCE, path.segment(1));
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
		assertEquals(Status.RESOURCE, path.segment(1));
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
			assertEquals(Remote.RESOURCE, path.segment(1));
			assertEquals("file", path.segment(3));
		} catch (AssertionError e) {
			error = e;
		}

		// /git/remote/{remote}/{branch}/file/{path}
		try {
			assertTrue(path.segmentCount() > 5);
			assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
			assertEquals(Remote.RESOURCE, path.segment(1));
			assertEquals("file", path.segment(4));
		} catch (AssertionError e) {
			if (error != null) {
				throw error; // rethrow the first exception
			} // otherwise it's a remote location, ignore this exception
		}
	}

	private static void assertTagListUri(String tagUri) {
		URI uri = URI.create(tagUri);
		IPath path = new Path(uri.getPath());
		// /git/tag/file/{path}
		assertTrue(path.segmentCount() > 3);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(Tag.RESOURCE, path.segment(1));
		assertEquals("file", path.segment(2));
	}

	private static void assertTagUri(String tagUri) {
		URI uri = URI.create(tagUri);
		IPath path = new Path(uri.getPath());
		// /git/tag/{tag}/file/{path}
		assertTrue(path.segmentCount() > 4);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(Tag.RESOURCE, path.segment(1));
		assertEquals("file", path.segment(3));
	}

	static void assertCloneUri(String cloneUri) throws CoreException, IOException {
		URI uri = URI.create(cloneUri);
		IPath path = new Path(uri.getPath());
		// /git/clone/workspace/{id} or /git/clone/file/{id}[/{path}]
		assertTrue(path.segmentCount() > 3);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(Clone.RESOURCE, path.segment(1));
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
		assertEquals(Branch.RESOURCE, path.segment(1));
		assertTrue("file".equals(path.segment(2)) || "file".equals(path.segment(3)));
	}

	protected static void assertConfigUri(String configUri) {
		URI uri = URI.create(configUri);
		IPath path = new Path(uri.getPath());
		// /gitapi/config/[{key}/]clone/file/{id}
		assertTrue(path.segmentCount() > 4);
		assertEquals(GitServlet.GIT_URI.substring(1), path.segment(0));
		assertEquals(ConfigOption.RESOURCE, path.segment(1));
		assertTrue(Clone.RESOURCE.equals(path.segment(2)) || Clone.RESOURCE.equals(path.segment(3)));
		if (Clone.RESOURCE.equals(path.segment(2)))
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
		WebRequest request = new PostGitCloneRequest().setURIish(uri).setWorkspacePath(workspacePath).setFilePath(filePath).setName(name).setKnownHosts(kh).setPassword(p).getWebRequest();
		setAuthentication(request);
		return request;
	}

	private static WebRequest getPostGitMergeRequest(String location, String commit, boolean squash) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Commit.RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_MERGE, commit);
		body.put(GitConstants.KEY_SQUASH, squash);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected static WebRequest getPostGitTagRequest(String location, String tagName, String commitId) throws JSONException, UnsupportedEncodingException {
		assertTagListUri(location);
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Tag.RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_NAME, tagName);
		body.put(GitConstants.KEY_TAG_COMMIT, commitId);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected static WebRequest getPutGitCommitRequest(String location, String tagName) throws UnsupportedEncodingException, JSONException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Commit.RESOURCE + location;
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_NAME, tagName);
		WebRequest request = new PutMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected static WebRequest getGetGitTagRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Tag.RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private static WebRequest getDeleteGitTagRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Commit.RESOURCE + location;
		WebRequest request = new DeleteMethodWebRequest(requestURI);
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
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Branch.RESOURCE + location;
		}
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_NAME, branchName);
		body.put(GitConstants.KEY_BRANCH_NAME, startPoint);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
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
		assertEquals(uri.toString(), result.getJSONObject("JsonData").get("Url"));
		if (uri.getUser() != null)
			assertEquals(uri.getUser(), result.getJSONObject("JsonData").get("User"));
		if (uri.getHost() != null)
			assertEquals(uri.getHost(), result.getJSONObject("JsonData").get("Host"));
		if (uri.getHumanishName() != null)
			assertEquals(uri.getHumanishName(), result.getJSONObject("JsonData").get("HumanishName"));
		if (uri.getPass() != null)
			assertEquals(uri.getPass(), result.getJSONObject("JsonData").get("Password"));
		if (uri.getPort() > 0)
			assertEquals(uri.getPort(), result.getJSONObject("JsonData").get("Port"));
		if (uri.getScheme() != null)
			assertEquals(uri.getScheme(), result.getJSONObject("JsonData").get("Scheme"));
	}

	protected JSONObject getChild(JSONObject folderObject, String childName) throws JSONException, IOException, SAXException {
		String folderChildrenLocation = folderObject.optString(ProtocolConstants.KEY_CHILDREN_LOCATION, null);

		if (folderChildrenLocation == null) {
			// folderObject is not a folder, but a newly created project
			folderObject.getString(ProtocolConstants.KEY_ID);
			String projectContentLocation = folderObject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
			WebRequest request = getGetFilesRequest(projectContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());
			folderChildrenLocation = folder.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		}

		WebRequest request = getGetFilesRequest(folderChildrenLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
		JSONObject child = getChildByName(children, childName);
		return child;
	}

	protected void modifyFile(JSONObject fileObject, String content) throws JSONException, IOException, SAXException {
		String fileLocation = fileObject.getString(ProtocolConstants.KEY_LOCATION);

		WebRequest request = getPutFileRequest(fileLocation, content);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	protected void deleteFile(JSONObject fileObject) throws JSONException, IOException, SAXException {
		String fileLocation = fileObject.getString(ProtocolConstants.KEY_LOCATION);

		WebRequest request = getDeleteFilesRequest(fileLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	protected void addFile(JSONObject... fileObject) throws JSONException, IOException, SAXException {
		WebRequest request = null;
		if (fileObject.length == 1) {
			JSONObject fileGitSection = fileObject[0].getJSONObject(GitConstants.KEY_GIT);
			String fileGitIndexUri = fileGitSection.getString(GitConstants.KEY_INDEX);

			request = GitAddTest.getPutGitIndexRequest(fileGitIndexUri, null);
		} else {
			JSONObject fileGitSection = fileObject[0].getJSONObject(GitConstants.KEY_GIT);
			String gitCloneUri = fileGitSection.getString(GitConstants.KEY_CLONE);
			IPath gitCloneFilePath = new Path(URI.create(gitCloneUri).getPath()).removeFirstSegments(2);
			JSONObject clone = getCloneForGitResource(fileObject[0]);
			String gitIndexUri = clone.getString(GitConstants.KEY_INDEX);
			Set<String> patterns = new HashSet<String>(fileObject.length);
			for (int i = 0; i < fileObject.length; i++) {
				IPath locationPath = new Path(URI.create(fileObject[i].getString(ProtocolConstants.KEY_LOCATION)).getPath());
				patterns.add(locationPath.makeRelativeTo(gitCloneFilePath).toString());
			}

			request = GitAddTest.getPutGitIndexRequest(gitIndexUri, patterns);
		}
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	protected JSONObject commitFile(JSONObject fileObject, String message) throws JSONException, IOException, SAXException {
		WebResponse response = commitFile(fileObject, message, false);
		return new JSONObject(response.getText());
	}

	protected WebResponse commitFile(JSONObject fileObject, String message, boolean amend) throws JSONException, IOException, SAXException {
		JSONObject fileGitSection = fileObject.getJSONObject(GitConstants.KEY_GIT);
		String fileGitHeadUri = fileGitSection.getString(GitConstants.KEY_HEAD);

		WebRequest request = GitCommitTest.getPostGitCommitRequest(fileGitHeadUri, message, amend);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return response;
	}

	protected String getFileContent(JSONObject fileObject) throws JSONException, IOException, SAXException {
		String location = fileObject.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(location);
		WebRequest request = getGetFilesRequest(location);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return response.getText();
	}

	protected String getCommitContent(JSONObject commitObject) throws JSONException, IOException, SAXException {
		String contentLocation = commitObject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);
		WebRequest request = getGetFilesRequest(contentLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return response.getText();
	}

	protected JSONObject assertStatus(StatusResult expected, String statusUri) throws IOException, SAXException, JSONException {
		assertStatusUri(statusUri);
		WebRequest request = GitStatusTest.getGetGitStatusRequest(statusUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject statusResponse = new JSONObject(response.getText());

		JSONArray statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_ADDED);
		assertEquals(expected.getAdded(), statusArray.length());
		if (expected.getAddedNames() != null) {
			for (int i = 0; i < expected.getAddedNames().length; i++) {
				JSONObject child = statusArray.getJSONObject(i);
				assertEquals(expected.getAddedNames()[i], child.getString(ProtocolConstants.KEY_NAME));
				if (expected.getAddedLogLengths() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
					String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
					JSONArray log = log(gitCommitUri);
					assertEquals(expected.getAddedLogLengths()[i], log.length());
				}
			}
		}

		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CHANGED);
		assertEquals(expected.getChanged(), statusArray.length());
		if (expected.getChangedNames() != null) {
			for (int i = 0; i < expected.getChangedNames().length; i++) {
				JSONObject child = statusArray.getJSONObject(i);
				assertEquals(expected.getChangedNames()[i], child.getString(ProtocolConstants.KEY_NAME));
				if (expected.getChangedContents() != null) {
					assertEquals("Invalid file content", expected.getChangedContents()[i], getFileContent(child));
				}
				if (expected.getChangedDiffs() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);
					String[] parts = GitDiffTest.getDiff(gitDiffUri);
					assertEquals("Invalid diff content", expected.getChangedDiffs()[i], parts[1]);
				}
				if (expected.getChangedIndexContents() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
					request = GitIndexTest.getGetGitIndexRequest(gitIndexUri);
					response = webConversation.getResponse(request);
					assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
					assertEquals("Invalid index content", expected.getChangedIndexContents()[i], response.getText());
				}
				if (expected.getChangedHeadContents() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					String commit = gitSection.getString(GitConstants.KEY_COMMIT);
					request = GitCommitTest.getGetGitCommitRequest(commit, true);
					response = webConversation.getResponse(request);
					assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
					assertEquals("Invalid head content", expected.getChangedHeadContents()[i], response.getText());
				}
				if (expected.getChangedLogLengths() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
					String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
					JSONArray log = log(gitCommitUri);
					assertEquals(expected.getChangedLogLengths()[i], log.length());
				}
			}
		}

		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_CONFLICTING);
		assertEquals(expected.getConflicting(), statusArray.length());
		if (expected.getConflictingNames() != null) {
			for (int i = 0; i < expected.getConflictingNames().length; i++) {
				assertEquals(expected.getConflictingNames()[i], statusArray.getJSONObject(i).getString(ProtocolConstants.KEY_NAME));
			}
		}

		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MISSING);
		assertEquals(expected.getMissing(), statusArray.length());
		if (expected.getMissingNames() != null) {
			for (int i = 0; i < expected.getMissingNames().length; i++) {
				JSONObject child = statusArray.getJSONObject(i);
				assertEquals(expected.getMissingNames()[i], child.getString(ProtocolConstants.KEY_NAME));
				if (expected.getMissingLogLengths() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
					String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
					JSONArray log = log(gitCommitUri);
					assertEquals(expected.getMissingLogLengths()[i], log.length());
				}
			}
		}

		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_MODIFIED);
		assertEquals(expected.getModified(), statusArray.length());
		if (expected.getModifiedNames() != null) {
			for (int i = 0; i < expected.getModifiedNames().length; i++) {
				JSONObject child = statusArray.getJSONObject(i);
				assertEquals(expected.getModifiedNames()[i], child.getString(ProtocolConstants.KEY_NAME));
				if (expected.getModifiedContents() != null) {
					assertEquals("Invalid file content", expected.getModifiedContents()[i], getFileContent(child));
				}
				if (expected.getModifiedDiffs() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);
					String[] parts = GitDiffTest.getDiff(gitDiffUri);
					assertEquals("Invalid diff content", expected.getModifiedDiffs()[i], parts[1]);
				}
				if (expected.getModifiedLogLengths() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
					String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
					JSONArray log = log(gitCommitUri);
					assertEquals(expected.getModifiedLogLengths()[i], log.length());
				}
			}
		}
		if (expected.getModifiedPaths() != null) {
			for (int i = 0; i < expected.getModifiedPaths().length; i++) {
				assertEquals(expected.getModifiedPaths()[i], statusArray.getJSONObject(i).getString(ProtocolConstants.KEY_PATH));
			}
		}

		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_REMOVED);
		assertEquals(expected.getRemoved(), statusArray.length());
		if (expected.getRemovedNames() != null) {
			for (int i = 0; i < expected.getRemovedNames().length; i++) {
				JSONObject child = statusArray.getJSONObject(i);
				assertEquals(expected.getRemovedNames()[i], child.getString(ProtocolConstants.KEY_NAME));
				if (expected.getRemovedLogLengths() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
					String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
					JSONArray log = log(gitCommitUri);
					assertEquals(expected.getRemovedLogLengths()[i], log.length());
				}
			}
		}

		statusArray = statusResponse.getJSONArray(GitConstants.KEY_STATUS_UNTRACKED);
		assertEquals(expected.getUntracked(), statusArray.length());
		if (expected.getUntrackedNames() != null) {
			for (int i = 0; i < expected.getUntrackedNames().length; i++) {
				JSONObject child = statusArray.getJSONObject(i);
				assertEquals(expected.getUntrackedNames()[i], child.getString(ProtocolConstants.KEY_NAME));
				if (expected.getUntrackedLogLengths() != null) {
					JSONObject gitSection = child.getJSONObject(GitConstants.KEY_GIT);
					gitSection = statusArray.getJSONObject(0).getJSONObject(GitConstants.KEY_GIT);
					String gitCommitUri = gitSection.getString(GitConstants.KEY_COMMIT);
					JSONArray log = log(gitCommitUri);
					assertEquals(expected.getUntrackedLogLengths()[i], log.length());
				}
			}
		}

		return statusResponse;
	}

	protected JSONArray listClones(String workspaceId, IPath path) throws JSONException, IOException, SAXException {
		WebRequest request = GitCloneTest.listGitClonesRequest(workspaceId, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		assertEquals(Clone.TYPE, clones.getString(ProtocolConstants.KEY_TYPE));
		return clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
	}
}
