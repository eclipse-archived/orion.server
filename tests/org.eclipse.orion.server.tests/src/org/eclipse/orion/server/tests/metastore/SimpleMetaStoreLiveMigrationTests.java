/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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

import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.json.JSONArray;
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
	 * The first version of the Simple Meta Store was version 4.
	 */
	public final static int VERSION4 = 4;

	/**
	 * The second version of the Simple Meta Store was version 6.
	 */
	public final static int VERSION6 = 6;

	@BeforeClass
	public static void initializeRootFileStorePrefixLocation() {
		initializeWorkspaceLocation();
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
		jsonObject.put("UniqueId", userId);
		jsonObject.put("UserName", userId);
		jsonObject.put("FullName", userId);
		JSONArray array = new JSONArray();
		for (String workspaceId : workspaceIds) {
			array.put(workspaceId);
		}
		jsonObject.put("WorkspaceIds", array);
		String password = SimpleUserPasswordUtil.encryptPassword(userId);
		jsonObject.put("password", password);
		JSONObject properties = new JSONObject();
		properties.put("UserRightsVersion", "3");
		JSONArray userRights = new JSONArray();
		JSONObject userRight = new JSONObject();
		userRight.put("Method", 15);
		String users = "/users/";
		userRight.put("Uri", users.concat(userId));
		userRights.put(userRight);
		properties.put("UserRights", userRights);
		jsonObject.put("Properties", properties);
		return jsonObject;
	}

	protected void createUserMetaData(JSONObject newUserJSON) throws CoreException {
		SimpleMetaStoreUtil.createMetaUserFolder(getWorkspaceRoot(), testUserId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), testUserId);
		assertTrue("Could not create directory " + userMetaFolder.toString(), userMetaFolder.exists() && userMetaFolder.isDirectory());
		assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		SimpleMetaStoreUtil.createMetaFile(userMetaFolder, SimpleMetaStore.USER, newUserJSON);
		File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, SimpleMetaStore.USER);
		assertTrue("Could not create file " + userMetaFile.toString(), userMetaFile.exists() && userMetaFile.isFile());
	}

	@Before
	public void setUp() throws Exception {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
	}

	/**
	 * This test does not actually test anything, just verifies the framework is running as expected.
	 * @throws Exception
	 */
	@Test
	public void testBasicFrameworkTest() throws Exception {
		// perform the basic steps from the parent abstract test class.
		setUpAuthorization();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		createTestProject(testName.getMethodName());

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		verifySampleFileContents(directoryPath, fileName);
		verifyWorkspaceRequest(1);
		verifyUserMetaData(testUserId);
	}

	/**
	 * A user with no workspaces created by the tests framework.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspaces() throws Exception {
		setUpAuthorization();
		verifyWorkspaceRequest(0);
		verifyUserMetaData(testUserId);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 4 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersion4() throws Exception {
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(VERSION4, testUserId, new ArrayList<String>());
		createUserMetaData(newUserJSON);
		verifyWorkspaceRequest(0);
		verifyUserMetaData(testUserId);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersion6() throws Exception {
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(VERSION6, testUserId, new ArrayList<String>());
		createUserMetaData(newUserJSON);
		verifyWorkspaceRequest(0);
		verifyUserMetaData(testUserId);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersion7() throws Exception {
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(SimpleMetaStore.VERSION, testUserId, new ArrayList<String>());
		createUserMetaData(newUserJSON);
		verifyWorkspaceRequest(0);
		verifyUserMetaData(testUserId);
	}

	/**
	 * Verifies the sample content using the remote Orion API. Verifies the user has been
	 * migrated successfully and all is good with the account.
	 * @param directoryPath
	 * @param fileName
	 * @throws Exception
	 */
	protected void verifySampleFileContents(String directoryPath, String fileName) throws Exception {
		WebRequest request = getGetFilesRequest(directoryPath + "/" + fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", fileName, response.getText());
	}

	protected void verifyUserJson(JSONObject jsonObject, String userId) throws Exception {
		assertTrue(jsonObject.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStore.VERSION, jsonObject.getInt(SimpleMetaStore.ORION_VERSION));
		assertTrue(jsonObject.has("UniqueId"));
		assertEquals(userId, jsonObject.getString("UniqueId"));
		assertTrue(jsonObject.has("UserName"));
		assertEquals(userId, jsonObject.getString("UserName"));
		assertTrue(jsonObject.has("FullName"));
		assertTrue(jsonObject.has("WorkspaceIds"));
		assertTrue(jsonObject.has("password"));
		assertTrue(jsonObject.has("Properties"));
		JSONObject properties = jsonObject.getJSONObject("Properties");
		assertTrue(properties.has("UserRightsVersion"));
		assertTrue(properties.has("UserRights"));
	}

	protected void verifyUserMetaData(String testUserId) throws Exception {
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), testUserId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), testUserId);
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		JSONObject userJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
		verifyUserJson(userJSON, testUserId);
	}

	/**
	 * Verifies the test user has the specified number of workspaces.
	 * @param workspaces Number of workspaces.
	 */
	protected void verifyWorkspaceRequest(int workspaceCount) throws Exception {
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
		assertEquals(testUserId, responseObject.optString("UserName"));
		JSONArray workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(workspaceCount, workspaces.length());
	}

}
