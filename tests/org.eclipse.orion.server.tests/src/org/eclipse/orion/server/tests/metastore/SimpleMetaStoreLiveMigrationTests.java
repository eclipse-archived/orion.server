/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreMigration;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.MetadataInfo;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Tests to ensure that the older versions of the Metadata storage are automatically updated to
 * the latest version.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStoreLiveMigrationTests extends FileSystemTest {

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
		jsonObject.put(UserConstants2.FULL_NAME, projectName);
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
		jsonObject.put(UserConstants2.USER_NAME, userId);
		jsonObject.put(UserConstants2.FULL_NAME, userId);
		String password = SimpleUserPasswordUtil.encryptPassword(userId);
		JSONObject properties = new JSONObject();
		if (version == SimpleMetaStoreMigration.VERSION4 || version == SimpleMetaStoreMigration.VERSION6 || version == SimpleMetaStoreMigration.VERSION7) {
			jsonObject.put("password", password);
		} else {
			properties.put(UserConstants2.PASSWORD, password);
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
			properties.put(UserConstants2.BLOCKED, "true");
			properties.put(UserConstants2.DISK_USAGE, "74M");
			properties.put(UserConstants2.DISK_USAGE_TIMESTAMP, TIMESTAMP);
			properties.put(UserConstants2.EMAIL, email);
			properties.put(UserConstants2.EMAIL_CONFIRMATION_ID, CONFIRMATION_ID);
			properties.put(UserConstants2.LAST_LOGIN_TIMESTAMP, TIMESTAMP);
			properties.put(UserConstants2.OAUTH, "https://api.github.com/users/ahunter-orion/4500523");
			properties.put(UserConstants2.OPENID, "https://www.google.com/accounts/o8/id?id=AItOawkTs8dYMgHG0tlvW8PE7RmNZwDlOWlWIU8");
			properties.put(UserConstants2.PASSWORD_RESET_ID, CONFIRMATION_ID);
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
		jsonObject.put(UserConstants2.FULL_NAME, workspaceName);
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

	@Before
	public void setUp() throws Exception {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
	}

	/**
	 * Verify that files in the users directory that are invalid metadata are archived
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testArchiveInvalidMetaDataFile(int version) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);

		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		// create invalid metadata on disk
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), testUserId);
		String invalid = "delete.html";
		File invalidFileInUserHome = new File(userMetaFolder, invalid);
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(invalidFileInUserHome);
			Charset utf8 = Charset.forName("UTF-8");
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, utf8);
			outputStreamWriter.write("<!doctype html>\n");
			outputStreamWriter.flush();
			outputStreamWriter.close();
			fileOutputStream.close();
		} catch (IOException e) {
			fail("Count not create a test file in the Orion Project:" + e.getLocalizedMessage());
		}
		assertTrue(invalidFileInUserHome.exists());
		assertTrue(invalidFileInUserHome.isFile());
		String archivedFilePath = invalidFileInUserHome.toString().substring(getWorkspaceRoot().toString().length());

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);

		// verify the invalid metadata file has moved to the archive
		File archiveFolder = new File(getWorkspaceRoot(), SimpleMetaStoreUtil.ARCHIVE);
		assertTrue(archiveFolder.exists());
		assertTrue(archiveFolder.isDirectory());
		File archivedFile = new File(archiveFolder, archivedFilePath);
		assertTrue(archivedFile.exists());
		assertTrue(archivedFile.isFile());
		assertFalse(invalidFileInUserHome.exists());
	}

	/**
	 * Archive invalid metadata file test for SimpleMetaStore version 4 format.
	 * @throws Exception
	 */
	@Test
	public void testArchiveInvalidMetaDataFileVersionFour() throws Exception {
		testArchiveInvalidMetaDataFile(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * Archive invalid metadata file test for SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testArchiveInvalidMetaDataFileVersionSeven() throws Exception {
		testArchiveInvalidMetaDataFile(SimpleMetaStoreMigration.VERSION7);
	}

	/**
	 * Archive invalid metadata file test for SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testArchiveInvalidMetaDataFileVersionSix() throws Exception {
		testArchiveInvalidMetaDataFile(SimpleMetaStoreMigration.VERSION6);
	}

	/**
	 * Verify that files in the users directory that are invalid metadata are archived
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testArchiveInvalidMetaDataFolder(int version) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);

		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		// create invalid metadata folder on disk
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), testUserId);
		String invalid = "delete.me";
		File invalidFolderInUserHome = new File(userMetaFolder, invalid);
		invalidFolderInUserHome.mkdirs();
		assertTrue(invalidFolderInUserHome.exists());
		assertTrue(invalidFolderInUserHome.isDirectory());
		String archivedFilePath = invalidFolderInUserHome.toString().substring(getWorkspaceRoot().toString().length());

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);

		// verify the invalid metadata folder has moved to the archive
		File archiveFolder = new File(getWorkspaceRoot(), SimpleMetaStoreUtil.ARCHIVE);
		assertTrue(archiveFolder.exists());
		assertTrue(archiveFolder.isDirectory());
		File archivedFile = new File(archiveFolder, archivedFilePath);
		assertTrue(archivedFile.exists());
		assertTrue(archivedFile.isDirectory());
		assertFalse(invalidFolderInUserHome.exists());
	}

	/**
	 * Archive invalid metadata folder test for SimpleMetaStore version 4 format.
	 * @throws Exception
	 */
	@Test
	public void testArchiveInvalidMetaDataFolderVersionFour() throws Exception {
		testArchiveInvalidMetaDataFolder(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * Verify nothing is modified in the case that the project metadata file is corrupt.
	 * @throws Exception
	 */
	@Test
	public void testProjectMetadataCorruption() throws Exception {
		int version = SimpleMetaStoreMigration.VERSION4;
		testUserId = testName.getMethodName();
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, workspaceName);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, workspaceName, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, workspaceName);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, workspaceName, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, workspaceName, projectNames.get(0));

		// corrupt the workspace metadata on disk
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), testUserId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectNames.get(0));
		File corruptedProjectJSON = SimpleMetaStoreUtil.retrieveMetaFile(workspaceMetaFolder, projectId);
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(corruptedProjectJSON);
			Charset utf8 = Charset.forName("UTF-8");
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, utf8);
			outputStreamWriter.write("<!doctype html>\n");
			outputStreamWriter.flush();
			outputStreamWriter.close();
			fileOutputStream.close();
		} catch (IOException e) {
			fail("Count not create a test file in the Orion Project:" + e.getLocalizedMessage());
		}
		assertTrue(corruptedProjectJSON.exists());
		assertTrue(corruptedProjectJSON.isFile());

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify the web request has failed
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());

		// verify the user metadata on disk was not modified
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		JSONObject userJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
		assertTrue(userJSON.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStoreMigration.VERSION4, userJSON.getInt(SimpleMetaStore.ORION_VERSION));

		// verify the workspace metadata on disk was not moved or modified
		assertTrue(SimpleMetaStoreUtil.isMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE));
		JSONObject workspaceJson = SimpleMetaStoreUtil.readMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE);
		assertTrue(workspaceJson.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStoreMigration.VERSION4, workspaceJson.getInt(SimpleMetaStore.ORION_VERSION));
	}

	/**
	 * A user named growth8 with one workspace with a non standard name and two projects in SimpleMetaStore version 4 format.
	 * Matches a user on an internal server.
	 * @throws Exception
	 */
	@Test
	public void testUserGrowth8WithOneWorkspaceTwoProjectsVersionFour() throws Exception {
		testUserId = "growth8";
		testUserLogin = testUserId;
		testUserPassword = testUserId;
		String workspaceName = "New Sandbox";
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, workspaceName);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add("growth8 | growth3");
		projectNames.add("growth8 | simpleProject");

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(SimpleMetaStoreMigration.VERSION4, testUserId, workspaceIds);
		// tweak the default to match the internal server's metadata.
		newUserJSON.put(UserConstants2.FULL_NAME, "Unnamed User");
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(SimpleMetaStoreMigration.VERSION4, testUserId, workspaceName, projectNames);
		createWorkspaceMetaData(SimpleMetaStoreMigration.VERSION4, newWorkspaceJSON, testUserId, workspaceName);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(SimpleMetaStoreMigration.VERSION4, testUserId, workspaceName, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(SimpleMetaStoreMigration.VERSION4, newProjectJSON, testUserId, workspaceName, projectNames.get(0));
		defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(1));
		newProjectJSON = createProjectJson(SimpleMetaStoreMigration.VERSION4, testUserId, workspaceName, projectNames.get(1), defaultContentLocation);
		createProjectMetaData(SimpleMetaStoreMigration.VERSION4, newProjectJSON, testUserId, workspaceName, projectNames.get(1));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, workspaceName, projectNames.get(0));
		verifyProjectRequest(testUserId, workspaceName, projectNames.get(1));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, workspaceName, projectNames);
		verifyProjectMetaData(testUserId, workspaceName, projectNames.get(0));
		verifyProjectMetaData(testUserId, workspaceName, projectNames.get(1));
	}

	/**
	 * Verify nothing is modified in the case that the user metadata file is corrupt.
	 * @throws Exception
	 */
	@Test
	public void testUserMetadataCorruption() throws Exception {
		int version = SimpleMetaStoreMigration.VERSION4;
		testUserId = testName.getMethodName();
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, workspaceName);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, workspaceName, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, workspaceName);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, workspaceName, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, workspaceName, projectNames.get(0));

		// corrupt the user metadata on disk
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), testUserId);
		String userJSON = "user.json";
		File corruptedUserJSON = new File(userMetaFolder, userJSON);
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(corruptedUserJSON);
			Charset utf8 = Charset.forName("UTF-8");
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, utf8);
			outputStreamWriter.write("<!doctype html>\n");
			outputStreamWriter.flush();
			outputStreamWriter.close();
			fileOutputStream.close();
		} catch (IOException e) {
			fail("Count not create a test file in the Orion Project:" + e.getLocalizedMessage());
		}
		assertTrue(corruptedUserJSON.exists());
		assertTrue(corruptedUserJSON.isFile());

		// verify the web request has failed
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// verify the workspace metadata on disk was not moved or modified
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.isMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE));
		JSONObject workspaceJson = SimpleMetaStoreUtil.readMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE);
		assertTrue(workspaceJson.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStoreMigration.VERSION4, workspaceJson.getInt(SimpleMetaStore.ORION_VERSION));
	}

	/**
	 * A user with Bug 433443 that has an orphan openid property
	 * @throws Exception
	 */
	@Test
	public void testUserWithBug433443() throws Exception {
		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(SimpleMetaStoreMigration.VERSION7, testUserId, EMPTY_LIST);

		// add the json for Bug 433433
		JSONObject profileProperties = newUserJSON.getJSONObject("profileProperties");
		profileProperties.put("openid", "\n\n");
		createUserMetaData(newUserJSON, testUserId);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(EMPTY_LIST);

		// verify metadata on disk
		verifyUserMetaData(testUserId, EMPTY_LIST);
	}

	/**
	 * A user with Bug 447759 that has an old passwordResetId property that needs to be removed
	 * @throws Exception
	 */
	@Test
	public void testUserWithBug447759() throws Exception {
		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(SimpleMetaStoreMigration.VERSION7, testUserId, EMPTY_LIST);

		// add the json for Bug 433433, reset id of from Nov 12 2012
		JSONObject profileProperties = newUserJSON.getJSONObject("profileProperties");
		profileProperties.put("passwordResetId", "1352742517746-0.02828520676443591");
		createUserMetaData(newUserJSON, testUserId);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(EMPTY_LIST);

		// verify metadata on disk
		verifyUserMetaData(testUserId, EMPTY_LIST);

		// verify the old reset id has been removed
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), testUserId);
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		JSONObject userJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
		assertFalse(userJSON.has("passwordResetId"));
		assertFalse(userJSON.has(UserConstants2.EMAIL_CONFIRMATION_ID));
	}

	/**
	 * A user with no workspaces.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithNoWorkspaces(int version) throws Exception {
		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(version, testUserId, EMPTY_LIST);
		createUserMetaData(newUserJSON, testUserId);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(EMPTY_LIST);

		// verify metadata on disk
		verifyUserMetaData(testUserId, EMPTY_LIST);
	}

	/**
	 * A user with no workspaces and does not have a version. This should never occur but test to reproduce corruption.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesNoVersion() throws Exception {
		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(SimpleMetaStore.VERSION, testUserId, EMPTY_LIST);
		newUserJSON.remove(SimpleMetaStore.ORION_VERSION);
		createUserMetaData(newUserJSON, testUserId);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(EMPTY_LIST);

		// verify metadata on disk
		verifyUserMetaData(testUserId, EMPTY_LIST);
	}

	/**
	 * A user with no workspaces created by the tests framework.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesUsingFramework() throws Exception {
		// perform the basic step from the parent abstract test class.
		setUpAuthorization();

		// verify the web request
		verifyWorkspaceRequest(EMPTY_LIST);

		// verify metadata on disk
		verifyUserMetaData(testUserId, EMPTY_LIST);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 8 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersionEight() throws Exception {
		testUserWithNoWorkspaces(SimpleMetaStore.VERSION);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 4 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersionFour() throws Exception {
		testUserWithNoWorkspaces(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersionSeven() throws Exception {
		testUserWithNoWorkspaces(SimpleMetaStoreMigration.VERSION7);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersionSix() throws Exception {
		testUserWithNoWorkspaces(SimpleMetaStoreMigration.VERSION6);
	}

	/**
	 * A user with one workspace and no projects.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithOneWorkspaceNoProjects(int version) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);

		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);
	}

	/**
	 * A user with one workspace and no projects created by the tests framework.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceNoProjectsUsingFramework() throws Exception {
		// perform the basic steps from the parent abstract test class.
		setUpAuthorization();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);

		// verify web request
		verifyWorkspaceRequest(workspaceIds);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);
	}

	/**
	 * A user with one workspace and no projects in SimpleMetaStore version 8 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceNoProjectsVersionEight() throws Exception {
		testUserWithOneWorkspaceNoProjects(SimpleMetaStore.VERSION);
	}

	/**
	* A user with one workspace and no projects in SimpleMetaStore version 4 format.
	* @throws Exception
	*/
	@Test
	public void testUserWithOneWorkspaceNoProjectsVersionFour() throws Exception {
		testUserWithOneWorkspaceNoProjects(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * A user with one workspace and no projects in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceNoProjectsVersionSeven() throws Exception {
		testUserWithOneWorkspaceNoProjects(SimpleMetaStoreMigration.VERSION7);
	}

	/**
	 * A user with one workspace and no projects in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceNoProjectsVersionSix() throws Exception {
		testUserWithOneWorkspaceNoProjects(SimpleMetaStoreMigration.VERSION6);
	}

	/**
	 * A user with one workspace and one project. Additionally confirm the workspace is given the default workspace name.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithOneWorkspaceOneProject(int version, String workspaceName) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, workspaceName);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, workspaceName, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, workspaceName);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, workspaceName, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, workspaceName, projectNames.get(0));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, workspaceName, projectNames.get(0));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, workspaceName, projectNames);
		verifyProjectMetaData(testUserId, workspaceName, projectNames.get(0));
	}

	/**
	 * A user with one workspace and one project created by the tests framework.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceOneProjectUsingFramework() throws Exception {
		// perform the basic steps from the parent abstract test class.
		setUpAuthorization();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		createTestProject(testName.getMethodName());
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
	}

	/**
	 * A user with one workspace and one project in SimpleMetaStore version 8 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceOneProjectVersionEight() throws Exception {
		testUserWithOneWorkspaceOneProject(SimpleMetaStore.VERSION, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
	}

	/**
	* A user with one workspace and one project in SimpleMetaStore version 4 format.
	* @throws Exception
	*/
	@Test
	public void testUserWithOneWorkspaceOneProjectVersionFour() throws Exception {
		testUserWithOneWorkspaceOneProject(SimpleMetaStoreMigration.VERSION4, "Work SandBox");
	}

	/**
	 * A user with one workspace and one project in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceOneProjectVersionSeven() throws Exception {
		testUserWithOneWorkspaceOneProject(SimpleMetaStoreMigration.VERSION7, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
	}

	/**
	 * A user with one workspace and one project in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceOneProjectVersionSix() throws Exception {
		testUserWithOneWorkspaceOneProject(SimpleMetaStoreMigration.VERSION6, "Sandbox");
	}

	/**
	 * A user with one workspace and two projects.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithOneWorkspaceTwoProjects(int version) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));
		projectNames.add("Second Project");

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		defaultContentLocation = getProjectDefaultContentLocation(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1));
		newProjectJSON = createProjectJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1));
	}

	/**
	 * A user with one workspace and two projects in SimpleMetaStore version 8 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceTwoProjectsVersionEight() throws Exception {
		testUserWithOneWorkspaceTwoProjects(SimpleMetaStore.VERSION);
	}

	/**
	* A user with one workspace and two projects in SimpleMetaStore version 4 format.
	* @throws Exception
	*/
	@Test
	public void testUserWithOneWorkspaceTwoProjectsVersionFour() throws Exception {
		testUserWithOneWorkspaceTwoProjects(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * A user with one workspace and two projects in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceTwoProjectsVersionSeven() throws Exception {
		testUserWithOneWorkspaceTwoProjects(SimpleMetaStoreMigration.VERSION7);
	}

	/**
	 * A user with one workspace and two projects in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceTwoProjectsVersionSix() throws Exception {
		testUserWithOneWorkspaceTwoProjects(SimpleMetaStoreMigration.VERSION6);
	}

	/**
	 * A user with two workspaces and two projects.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithTwoWorkspacesTwoProjects(int version) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));
		String secondWorkspaceName = "Second Workspace";
		String secondWorkspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, secondWorkspaceName);
		workspaceIds.add(secondWorkspaceId);
		List<String> secondProjectNames = new ArrayList<String>();
		secondProjectNames.add("Second Project");

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		newWorkspaceJSON = createWorkspaceJson(version, testUserId, secondWorkspaceName, secondProjectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, secondWorkspaceName);

		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, secondWorkspaceName, secondProjectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, secondWorkspaceName, secondProjectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, secondWorkspaceName, secondProjectNames.get(0));

		defaultContentLocation = getProjectDefaultContentLocation(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		newProjectJSON = createProjectJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// Fix the workspace ids now that the migration has run and there is only one wrkspace
		workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, secondProjectNames.get(0));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, secondProjectNames);
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, secondProjectNames.get(0));
	}

	/**
	* A user with two workspaces and two projects in SimpleMetaStore version 4 format.
	* @throws Exception
	*/
	@Test
	public void testUserWithTwoWorkspacesTwoProjectsVersionFour() throws Exception {
		testUserWithTwoWorkspacesTwoProjects(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * A user with two workspaces and two projects in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithTwoWorkspacesTwoProjectsVersionSix() throws Exception {
		testUserWithTwoWorkspacesTwoProjects(SimpleMetaStoreMigration.VERSION6);
	}

	/**
	 * Verify nothing is modified in the case that the workspace metadata file is corrupt.
	 * @throws Exception
	 */
	@Test
	public void testWorkspaceMetadataCorruption() throws Exception {
		int version = SimpleMetaStoreMigration.VERSION4;
		testUserId = testName.getMethodName();
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, workspaceName);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, workspaceName, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, workspaceName);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, workspaceName, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, workspaceName, projectNames.get(0));

		// corrupt the workspace metadata on disk
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), testUserId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		String workspaceJSON = "workspace.json";
		File corruptedWorkspaceJSON = new File(workspaceMetaFolder, workspaceJSON);
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(corruptedWorkspaceJSON);
			Charset utf8 = Charset.forName("UTF-8");
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, utf8);
			outputStreamWriter.write("<!doctype html>\n");
			outputStreamWriter.flush();
			outputStreamWriter.close();
			fileOutputStream.close();
		} catch (IOException e) {
			fail("Count not create a test file in the Orion Project:" + e.getLocalizedMessage());
		}
		assertTrue(corruptedWorkspaceJSON.exists());
		assertTrue(corruptedWorkspaceJSON.isFile());

		// since we modified files on disk directly, force user cache update by reading all users
		OrionConfiguration.getMetaStore().readAllUsers();

		// verify the web request has failed
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());

		// verify the user metadata on disk was not modified
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		JSONObject userJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
		assertTrue(userJSON.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStoreMigration.VERSION4, userJSON.getInt(SimpleMetaStore.ORION_VERSION));
	}

	protected void verifyProjectJson(JSONObject jsonObject, String userId, String workspaceId, String projectName, File contentLocation) throws Exception {
		assertTrue(jsonObject.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStore.VERSION, jsonObject.getInt(SimpleMetaStore.ORION_VERSION));
		assertTrue(jsonObject.has(MetadataInfo.UNIQUE_ID));
		assertEquals(projectName, jsonObject.getString(MetadataInfo.UNIQUE_ID));
		assertTrue(jsonObject.has("WorkspaceId"));
		assertEquals(workspaceId, jsonObject.getString("WorkspaceId"));
		assertTrue(jsonObject.has(UserConstants2.FULL_NAME));
		assertEquals(projectName, jsonObject.getString(UserConstants2.FULL_NAME));
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
		assertTrue(jsonObject.has(UserConstants2.USER_NAME));
		assertEquals(userId, jsonObject.getString(UserConstants2.USER_NAME));
		assertTrue(jsonObject.has(UserConstants2.FULL_NAME));
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
		assertTrue(properties.has(UserConstants2.PASSWORD));

		String email = userId + "@example.com";

		if (properties.has(UserConstants2.BLOCKED)) {
			assertEquals(properties.getString(UserConstants2.BLOCKED), "true");
		}
		if (properties.has(UserConstants2.DISK_USAGE)) {
			assertEquals(properties.getString(UserConstants2.DISK_USAGE), "74M");
		}
		if (properties.has(UserConstants2.DISK_USAGE_TIMESTAMP)) {
			assertEquals(properties.getString(UserConstants2.DISK_USAGE_TIMESTAMP), TIMESTAMP);
		}
		if (properties.has(UserConstants2.EMAIL)) {
			assertEquals(properties.getString(UserConstants2.EMAIL), email);
		}
		if (properties.has(UserConstants2.EMAIL_CONFIRMATION_ID)) {
			assertEquals(properties.getString(UserConstants2.EMAIL_CONFIRMATION_ID), CONFIRMATION_ID);
		}
		if (properties.has(UserConstants2.LAST_LOGIN_TIMESTAMP)) {
			assertEquals(properties.getString(UserConstants2.LAST_LOGIN_TIMESTAMP), TIMESTAMP);
		}
		if (properties.has(UserConstants2.OAUTH)) {
			assertEquals(properties.getString(UserConstants2.OAUTH), "https://api.github.com/users/ahunter-orion/4500523");
		}
		if (properties.has(UserConstants2.OPENID)) {
			assertEquals(properties.getString(UserConstants2.OPENID), "https://www.google.com/accounts/o8/id?id=AItOawkTs8dYMgHG0tlvW8PE7RmNZwDlOWlWIU8");
		}
		if (properties.has(UserConstants2.PASSWORD_RESET_ID)) {
			assertEquals(properties.getString(UserConstants2.PASSWORD_RESET_ID), CONFIRMATION_ID);
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
		assertTrue(jsonObject.has(UserConstants2.FULL_NAME));
		assertEquals(workspaceName, jsonObject.getString(UserConstants2.FULL_NAME));
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
		assertEquals(testUserId, responseObject.optString(UserConstants2.USER_NAME));
		JSONArray workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(workspaceIds.size(), workspaces.length());
		for (String workspaceId : workspaceIds) {
			assertTrue(workspaces.toString().contains(workspaceId));
		}

	}
}
