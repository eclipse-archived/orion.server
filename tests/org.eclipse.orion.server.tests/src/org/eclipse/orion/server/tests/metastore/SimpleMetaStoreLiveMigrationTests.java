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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreMigration;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.users.UserConstants2;
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
public class SimpleMetaStoreLiveMigrationTests extends AbstractSimpleMetaStoreMigrationTests {

	/**
	 * type-safe value for an empty list.
	 */
	protected static final List<String> EMPTY_LIST = Collections.emptyList();

	@BeforeClass
	public static void initializeRootFileStorePrefixLocation() {
		initializeWorkspaceLocation();
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
}
