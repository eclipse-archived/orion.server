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
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
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

	@Test
	public void testSetAndUnsetConfigValues() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject projectTop = createProjectOrLink(workspaceLocation, getMethodName() + "-top", null);
		IPath clonePathTop = new Path("file").append(projectTop.getString(ProtocolConstants.KEY_ID)).makeAbsolute();

		JSONObject projectFolder = createProjectOrLink(workspaceLocation, getMethodName() + "-folder", null);
		IPath clonePathFolder = new Path("file").append(projectFolder.getString(ProtocolConstants.KEY_ID)).append("folder").makeAbsolute();

		IPath[] clonePaths = new IPath[] {clonePathTop, clonePathFolder};

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetFilesRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
			assertNotNull(gitSection);

			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);
			assertNotNull(gitConfigUri);

			// get list of config entries
			request = getGetGitConfigRequest(gitConfigUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject configResponse = new JSONObject(response.getText());
			JSONArray configEntries = configResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			// initial number of config entries
			int initialConfigEntriesCount = configEntries.length();

			// set some dummy value
			final String ENTRY_KEY = "sectionA.subsectionB.nameC";
			final String ENTRY_VALUE = "valueXYZ";

			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			// get list of config entries again
			request = getGetGitConfigRequest(gitConfigUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			configResponse = new JSONObject(response.getText());
			configEntries = configResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(initialConfigEntriesCount + 1, configEntries.length());

			String entryLocation = null;
			for (int i = 0; i < configEntries.length(); i++) {
				JSONObject configEntry = configEntries.getJSONObject(i);
				if (ENTRY_KEY.equals(configEntry.getString(GitConstants.KEY_CONFIG_ENTRY_KEY))) {
					assertEquals(ENTRY_VALUE, configEntry.getString(GitConstants.KEY_CONFIG_ENTRY_VALUE));
					entryLocation = configEntry.getString(ProtocolConstants.KEY_LOCATION);
				}
			}
			assertNotNull(entryLocation);

			// update config entry using POST
			final String NEW_ENTRY_VALUE_1 = "valueABC";

			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, NEW_ENTRY_VALUE_1);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get list of config entries again
			request = getGetGitConfigRequest(gitConfigUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			configResponse = new JSONObject(response.getText());
			configEntries = configResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(initialConfigEntriesCount + 1, configEntries.length());

			entryLocation = null;
			for (int i = 0; i < configEntries.length(); i++) {
				JSONObject configEntry = configEntries.getJSONObject(i);
				if (ENTRY_KEY.equals(configEntry.getString(GitConstants.KEY_CONFIG_ENTRY_KEY))) {
					assertEquals(NEW_ENTRY_VALUE_1, configEntry.getString(GitConstants.KEY_CONFIG_ENTRY_VALUE));
					entryLocation = configEntry.getString(ProtocolConstants.KEY_LOCATION);
				}
			}
			assertNotNull(entryLocation);

			// update config entry using PUT
			final String NEW_ENTRY_VALUE_2 = "valueABCXYZ";

			request = getPutGitConfigRequest(entryLocation, NEW_ENTRY_VALUE_2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get list of config entries again
			request = getGetGitConfigRequest(gitConfigUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			configResponse = new JSONObject(response.getText());
			configEntries = configResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(initialConfigEntriesCount + 1, configEntries.length());

			entryLocation = null;
			for (int i = 0; i < configEntries.length(); i++) {
				JSONObject configEntry = configEntries.getJSONObject(i);
				if (ENTRY_KEY.equals(configEntry.getString(GitConstants.KEY_CONFIG_ENTRY_KEY))) {
					assertEquals(NEW_ENTRY_VALUE_2, configEntry.getString(GitConstants.KEY_CONFIG_ENTRY_VALUE));
					entryLocation = configEntry.getString(ProtocolConstants.KEY_LOCATION);
				}
			}
			assertNotNull(entryLocation);

			// test PUT with invalid entry
			String invalidEntryLocation = entryLocation.replace(ENTRY_KEY, "qwerty.asdfg");
			request = getPutGitConfigRequest(invalidEntryLocation, NEW_ENTRY_VALUE_2);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

			// delete config entry
			request = getDeleteGitConfigRequest(entryLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// get list of config entries again
			request = getGetGitConfigRequest(gitConfigUri);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			configResponse = new JSONObject(response.getText());
			configEntries = configResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(initialConfigEntriesCount, configEntries.length());

			boolean found = false;
			for (int i = 0; i < configEntries.length(); i++) {
				JSONObject configEntry = configEntries.getJSONObject(i);
				if (ENTRY_KEY.equals(configEntry.getString(GitConstants.KEY_CONFIG_ENTRY_KEY)))
					found = true;
			}
			assertEquals(false, found);
		}
	}

	static WebRequest getDeleteGitConfigRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CONFIG_RESOURCE + location;
		WebRequest request = new DeleteMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getPutGitConfigRequest(String location, String value) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + location;
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_CONFIG_ENTRY_VALUE, value);
		WebRequest request = new PutMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getPostGitConfigRequest(String location, String key, String value) throws JSONException, UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CONFIG_RESOURCE + "/" + GitConstants.CLONE_RESOURCE + location;
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_CONFIG_ENTRY_KEY, key);
		body.put(GitConstants.KEY_CONFIG_ENTRY_VALUE, value);
		WebRequest request = new PostMethodWebRequest(requestURI, getJsonAsStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getGetGitConfigRequest(String location) {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.CONFIG_RESOURCE + "/" + GitConstants.CLONE_RESOURCE + location;
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
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
