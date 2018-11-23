/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.objects.ConfigOption;
import org.eclipse.orion.server.tests.ReflectionUtils;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitConfigTest extends GitTest {
	/*
	private static final String GIT_NAME = "test";
	private static final String GIT_MAIL = "test mail";
	private static final String GIT_COMMIT_MESSAGE = "message";

	@Test
	public void testClonedRepoConfigUsingUserProfile() throws Exception {
		// set Git name and mail in the user profile
		WebRequest request = getPutUserRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone a  repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = getClonePath(workspaceId, project);

		String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// check the repository configuration using JGit API
		Git git = new Git(getRepositoryForContentLocation(contentLocation));
		StoredConfig config = git.getRepository().getConfig();
		assertEquals(GIT_NAME, config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME));
		assertEquals(GIT_MAIL, config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL));

		// now check if commits have the right committer set

		request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		String childrenLocation = project.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
		assertNotNull(childrenLocation);

		// check if Git locations are in place
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
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
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, GIT_COMMIT_MESSAGE, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// log
		JSONArray commitsArray = log(gitHeadUri);
		assertEquals(2, commitsArray.length());
		for (int i = 0; i < commitsArray.length(); i++) {
			if (commitsArray.getJSONObject(i).getString(GitConstants.KEY_COMMIT_MESSAGE).equals(GIT_COMMIT_MESSAGE)) {
				assertEquals(GIT_NAME, commitsArray.getJSONObject(i).getString(GitConstants.KEY_AUTHOR_NAME));
				assertEquals(GIT_MAIL, commitsArray.getJSONObject(i).getString(GitConstants.KEY_AUTHOR_EMAIL));
			}
		}
	}

	@Test
	public void testInitializedRepoConfigUsingUserProfile() throws Exception {
		// set Git name and mail in the user profile
		WebRequest request = getPutUserRequest();
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// init a repo
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath initPath = getClonePath(workspaceId, project);

		String contentLocation = init(null, initPath, null).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// check the repository configuration using JGit API
		Git git = new Git(getRepositoryForContentLocation(contentLocation));
		StoredConfig config = git.getRepository().getConfig();
		assertEquals(GIT_NAME, config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME));
		assertEquals(GIT_MAIL, config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL));

		// now check if commits have the right committer set

		request = getGetRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
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
		request = GitCommitTest.getPostGitCommitRequest(gitHeadUri, GIT_COMMIT_MESSAGE, false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// log
		JSONArray commitsArray = log(gitHeadUri);
		assertEquals(2, commitsArray.length());
		for (int i = 0; i < commitsArray.length(); i++) {
			if (commitsArray.getJSONObject(i).getString(GitConstants.KEY_COMMIT_MESSAGE).equals(GIT_COMMIT_MESSAGE)) {
				assertEquals(GIT_NAME, commitsArray.getJSONObject(i).getString(GitConstants.KEY_AUTHOR_NAME));
				assertEquals(GIT_MAIL, commitsArray.getJSONObject(i).getString(GitConstants.KEY_AUTHOR_EMAIL));
			}
		}
	}
	*/
	@Test
	public void testGetListOfConfigEntries() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			JSONObject configResponse = listConfigEntries(gitConfigUri);
			JSONArray configEntries = configResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);

			for (int i = 0; i < configEntries.length(); i++) {
				JSONObject configEntry = configEntries.getJSONObject(i);
				assertNotNull(configEntry.optString(GitConstants.KEY_CONFIG_ENTRY_KEY, null));
				assertNotNull(configEntry.optString(GitConstants.KEY_CONFIG_ENTRY_VALUE, null));
				assertConfigUri(configEntry.getString(ProtocolConstants.KEY_LOCATION));
				assertCloneUri(configEntry.getString(GitConstants.KEY_CLONE));
				assertEquals(ConfigOption.TYPE, configEntry.getString(ProtocolConstants.KEY_TYPE));
			}
		}
	}

	@Test
	public void testAddConfigEntry() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			JSONObject configResponse = listConfigEntries(gitConfigUri);
			JSONArray configEntries = configResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			// initial number of config entries
			int initialConfigEntriesCount = configEntries.length();

			// set some dummy value
			final String ENTRY_KEY = "a.b.c";
			final String ENTRY_VALUE = "v";

			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			configResponse = new JSONObject(response.getText());
			String entryLocation = configResponse.getString(ProtocolConstants.KEY_LOCATION);
			assertConfigUri(entryLocation);

			// get list of config entries again
			configResponse = listConfigEntries(gitConfigUri);
			configEntries = configResponse.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			assertEquals(initialConfigEntriesCount + 1, configEntries.length());

			entryLocation = null;
			for (int i = 0; i < configEntries.length(); i++) {
				JSONObject configEntry = configEntries.getJSONObject(i);
				if (ENTRY_KEY.equals(configEntry.getString(GitConstants.KEY_CONFIG_ENTRY_KEY))) {
					assertMultiConfigOption(configEntry, ENTRY_KEY, new String[] {ENTRY_VALUE});
					break;
				}
			}

			// double check
			org.eclipse.jgit.lib.Config config = getRepositoryForContentLocation(contentLocation).getConfig();
			assertEquals(ENTRY_VALUE, config.getString("a", "b", "c"));
		}
	}

	@Test
	public void testGetSingleConfigEntry() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			// set some dummy value
			final String ENTRY_KEY = "a.b.c";
			final String ENTRY_VALUE = "v";

			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			JSONObject configResponse = new JSONObject(response.getText());
			String entryLocation = configResponse.getString(ProtocolConstants.KEY_LOCATION);

			JSONObject configEntry = listConfigEntries(entryLocation);
			assertMultiConfigOption(configEntry, ENTRY_KEY, new String[] {ENTRY_VALUE});
		}
	}

	@Test
	public void testUpdateConfigEntryUsingPOST() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			// set some dummy value
			final String ENTRY_KEY = "a.b.c";
			final String ENTRY_VALUE = "v";

			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			JSONObject configResponse = new JSONObject(response.getText());
			String entryLocation = configResponse.getString(ProtocolConstants.KEY_LOCATION);

			// update config entry using POST
			final String NEW_ENTRY_VALUE = "valueABC";

			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, NEW_ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			// get value of config entry
			JSONObject configEntry = listConfigEntries(entryLocation);
			// assert unchanged
			assertMultiConfigOption(configEntry, ENTRY_KEY, new String[] {ENTRY_VALUE, NEW_ENTRY_VALUE});
		}
	}

	@Test
	public void testUpdateConfigEntryUsingPUT() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			// set some dummy value
			final String ENTRY_KEY = "a.b.c";
			final String ENTRY_VALUE = "v";

			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			JSONObject configResponse = new JSONObject(response.getText());
			String entryLocation = configResponse.getString(ProtocolConstants.KEY_LOCATION);

			// update config entry using PUT
			final String NEW_ENTRY_VALUE = "v2";

			request = getPutGitConfigRequest(entryLocation, NEW_ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			JSONObject configEntry = listConfigEntries(entryLocation);
			assertMultiConfigOption(configEntry, ENTRY_KEY, new String[] {NEW_ENTRY_VALUE});
		}
	}

	@Test
	public void testDeleteConfigEntry() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			// set some dummy value
			final String ENTRY_KEY = "a.b.c";
			final String ENTRY_VALUE = "v";

			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

			JSONObject configResponse = new JSONObject(response.getText());
			String entryLocation = configResponse.getString(ProtocolConstants.KEY_LOCATION);

			// check if it exists
			request = getGetGitConfigRequest(entryLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// delete config entry
			request = getDeleteGitConfigRequest(entryLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			// it shouldn't exist
			request = getGetGitConfigRequest(entryLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

			// so next delete operation should fail
			request = getDeleteGitConfigRequest(entryLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
		}
	}

	@Test
	public void testCreateInvalidConfigEntry() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			final String INVALID_ENTRY_KEY = "a"; // no name specified, dot missing
			final String ENTRY_VALUE = "v";

			// try to set entry with invalid key
			request = getPostGitConfigRequest(gitConfigUri, INVALID_ENTRY_KEY, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
		}
	}

	@Test
	public void testUpdateNonExistingConfigEntryUsingPUT() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			final String ENTRY_KEY = "a.b.c";
			final String ENTRY_VALUE = "v";

			String invalidEntryLocation = gitConfigUri.replace(ConfigOption.RESOURCE, ConfigOption.RESOURCE + "/" + ENTRY_KEY);

			// check if it doesn't exist
			request = getGetGitConfigRequest(invalidEntryLocation);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

			// try to update non-existing config entry using PUT (not allowed)
			request = getPutGitConfigRequest(invalidEntryLocation, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
		}
	}

	@Test
	public void testRequestWithMissingArguments() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			String contentLocation = clone(clonePath).getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project metadata
			WebRequest request = getGetRequest(contentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject project = new JSONObject(response.getText());
			JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
			String gitConfigUri = gitSection.getString(GitConstants.KEY_CONFIG);

			final String ENTRY_KEY = "a.b.c";
			final String ENTRY_VALUE = "v";

			// missing key
			request = getPostGitConfigRequest(gitConfigUri, null, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

			// missing value
			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

			// missing key and value
			request = getPostGitConfigRequest(gitConfigUri, null, null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

			// add some config
			request = getPostGitConfigRequest(gitConfigUri, ENTRY_KEY, ENTRY_VALUE);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			JSONObject configResponse = new JSONObject(response.getText());
			String entryLocation = configResponse.getString(ProtocolConstants.KEY_LOCATION);

			// put without value
			request = getPutGitConfigRequest(entryLocation, null);
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
		}
	}

	@Test
	public void testGetConfigEntryForNonExistingRepository() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = getWorkspaceId(workspaceLocation);

		JSONArray clonesArray = listClones(workspaceId, null);

		String dummyId = "dummyId";
		GitCloneTest.ensureCloneIdDoesntExist(clonesArray, dummyId);
		String entryLocation = SERVER_LOCATION + GIT_SERVLET_LOCATION + ConfigOption.RESOURCE + "/dummyKey/" + Clone.RESOURCE + "/file/" + dummyId;

		// get value of config entry
		WebRequest request = getGetGitConfigRequest(entryLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getResponseMessage(), HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testKeyToSegmentsMethod() throws Exception {
		Object configOption = ReflectionUtils.callConstructor(ConfigOption.class, new Object[] {new URI(""), FileRepositoryBuilder.create(new File(""))});

		String[] segments = (String[]) ReflectionUtils.callMethod(configOption, "keyToSegments", new Object[] {"a.b.c"});
		assertArrayEquals(new String[] {"a", "b", "c"}, segments);

		segments = (String[]) ReflectionUtils.callMethod(configOption, "keyToSegments", new Object[] {"a.c"});
		assertArrayEquals(new String[] {"a", null, "c"}, segments);

		segments = (String[]) ReflectionUtils.callMethod(configOption, "keyToSegments", new Object[] {"a.b.c.d"});
		assertArrayEquals(new String[] {"a", "b.c", "d"}, segments);

		segments = (String[]) ReflectionUtils.callMethod(configOption, "keyToSegments", new Object[] {"a"});
		assertArrayEquals(null, segments);
	}

	@Test
	public void testSegmentsToKeyMethod() throws Exception {
		Object configOption = ReflectionUtils.callConstructor(ConfigOption.class, new Object[] {new URI(""), FileRepositoryBuilder.create(new File(""))});

		String key = (String) ReflectionUtils.callMethod(configOption, "segmentsToKey", new Object[] {new String[] {"a", "b", "c"}});
		assertEquals("a.b.c", key);

		key = (String) ReflectionUtils.callMethod(configOption, "segmentsToKey", new Object[] {new String[] {"a", null, "c"}});
		assertEquals("a.c", key);

		key = (String) ReflectionUtils.callMethod(configOption, "segmentsToKey", new Object[] {new String[] {"a", "b.c", "d"}});
		assertEquals("a.b.c.d", key);

		key = (String) ReflectionUtils.callMethod(configOption, "segmentsToKey", new Object[] {new String[] {"a", "b"}});
		assertEquals(null, key);

		key = (String) ReflectionUtils.callMethod(configOption, "segmentsToKey", new Object[] {new String[] {"a", "b", "c", "d"}});
		assertEquals(null, key);
	}

	static WebRequest getDeleteGitConfigRequest(String location) {
		String requestURI = toAbsoluteURI(location);
		WebRequest request = new DeleteMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getPutGitConfigRequest(String location, String value) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		JSONArray array = new JSONArray();
		array.put(value);
		body.put(GitConstants.KEY_CONFIG_ENTRY_VALUE, array);
		WebRequest request = new PutMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getPostGitConfigRequest(String location, String key, String value) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_CONFIG_ENTRY_KEY, key);
		body.put(GitConstants.KEY_CONFIG_ENTRY_VALUE, value);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private JSONObject listConfigEntries(final String gitConfigUri) throws IOException, SAXException, JSONException {
		WebRequest request = getGetGitConfigRequest(gitConfigUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject configResponse = new JSONObject(response.getText());
		assertEquals(ConfigOption.TYPE, configResponse.getString(ProtocolConstants.KEY_TYPE));
		return configResponse;
	}

	static WebRequest getGetGitConfigRequest(String location) {
		String requestURI = toAbsoluteURI(location);
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	private void assertMultiConfigOption(final JSONObject cfg, final String k, final String[] v) throws JSONException, CoreException, IOException {
		assertEquals(k, cfg.getString(GitConstants.KEY_CONFIG_ENTRY_KEY));

		ArrayList<String> list = new ArrayList<String>();
		JSONArray jsonArray = cfg.getJSONArray(GitConstants.KEY_CONFIG_ENTRY_VALUE);
		if (jsonArray != null) {
			for (int i = 0; i < jsonArray.length(); i++) {
				list.add(jsonArray.get(i).toString());
			}
		}
		String[] compare = new String[list.size()];
		compare = list.toArray(compare);
		assertArrayEquals(v, compare);
		assertConfigUri(cfg.getString(ProtocolConstants.KEY_LOCATION));
		assertCloneUri(cfg.getString(GitConstants.KEY_CLONE));
	}
}
