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

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitConfigTest extends GitTest {

	private static final String GIT_NAME = "test";
	private static final String GIT_MAIL = "test mail";
	private static final String GIT_COMMIT_MESSAGE = "message";

	@Test
	public void testConfigUsingUserProfile() throws Exception {

		// set Git name and mail in the user profile
		WebRequest request = getPutUserRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone a  repo
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// check the repository configuration using JGit API
		Git git = new Git(getRepositoryForContentLocation(contentLocation));
		StoredConfig config = git.getRepository().getConfig();
		assertEquals(GIT_NAME, config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME));
		assertEquals(GIT_MAIL, config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL));

		// now check if commits have the right committer set

		request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// check if Git locations are in place
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitIndexUri = gitSection.getString(GitConstants.KEY_INDEX);
		String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

		// modify
		String projectLocation = project.getString(ProtocolConstants.KEY_LOCATION);
		request = getPutFileRequest(projectLocation + "/test.txt", "change to commit");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri + "test.txt");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// commit all
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri /* all */, GIT_COMMIT_MESSAGE, false);
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
	private WebRequest getPutUserRequest() throws JSONException, UnsupportedEncodingException {
		String requestURI = SERVER_LOCATION + "/users/test";
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_NAME, GIT_NAME);
		body.put(GitConstants.KEY_MAIL, GIT_MAIL);
		WebRequest request = new PutMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
