/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreMigration;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.MetadataInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Abstract class for tests to ensure that the older versions of the Metadata storage are 
 * automatically updated to the latest version. Abstract class shared between concurrency
 * tests and functional tests.
 * 
 * @author Anthony Hunter
 */
public class AbstractSimpleMetaStoreMigrationTests extends FileSystemTest {

	/**
	 * type-safe value for an empty list.
	 */
	protected static final List<String> EMPTY_LIST = Collections.emptyList();

	private final static String TIMESTAMP = new Long(System.currentTimeMillis()).toString();
	private final static String CONFIRMATION_ID = System.currentTimeMillis() + "-" + Math.random();

	@BeforeClass
	public static void initializeRootFileStorePrefixLocation() {
		initializeWorkspaceLocation();
	}

	protected JSONObject createProjectJson(int version, String userId, String workspaceName, String projectName, File contentLocation) throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(SimpleMetaStore.ORION_VERSION, version);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		jsonObject.put("WorkspaceId", workspaceId);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		jsonObject.put(MetadataInfo.UNIQUE_ID, projectId);
		jsonObject.put(UserConstants.FULL_NAME, projectName);
		String encodedContentLocation = SimpleMetaStoreUtil.encodeProjectContentLocation(contentLocation.toURI().toString());
		jsonObject.put("ContentLocation", encodedContentLocation);
		JSONObject properties = new JSONObject();
		jsonObject.put("Properties", properties);
		return jsonObject;
	}

	protected void createProjectMetaData(int version, JSONObject newProjectJSON, String userId, String workspaceName, String projectName) throws Exception {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		assertNotNull(workspaceId);
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		assertNotNull(encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName));
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, projectId));
		assertFalse(SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId));
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(workspaceMetaFolder, projectId));
		File projectFolder = SimpleMetaStoreUtil.readMetaFolder(workspaceMetaFolder, projectId);
		assertNotNull(projectFolder);
		assertTrue(projectFolder.exists());
		assertTrue(projectFolder.isDirectory());
		File projectMetaFile;
		if (version == SimpleMetaStoreMigration.VERSION4) {
			// the project metadata is saved in a file in the workspace folder.
			assertTrue(SimpleMetaStoreUtil.createMetaFile(workspaceMetaFolder, projectId, newProjectJSON));
			projectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(workspaceMetaFolder, projectId);
		} else {
			// the project metadata is saved in a file in the user folder.
			assertTrue(SimpleMetaStoreUtil.createMetaFile(userMetaFolder, projectId, newProjectJSON));
			projectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, projectId);
		}
		assertTrue(projectMetaFile.exists());
		assertTrue(projectMetaFile.isFile());

		// Update the JUnit base variables
		testProjectBaseLocation = "/" + workspaceId + '/' + projectName;
		testProjectLocalFileLocation = "/" + projectId;
	}

	/**
	 * Creates the sample directory in the local directory using the IFileStore API.
	 * @param directoryPath
	 * @param fileName
	 * @throws Exception
	 */
	protected String createSampleDirectory() throws Exception {
		assertFalse("Test Project Base Location should not be the empty string, user or workspace or project failure", "".equals(getTestBaseResourceURILocation()));
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		return directoryPath;
	}

	/**
	 * Creates the sample file in the local directory using the IFileStore API.
	 * @param directoryPath
	 * @param fileName
	 * @throws Exception
	 */
	protected String createSampleFile(String directoryPath) throws Exception {
		String fileName = "sampleFile" + System.currentTimeMillis() + ".txt";
		String fileContent = fileName;
		createFile(directoryPath + "/" + fileName, fileContent);
		return fileName;
	}

	/**
	 * Create the content for a user.json file to be saved to the disk
	 * @param version The SimpleMetaStore version.
	 * @param userId The userId
	 * @param workspaceIds A list of workspace Ids.
	 * @return The JSON object.
	 * @throws Exception
	 */
	protected JSONObject createUserJson(int version, String userId, List<String> workspaceIds) throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(SimpleMetaStore.ORION_VERSION, version);
		jsonObject.put(MetadataInfo.UNIQUE_ID, userId);
		jsonObject.put(UserConstants.USER_NAME, userId);
		jsonObject.put(UserConstants.FULL_NAME, userId);
		String password = SimpleUserPasswordUtil.encryptPassword(userId);
		JSONObject properties = new JSONObject();
		if (version == SimpleMetaStoreMigration.VERSION4 || version == SimpleMetaStoreMigration.VERSION6 || version == SimpleMetaStoreMigration.VERSION7) {
			jsonObject.put("password", password);
		} else {
			properties.put(UserConstants.PASSWORD, password);
		}
		properties.put("UserRightsVersion", "3");
		JSONArray userRights = new JSONArray();
		JSONObject userRight = new JSONObject();
		userRight.put("Method", 15);
		String usersRight = "/users/";
		userRight.put("Uri", usersRight.concat(userId));
		userRights.put(userRight);
		JSONArray workspaceIdsJson = new JSONArray();
		for (String workspaceId : workspaceIds) {
			workspaceIdsJson.put(workspaceId);

			userRight = new JSONObject();
			userRight.put("Method", 15);
			String workspaceRight = "/workspace/";
			userRight.put("Uri", workspaceRight.concat(workspaceId));
			userRights.put(userRight);

			userRight = new JSONObject();
			userRight.put("Method", 15);
			userRight.put("Uri", workspaceRight.concat(workspaceId).concat("/*"));
			userRights.put(userRight);

			userRight = new JSONObject();
			userRight.put("Method", 15);
			String fileRight = "/file/";
			userRight.put("Uri", fileRight.concat(workspaceId));
			userRights.put(userRight);

			userRight = new JSONObject();
			userRight.put("Method", 15);
			userRight.put("Uri", fileRight.concat(workspaceId).concat("/*"));
			userRights.put(userRight);
		}
		jsonObject.put("WorkspaceIds", workspaceIdsJson);
		properties.put("UserRights", userRights);
		String email = userId + "@example.com";
		if (version == SimpleMetaStoreMigration.VERSION4 || version == SimpleMetaStoreMigration.VERSION6 || version == SimpleMetaStoreMigration.VERSION7) {
			jsonObject.put("blocked", "true");
			jsonObject.put("diskusage", "74M");
			jsonObject.put("diskusagetimestamp", TIMESTAMP);
			jsonObject.put("email", email);
			jsonObject.put("email_confirmation", CONFIRMATION_ID);
			jsonObject.put("lastlogintimestamp", TIMESTAMP);
			JSONObject profileProperties = new JSONObject();
			profileProperties.put("oauth", "https://api.github.com/users/ahunter-orion/4500523");
			profileProperties.put("openid", "https://www.google.com/accounts/o8/id?id=AItOawkTs8dYMgHG0tlvW8PE7RmNZwDlOWlWIU8");
			profileProperties.put("passwordResetId", CONFIRMATION_ID);
			jsonObject.put("profileProperties", profileProperties);
			jsonObject.put("GitName", userId);
			jsonObject.put("GitMail", email);
		} else {
			properties.put(UserConstants.BLOCKED, "true");
			properties.put(UserConstants.DISK_USAGE, "74M");
			properties.put(UserConstants.DISK_USAGE_TIMESTAMP, TIMESTAMP);
			properties.put(UserConstants.EMAIL, email);
			properties.put(UserConstants.EMAIL_CONFIRMATION_ID, CONFIRMATION_ID);
			properties.put(UserConstants.LAST_LOGIN_TIMESTAMP, TIMESTAMP);
			properties.put(UserConstants.OAUTH, "https://api.github.com/users/ahunter-orion/4500523");
			properties.put(UserConstants.OPENID, "https://www.google.com/accounts/o8/id?id=AItOawkTs8dYMgHG0tlvW8PE7RmNZwDlOWlWIU8");
			properties.put(UserConstants.PASSWORD_RESET_ID, CONFIRMATION_ID);
			properties.put("git/config/userInfo", "{\"GitName\":\"" + userId + "\",\"GitMail\":\"" + email + "\"}");
		}
		jsonObject.put("Properties", properties);
		return jsonObject;
	}

	protected void createUserMetaData(JSONObject newUserJSON, String userId) throws CoreException {
		SimpleMetaStoreUtil.createMetaUserFolder(getWorkspaceRoot(), userId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		assertTrue(userMetaFolder.exists());
		assertTrue(userMetaFolder.isDirectory());
		assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		SimpleMetaStoreUtil.createMetaFile(userMetaFolder, SimpleMetaStore.USER, newUserJSON);
		File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, SimpleMetaStore.USER);
		assertTrue(userMetaFile.exists());
		assertTrue(userMetaFile.isFile());
	}

	/**
	 * Create the content for a user.json file to be saved to the disk
	 * @param version The SimpleMetaStore version.
	 * @param userId The userId
	 * @param workspaceIds A list of workspace Ids.
	 * @return The JSON object.
	 * @throws Exception
	 */
	protected JSONObject createWorkspaceJson(int version, String userId, String workspaceName, List<String> projectNames) throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(SimpleMetaStore.ORION_VERSION, version);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		jsonObject.put(MetadataInfo.UNIQUE_ID, workspaceId);
		jsonObject.put("UserId", userId);
		jsonObject.put(UserConstants.FULL_NAME, workspaceName);
		JSONArray projectNamesJson = new JSONArray();
		for (String projectName : projectNames) {
			projectNamesJson.put(projectName);
		}
		jsonObject.put("ProjectNames", projectNamesJson);
		JSONObject properties = new JSONObject();
		jsonObject.put("Properties", properties);
		return jsonObject;
	}

	protected void createWorkspaceMetaData(int version, JSONObject newWorkspaceJSON, String userId, String workspaceName) throws CoreException {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		assertNotNull(workspaceId);
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		assertNotNull(encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(userMetaFolder, encodedWorkspaceName));
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		assertNotNull(workspaceMetaFolder);
		File workspaceMetaFile;
		if (version == SimpleMetaStoreMigration.VERSION4) {
			// the workspace metadata is saved in a file named workspace.json in the workspace folder.
			assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.WORKSPACE));
			assertTrue(SimpleMetaStoreUtil.createMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE, newWorkspaceJSON));
			workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE);
			assertNotNull(workspaceMetaFile);
		} else {
			// the workspace metadata is saved in a file named {workspaceid}.json in the user folder.
			assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, workspaceId));
			assertTrue(SimpleMetaStoreUtil.createMetaFile(userMetaFolder, workspaceId, newWorkspaceJSON));
			workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, workspaceId);
			assertNotNull(workspaceMetaFile);
		}
		assertTrue(workspaceMetaFile.exists());
		assertTrue(workspaceMetaFile.isFile());
	}

	protected File getProjectDefaultContentLocation(String userId, String workspaceName, String projectName) throws CoreException {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, encodedWorkspaceName);
		assertEquals(workspaceMetaFolder.getParent(), userMetaFolder.toString());
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		File projectFolder = SimpleMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectId);
		assertNotNull(projectFolder);
		assertFalse(projectFolder.exists());
		assertEquals(projectFolder.getParent(), workspaceMetaFolder.toString());
		return projectFolder;
	}

	protected void verifyProjectJson(JSONObject jsonObject, String userId, String workspaceId, String projectName, File contentLocation) throws Exception {
		assertTrue(jsonObject.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStore.VERSION, jsonObject.getInt(SimpleMetaStore.ORION_VERSION));
		assertTrue(jsonObject.has(MetadataInfo.UNIQUE_ID));
		assertEquals(projectName, jsonObject.getString(MetadataInfo.UNIQUE_ID));
		assertTrue(jsonObject.has("WorkspaceId"));
		assertEquals(workspaceId, jsonObject.getString("WorkspaceId"));
		assertTrue(jsonObject.has(UserConstants.FULL_NAME));
		assertEquals(projectName, jsonObject.getString(UserConstants.FULL_NAME));
		assertTrue(jsonObject.has("Properties"));
		assertTrue(jsonObject.has("ContentLocation"));
		String contentLocationFromJson = jsonObject.getString("ContentLocation");
		assertTrue(contentLocationFromJson.startsWith(SimpleMetaStoreUtil.SERVERWORKSPACE));
		String decodedContentLocationFromJson = SimpleMetaStoreUtil.decodeProjectContentLocation(contentLocationFromJson);
		URI contentLocationURIFromJson = new URI(decodedContentLocationFromJson);
		assertEquals(SimpleMetaStoreUtil.FILE_SCHEMA, contentLocationURIFromJson.getScheme());
		File contentLocationFileFromJson = new File(contentLocationURIFromJson);
		assertEquals(contentLocation.toString(), contentLocationFileFromJson.getPath());
	}

	protected void verifyProjectMetaData(String userId, String workspaceName, String projectName) throws Exception {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		assertNotNull(workspaceId);
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		assertNotNull(encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName));
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, projectId));
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId));
		File projectFolder = SimpleMetaStoreUtil.readMetaFolder(workspaceMetaFolder, projectId);
		assertTrue(projectFolder.exists());
		assertTrue(projectFolder.isDirectory());
		JSONObject projectJson = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, projectId);
		verifyProjectJson(projectJson, userId, workspaceId, projectName, projectFolder);
	}

	/**
	 * Verifies the test user has the specified number of workspaces.
	 * @param workspaces Number of workspaces.
	 */
	protected void verifyProjectRequest(String userId, String workspaceName, String projectName) throws Exception {
		//now get the project metadata
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		String encodedProjectId = URLEncoder.encode(projectId, "UTF-8").replace("+", "%20");
		String projectLocation = "workspace/" + workspaceId + "/project/" + encodedProjectId;
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + projectLocation);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(sourceContentLocation);
		assertEquals(projectName, responseObject.optString(ProtocolConstants.KEY_NAME));
	}

	/**
	 * Verifies the sample content using the remote Orion API. Verifies the user has been
	 * migrated successfully and all is good with the account.
	 * @param directoryPath
	 * @param fileName
	 * @throws Exception
	 */
	protected void verifySampleFileContents(String directoryPath, String fileName) throws Exception {
		String location = directoryPath + "/" + fileName;
		String path = new Path(FILE_SERVLET_LOCATION).append(getTestBaseResourceURILocation()).append(location).toString();
		String requestURI = URIUtil.fromString(SERVER_LOCATION + path).toString();
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", fileName, response.getText());
	}

	protected void verifyUserJson(JSONObject jsonObject, String userId, List<String> workspaceIds) throws Exception {
		assertTrue(jsonObject.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStore.VERSION, jsonObject.getInt(SimpleMetaStore.ORION_VERSION));
		assertTrue(jsonObject.has(MetadataInfo.UNIQUE_ID));
		assertEquals(userId, jsonObject.getString(MetadataInfo.UNIQUE_ID));
		assertTrue(jsonObject.has(UserConstants.USER_NAME));
		assertEquals(userId, jsonObject.getString(UserConstants.USER_NAME));
		assertTrue(jsonObject.has(UserConstants.FULL_NAME));
		assertTrue(jsonObject.has("WorkspaceIds"));
		JSONArray workspaceIdsFromJson = jsonObject.getJSONArray("WorkspaceIds");
		assertNotNull(workspaceIdsFromJson);
		for (String workspaceId : workspaceIds) {
			verifyValueExistsInJsonArray(workspaceIdsFromJson, workspaceId);
		}
		assertTrue(jsonObject.has("Properties"));
		JSONObject properties = jsonObject.getJSONObject("Properties");
		assertTrue(properties.has("UserRightsVersion"));
		assertTrue(properties.has("UserRights"));
		assertTrue(properties.has(UserConstants.PASSWORD));

		String email = userId + "@example.com";

		if (properties.has(UserConstants.BLOCKED)) {
			assertEquals(properties.getString(UserConstants.BLOCKED), "true");
		}
		if (properties.has(UserConstants.DISK_USAGE)) {
			assertEquals(properties.getString(UserConstants.DISK_USAGE), "74M");
		}
		if (properties.has(UserConstants.DISK_USAGE_TIMESTAMP)) {
			assertEquals(properties.getString(UserConstants.DISK_USAGE_TIMESTAMP), TIMESTAMP);
		}
		if (properties.has(UserConstants.EMAIL)) {
			assertEquals(properties.getString(UserConstants.EMAIL), email);
		}
		if (properties.has(UserConstants.EMAIL_CONFIRMATION_ID)) {
			assertEquals(properties.getString(UserConstants.EMAIL_CONFIRMATION_ID), CONFIRMATION_ID);
		}
		if (properties.has(UserConstants.LAST_LOGIN_TIMESTAMP)) {
			assertEquals(properties.getString(UserConstants.LAST_LOGIN_TIMESTAMP), TIMESTAMP);
		}
		if (properties.has(UserConstants.OAUTH)) {
			assertEquals(properties.getString(UserConstants.OAUTH), "https://api.github.com/users/ahunter-orion/4500523");
		}
		if (properties.has(UserConstants.OPENID)) {
			assertEquals(properties.getString(UserConstants.OPENID), "https://www.google.com/accounts/o8/id?id=AItOawkTs8dYMgHG0tlvW8PE7RmNZwDlOWlWIU8");
		}
		if (properties.has(UserConstants.PASSWORD_RESET_ID)) {
			assertEquals(properties.getString(UserConstants.PASSWORD_RESET_ID), CONFIRMATION_ID);
		}
		if (properties.has("git/config/userInfo")) {
			assertEquals(properties.getString("git/config/userInfo"), "{\"GitName\":\"" + userId + "\",\"GitMail\":\"" + email + "\"}");
		}
		assertFalse(jsonObject.has("profileProperties"));
		assertFalse(jsonObject.has("GitName"));
		assertFalse(jsonObject.has("GitMail"));
	}

	protected void verifyUserMetaData(String userId, List<String> workspaceIds) throws Exception {
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		JSONObject userJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
		verifyUserJson(userJSON, userId, workspaceIds);
	}

	protected void verifyValueExistsInJsonArray(JSONArray jsonArray, String value) throws JSONException {
		for (int i = 0; i < jsonArray.length(); i++) {
			String jsonValue = jsonArray.getString(i);
			if (value.equals(jsonValue)) {
				return;
			}
		}
		fail("Value \"" + value + "\" does not exist in JSONArray " + jsonArray.toString());
	}

	protected void verifyWorkspaceJson(JSONObject jsonObject, String userId, String workspaceId, String workspaceName, List<String> projectNames) throws Exception {
		assertTrue(jsonObject.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStore.VERSION, jsonObject.getInt(SimpleMetaStore.ORION_VERSION));
		assertTrue(jsonObject.has(MetadataInfo.UNIQUE_ID));
		assertEquals(workspaceId, jsonObject.getString(MetadataInfo.UNIQUE_ID));
		assertTrue(jsonObject.has("UserId"));
		assertEquals(userId, jsonObject.getString("UserId"));
		assertTrue(jsonObject.has(UserConstants.FULL_NAME));
		assertEquals(workspaceName, jsonObject.getString(UserConstants.FULL_NAME));
		assertTrue(jsonObject.has("ProjectNames"));
		JSONArray projectNamesJson = jsonObject.getJSONArray("ProjectNames");
		for (String projectName : projectNames) {
			projectNamesJson.put(projectName);
		}
		assertNotNull(projectNamesJson);
		assertTrue(jsonObject.has("Properties"));
	}

	protected void verifyWorkspaceMetaData(String userId, String workspaceName, List<String> projectNames) throws Exception {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		assertNotNull(workspaceId);
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		assertNotNull(encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName));
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, workspaceId));
		JSONObject workspaceJson = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, workspaceId);
		verifyWorkspaceJson(workspaceJson, userId, workspaceId, workspaceName, projectNames);
	}

	/**
	 * Verifies the test user has the specified number of workspaces.
	 * @param workspaces Number of workspaces.
	 */
	protected void verifyWorkspaceRequest(List<String> workspaceIds) throws Exception {
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No workspace information in response", responseObject);
		String userId = responseObject.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(userId);
		assertEquals(testUserId, responseObject.optString(UserConstants.USER_NAME));
		JSONArray workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(workspaceIds.size(), workspaces.length());
		for (String workspaceId : workspaceIds) {
			assertTrue(workspaces.toString().contains(workspaceId));
		}

	}
}
