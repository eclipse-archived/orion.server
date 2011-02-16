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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringBufferInputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.workspace.WorkspaceServiceTest;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitTest extends FileSystemTest {

	private static final String GIT_SERVLET_LOCATION = GitServlet.GIT_URI + '/';

	WebConversation webConversation;
	private File gitDir;
	private File testFile;

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
	public void tearDown() {
		// see bug 336800
		//		FileSystemHelper.clear(gitDir);
	}

	@Test
	public void testLinkToExistingClone() throws Exception {
		URI workspaceLocation = createWorkspace("testLinkToExistingClone");

		String projectName = "testLinkToExistingClone";
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString("Name"));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);

		String gitStatusUri = project.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);
		String gitDiffUri = project.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);
	}

	@Test
	public void testNoDiff() throws IOException, SAXException, URISyntaxException, JSONException {
		URI workspaceLocation = createWorkspace("testDiff");

		String projectName = "testDiff";
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString("Name"));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);

		String gitDiffUri = project.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);

		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("", response.getText());
	}

	@Test
	public void testDiffAlreadyModified() throws IOException, SAXException, URISyntaxException, JSONException {
		Writer w = new OutputStreamWriter(new FileOutputStream(testFile), "UTF-8");
		try {
			w.write("hello");
		} finally {
			w.close();
		}

		URI workspaceLocation = createWorkspace("testDiff");

		String projectName = "testDiff";
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString("Name"));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);

		String gitDiffUri = project.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);

		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("+hello"/*TODO*/, response.getText());
	}

	@Test
	public void testDiffModifiedByOrion() throws IOException, SAXException, URISyntaxException, JSONException {
		URI workspaceLocation = createWorkspace("testDiff");

		String projectName = "testDiff";
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString("Name"));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);

		// TODO: modify the file

		String gitDiffUri = project.optString(GitConstants.KEY_DIFF, null);
		assertNotNull(gitDiffUri);

		WebRequest request = getGetGitDiffRequest(gitDiffUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("+hello"/*TODO*/, response.getText());
	}

	@Test
	public void testStatus() throws IOException, SAXException, URISyntaxException, JSONException {
		URI workspaceLocation = createWorkspace("testStatus");

		String projectName = "testStatus";
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString("Name"));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);

		String gitStatusUri = project.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);

		// TODO: GET gitStatusUri, verify response - should be empty, nothing to commit
	}

	// TODO: make a change and then call status

	/**
	 * Creates a request to get the diff result for the given location.
	 * @param location Either an absolute URI, or a workspace-relative URI
	 */
	protected WebRequest getGetGitDiffRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.DIFF_COMMAND + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField("Orion-Version", "1");
		setAuthentication(request);
		return request;
	}

	private WebResponse createProjectWithContentLocation(URI workspaceLocation, String projectName, String location) throws JSONException, IOException, SAXException {
		JSONObject body = new JSONObject();
		body.put("ContentLocation", location);
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField("Orion-Version", "1");
		setAuthentication(request);
		return webConversation.getResponse(request);
	}

	private URI createWorkspace(String suffix) throws IOException, SAXException, URISyntaxException {
		String workspaceName = WorkspaceServiceTest.class.getName() + "#" + suffix;
		WebRequest request = getCreateWorkspaceRequest(workspaceName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new URI(response.getHeaderField("Location"));
	}

	private void createRepository() throws IOException, GitAPIException {
		IPath randomLocation = getRandomLocation();
		gitDir = randomLocation.toFile();
		randomLocation = randomLocation.addTrailingSeparator().append(Constants.DOT_GIT);
		File dotGitDir = randomLocation.toFile().getCanonicalFile();
		FileRepository db = new FileRepository(dotGitDir);
		assertFalse(dotGitDir.exists());
		db.create(false /* non bare */);

		testFile = new File(gitDir, "test.txt");
		testFile.createNewFile();
		//		setContent("test.txt", "Hello world");
		Git git = new Git(db);
		git.add().addFilepattern("test.txt").call();
		git.commit().setMessage("Initial commit").call();
	}

	private IPath getRandomLocation() {
		return FileSystemHelper.getRandomLocation(FileSystemHelper.getTempDir());
	}
}
