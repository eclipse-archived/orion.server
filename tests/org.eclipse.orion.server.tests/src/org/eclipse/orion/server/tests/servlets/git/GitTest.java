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
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.workspace.WorkspaceServiceTest;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitTest extends FileSystemTest {

	WebConversation webConversation;
	private File gitDir;

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	@Before
	public void setUp() throws CoreException, IOException {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		gitDir = createBareRepository();
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
	public void testDiff() throws IOException, SAXException, URISyntaxException, JSONException {
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

		// TODO: GET gitDiffUri, verify response - should be empty, no diff
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

	// TODO: make a change and then call diff
	// TODO: make a change and then call status

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

	private File createBareRepository() throws IOException {
		IPath randomLocation = getRandomLocation();
		randomLocation = randomLocation.addFileExtension(Constants.DOT_GIT);
		File dir = randomLocation.toFile().getCanonicalFile();
		FileRepository db = new FileRepository(dir);
		assertFalse(dir.exists());
		db.create(true /* bare */);
		return dir;
	}

	private IPath getRandomLocation() {
		return FileSystemHelper.getRandomLocation(FileSystemHelper.getTempDir());
	}
}
