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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.meterware.httpunit.*;
import java.io.*;
import java.net.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

public class GitConfigTest extends GitTest {

	private static final String GIT_NAME = "test";
	private static final String GIT_MAIL = "test mail";
	private static final String GIT_COMMIT_MESSAGE = "message";

	@Test
	public void testConfigUsingUserProfile() throws JSONException, IOException, SAXException, URISyntaxException, NoHeadException, JGitInternalException, ConfigInvalidException {

		// set Git name and mail in the user profile
		WebRequest request = getPutUserRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone a  repo
		URIish uri = new URIish(gitDir.toURL());
		String name = null;
		request = GitCloneTest.getPostGitCloneRequest(uri, name);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForCloneCompletion(taskLocation);

		//validate the clone metadata
		response = webConversation.getResponse(getCloneRequest(cloneLocation));
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);

		// check the repository configuration using JGit API
		File file = new File(URIUtil.toFile(new URI(contentLocation)), Constants.DOT_GIT);
		Git git = new Git(new FileRepository(file));
		assertTrue(file.exists());
		assertTrue(RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED));
		StoredConfig config = git.getRepository().getConfig();
		assertEquals(GIT_NAME, config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME));
		assertEquals(GIT_MAIL, config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL));

		// now check if commits have the right committer set

		// link a project to the cloned repo
		URI workspaceLocation = createWorkspace(getMethodName());
		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// POST http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject newProject = new JSONObject(response.getText());
		String projectContentLocation = newProject.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(projectContentLocation);

		// GET http://<host>/file/<projectId>/
		request = getGetFilesRequest(projectContentLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// check if Git locations are in place
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri);
		String gitCommitUri = gitSection.optString(GitConstants.KEY_COMMIT, null);
		assertNotNull(gitCommitUri);

		// modify
		String projectId = project.optString(ProtocolConstants.KEY_LOCATION, null);
		assertNotNull(projectId);
		request = getPutFileRequest(projectId + "/test.txt", "change to commit");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri /* all */, GIT_COMMIT_MESSAGE, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// log
		// TODO: replace with RESTful API for git log when available
		Iterable<RevCommit> commits = git.log().call();
		PersonIdent testIdent = new PersonIdent(GIT_NAME, GIT_MAIL);
		PersonIdent[] expectedIdents = new PersonIdent[] {testIdent};
		int c = 0;
		for (RevCommit commit : commits) {
			if (commit.getFullMessage().equals(GIT_COMMIT_MESSAGE)) {
				assertEquals(expectedIdents[expectedIdents.length - 1 - c].getName(), commit.getCommitterIdent().getName());
				assertEquals(expectedIdents[expectedIdents.length - 1 - c].getEmailAddress(), commit.getCommitterIdent().getEmailAddress());
			}
			c++;
		}
		assertEquals(2, c);
	}

	// TODO: should be moved to User tests as a static method
	private WebRequest getPutUserRequest() throws JSONException {
		String requestURI = SERVER_LOCATION + "/users/test";
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_NAME, GIT_NAME);
		body.put(GitConstants.KEY_MAIL, GIT_MAIL);
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PutMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
