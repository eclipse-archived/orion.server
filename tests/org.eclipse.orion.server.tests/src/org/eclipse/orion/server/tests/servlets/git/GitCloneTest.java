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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.URIUtil;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitConstants;
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
	public void testGetCloneEmpty() throws IOException, SAXException, JSONException {
		WebRequest request = listGitClonesRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject clones = new JSONObject(response.getText());
		JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(0, clonesArray.length());
	}

	@Test
	public void testGetClone() throws IOException, SAXException, JSONException {
		List<String> locations = new ArrayList<String>();

		// 1st clone
		String contentLocation = clone(null);
		locations.add(contentLocation);

		// 2nd clone
		contentLocation = clone(null);
		locations.add(contentLocation);

		// 3rd clone
		contentLocation = clone(null);
		locations.add(contentLocation);

		// get clones list
		WebRequest request = listGitClonesRequest();
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
		WebRequest request = getPostGitCloneRequest("", null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCloneBadUrl() throws IOException, SAXException, JSONException {
		WebRequest request = getPostGitCloneRequest("I'm//bad!", null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testClone() throws IOException, SAXException, JSONException, URISyntaxException {
		String contentLocation = clone(null);

		File file = URIUtil.toFile(new URI(contentLocation));
		assertTrue(file.exists());
		assertTrue(file.isDirectory());
		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED));
	}

	@Test
	public void testCloneAndLink() throws IOException, SAXException, JSONException, URISyntaxException {
		String contentLocation = clone(null);

		URI workspaceLocation = createWorkspace(getMethodName());

		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
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
		JSONObject newProject = new JSONObject(response.getText());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(projectContentLocation);

		// http://<host>/file/<projectId>/
		request = getGetFilesRequest(projectContentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
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
	public void testCloneAndLinkToFolder() throws IOException, SAXException, JSONException, URISyntaxException {
		String contentLocation = clone(null);

		File file = URIUtil.toFile(new URI(contentLocation));
		File folder = new File(file, "folder");

		URI workspaceLocation = createWorkspace(getMethodName());

		ServletTestingSupport.allowedPrefixes = contentLocation.toString();
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, folder.toURI().toString());
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject newProject = new JSONObject(response.getText());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(projectContentLocation);

		// http://<host>/file/<projectId>/
		request = getGetFilesRequest(projectContentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
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
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode("org.eclipse.orion.server.configurator");
		preferences.put("orion.project.defaultSCM", "git");
		// XXX: see org.eclipse.orion.internal.server.servlets.workspace.WorkspaceResourceHandler.generateProjectLocation(WebProject, String)
		preferences.put(Activator.PROP_FILE_LAYOUT, "usertree");

		String contentLocation = new File(gitDir, "folder").getAbsolutePath();

		URI workspaceLocation = createWorkspace(getMethodName());

		ServletTestingSupport.allowedPrefixes = contentLocation.toString();
		String projectName = getMethodName();
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
		JSONObject newProject = new JSONObject(response.getText());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(projectContentLocation);

		// http://<host>/file/<projectId>/
		request = getGetFilesRequest(projectContentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// http://<host>/file/<projectId>/?depth=1
		request = getGetFilesRequest(childrenLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
		String[] expectedChildren = new String[] {"folder.txt"};
		assertEquals("Wrong number of directory children", expectedChildren.length, children.size());
		assertEquals(expectedChildren[0], children.get(0).getString(ProtocolConstants.KEY_NAME));

		// TODO: clean up, should be done in finally block
		preferences.remove("orion.project.defaultSCM");
		preferences.remove(Activator.PROP_FILE_LAYOUT);
	}

	private static String sshRepo;
	private static String sshRepo2;
	private static char[] password;
	private static String knownHosts;
	private static byte[] privateKey;
	private static byte[] publicKey;
	private static byte[] passphrase;

	@BeforeClass
	public static void readSshProperties() {
		String propertiesFile = System.getProperty("orion.tests.ssh");
		// if (propertiesFile == null) return;
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
			privateKey = loadFileContents(properties.get("privateKeyPath")).getBytes();
			publicKey = loadFileContents(properties.get("publicKeyPath")).getBytes();
			passphrase = properties.get("passphrase").getBytes();
		} catch (Exception e) {
			System.err.println("Could not read ssh properties file: " + propertiesFile);
		}
	}

	@Test
	public void testCloneOverSshWithNoKnownHosts() throws IOException, SAXException, JSONException, URISyntaxException {
		Assume.assumeTrue(sshRepo != null);

		URIish uri = new URIish(sshRepo);
		String name = null;
		WebRequest request = getPostGitCloneRequest(uri, name, null, null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
	}

	@Test
	public void testCloneOverSshWithNoPassword() throws IOException, SAXException, JSONException, URISyntaxException {
		Assume.assumeTrue(sshRepo != null);
		Assume.assumeTrue(knownHosts != null);

		URIish uri = new URIish(sshRepo);
		String name = null;
		WebRequest request = getPostGitCloneRequest(uri, name, knownHosts, (char[]) null);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
	}

	@Test
	public void testCloneOverSshWithBadPassword() throws IOException, SAXException, JSONException, URISyntaxException {
		Assume.assumeTrue(sshRepo != null);
		Assume.assumeTrue(knownHosts != null);

		URIish uri = new URIish(sshRepo);
		String name = null;
		WebRequest request = getPostGitCloneRequest(uri, name, knownHosts, "I'm bad".toCharArray());
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
		String name = null;
		WebRequest request = getPostGitCloneRequest(uri, name, knownHosts, privateKey, publicKey, passphrase);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(location);
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);

		File file = URIUtil.toFile(new URI(contentLocation));
		assertTrue(file.exists());
		assertTrue(file.isDirectory());
		assertTrue(RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED));
	}

	@Test
	@Ignore("see bug 336216")
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
		InputStream in = new StringBufferInputStream(body.toString());
		// http://localhost:8080/workspace/{workspaceId}
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
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
	public void testGetCloneAndPull() throws IOException, SAXException, JSONException, URISyntaxException, WrongRepositoryStateException, InvalidConfigurationException, DetachedHeadException, InvalidRemoteException, CanceledException, RefNotFoundException {
		// see bug 339254
		String contentLocation = clone(null);

		// TODO: add assertion on clones number once bug 340553 is fixed and we're able to clean clones after each test
		// request = listGitClonesRequest();
		// response = webConversation.getResponse(request);
		// assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		// JSONObject clones = new JSONObject(response.getText());
		// JSONArray clonesArray = clones.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		// assertEquals(1, clonesArray.length());
		// String contentLocation = clonesArray.getJSONObject(0).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		Git git = new Git(getRepositoryForContentLocation(contentLocation));
		// TODO: replace with RESTful API when ready, see bug 339114
		PullResult pullResult = git.pull().call();
		assertEquals(pullResult.getMergeResult().getMergeStatus(), MergeStatus.ALREADY_UP_TO_DATE);
		assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
	}

	private WebRequest getPostGitCloneRequest(String uri, String name, String kh, byte[] privk, byte[] pubk, byte[] p) throws JSONException {
		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CLONE_RESOURCE + '/';
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_URL, uri);
		body.put(ProtocolConstants.KEY_NAME, name);
		if (kh != null)
			body.put(GitConstants.KEY_KNOWN_HOSTS, kh);
		if (privk != null)
			body.put(GitConstants.KEY_PRIVATE_KEY, new String(privk));
		if (pubk != null)
			body.put(GitConstants.KEY_PUBLIC_KEY, new String(pubk));
		if (p != null)
			body.put(GitConstants.KEY_PASSPHRASE, new String(p));
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private WebRequest listGitClonesRequest() {
		String requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CLONE_RESOURCE + '/';
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	// Convenience methods for creating requests

	private WebRequest getPostGitCloneRequest(String uri, String name) throws JSONException {
		return getPostGitCloneRequest(uri, name, null, null);
	}

	private WebRequest getPostGitCloneRequest(URIish uri, String name, String kh, char[] p) throws JSONException {
		return getPostGitCloneRequest(uri.toString(), name, kh, p);
	}

	private WebRequest getPostGitCloneRequest(URIish uri, String name, String kh, byte[] privk, byte[] pubk, byte[] p) throws JSONException {
		return getPostGitCloneRequest(uri.toString(), name, kh, privk, pubk, p);
	}

	// Utility methods

	private static String loadFileContents(String path) throws IOException {
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
}
