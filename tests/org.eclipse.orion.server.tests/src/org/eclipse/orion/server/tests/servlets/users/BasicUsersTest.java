/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.users;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class BasicUsersTest extends UsersTest {

	@Test
	public void testGetUsersList() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		WebRequest request = getGetUsersRequest("", true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());
		assertTrue("Invalid format of response.", responseObject.has(UserConstants.KEY_USERS));
		JSONArray usersArray = responseObject.getJSONArray(UserConstants.KEY_USERS);
		assertTrue("Too small number of users returned", usersArray.length() > 1);
	}

	@Test
	public void testGetUsersForbidden() throws IOException, SAXException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		WebRequest request = getGetUsersRequest("", false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());
		boolean wasJson = true;
		try {
			new JSONObject(response.getText());
		} catch (JSONException e) {
			wasJson = false;
		}
		assertFalse("Returned a jsonObject in reponse where FORBIDDEN should be returned", wasJson);
	}

	@Test
	public void testCreateDuplicateUser() throws IOException, SAXException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put(UserConstants.KEY_LOGIN, "testDupUser");
		params.put(UserConstants2.FULL_NAME, "username_testCreateDuplicateUser");
		params.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		//try creating same user again
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCreateUserDuplicateEmail() throws IOException, SAXException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put(UserConstants.KEY_LOGIN, "testDupEmail");
		params.put(UserConstants2.FULL_NAME, "username_testCreateUserDuplicateEmail");
		params.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		params.put(UserConstants2.EMAIL, "username@example.com");
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		//try creating another user with same email address
		params.put(UserConstants.KEY_LOGIN, "usertestCreateUserDuplicateEmail2");
		request = getPostUsersRequest("", params, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCreateUserEmailDifferentCase() throws IOException, SAXException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put(UserConstants.KEY_LOGIN, "testCaseEmail");
		params.put(UserConstants2.FULL_NAME, "username_testCreateUserEmailDifferentCase");
		params.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		params.put(UserConstants2.EMAIL, "duplicateemail@example.com");
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		//try creating another user with same email address but different case
		params.put(UserConstants2.EMAIL, "DUPLICATEEMAIL@example.com");
		params.put(UserConstants.KEY_LOGIN, "testCaseEmail2");
		request = getPostUsersRequest("", params, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	/**
	 * Tests creating users with username that can't be represented in a URI.
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	@Test
	public void testCreateUserInvalidName() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		//restrict to alphanumeric characters
		String invalidChars = " !@#$%^&*()-=_+[]{}\";':\\/><.,`~";
		for (int i = 0; i < invalidChars.length(); i++) {
			String name = "bad" + invalidChars.charAt(i) + "name";

			// create user
			Map<String, String> params = new HashMap<String, String>();
			params.put(UserConstants.KEY_LOGIN, name);
			params.put(UserConstants2.FULL_NAME, "Tom");
			params.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
			WebRequest request = getPostUsersRequest("", params, true);
			WebResponse response = webConversation.getResponse(request);
			assertEquals("Should fail with name: " + name, HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

		}
	}

	@Test
	public void testCreateDeleteUsers() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put(UserConstants.KEY_LOGIN, "user" + System.currentTimeMillis());
		params.put(UserConstants2.FULL_NAME, "username_" + System.currentTimeMillis());
		params.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contain user location", responseObject.has(UserConstants.KEY_LOCATION));

		// check user details
		String location = responseObject.getString(UserConstants.KEY_LOCATION);

		request = getAuthenticatedRequest(location, METHOD_GET, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", params.get(UserConstants.KEY_LOGIN), responseObject.getString(UserConstants.KEY_LOGIN));
		assertEquals("Invalid user name", params.get(UserConstants2.FULL_NAME), responseObject.getString(UserConstants2.FULL_NAME));
		assertFalse("Response shouldn't contain password", responseObject.has(UserConstants2.PASSWORD));

		// check if user can authenticate
		request = getGetUsersRequest("", true);

		// create some project contents to test that delete user also deletes all project contents
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUser(params.get(UserConstants.KEY_LOGIN));
			String workspaceName = "Orion Content";
			WorkspaceInfo workspaceInfo = new WorkspaceInfo();
			workspaceInfo.setFullName(workspaceName);
			workspaceInfo.setUserId(userInfo.getUniqueId());
			OrionConfiguration.getMetaStore().createWorkspace(workspaceInfo);

			String projectName = "Orion Project";
			ProjectInfo projectInfo = new ProjectInfo();
			projectInfo.setFullName(projectName);
			projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
			OrionConfiguration.getMetaStore().createProject(projectInfo);

			IFileStore projectFolder = OrionConfiguration.getMetaStore().getDefaultContentLocation(projectInfo);
			projectInfo.setContentLocation(projectFolder.toURI());
			OrionConfiguration.getMetaStore().updateProject(projectInfo);
			if (!projectFolder.fetchInfo().exists()) {
				projectFolder.mkdir(EFS.NONE, null);
			}
			assertTrue(projectFolder.fetchInfo().exists() && projectFolder.fetchInfo().isDirectory());
			String fileName = "file.html";
			IFileStore file = projectFolder.getChild(fileName);
			try {
				OutputStream outputStream = file.openOutputStream(EFS.NONE, null);
				outputStream.write("<!doctype html>".getBytes());
				outputStream.close();
			} catch (IOException e) {
				fail("Count not create a test file in the Orion Project:" + e.getLocalizedMessage());
			}
			assertTrue("the file in the project folder should exist.", file.fetchInfo().exists());
		} catch (CoreException e) {
			fail("Could not create project contents for the user");
		}

		// delete user
		request = getAuthenticatedRequest(location, METHOD_DELETE, true);
		setAuthentication(request, params.get(UserConstants.KEY_LOGIN), params.get(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User could not delete his own account, response: " + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testCreateDeleteRights() throws IOException, SAXException, CoreException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put(UserConstants.KEY_LOGIN, "testCrDelRights");
		params.put(UserConstants2.FULL_NAME, "username_" + System.currentTimeMillis());
		params.put(UserConstants2.EMAIL, "test@test_" + System.currentTimeMillis());
		params.put("workspace", "workspace_" + System.currentTimeMillis());
		params.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user uid", responseObject.has(UserConstants.KEY_UID));

		String uid = responseObject.getString(UserConstants.KEY_UID);

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get(UserConstants.KEY_LOGIN), params.get(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privileges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());
		// add admin rights

		AuthorizationService.addUserRight(uid, "/users");
		AuthorizationService.addUserRight(uid, "/users/*");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get(UserConstants.KEY_LOGIN), params.get(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User tried to use his admin role but did not get the valid response: " + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// delete admin rights
		AuthorizationService.removeUserRight(uid, "/users");
		AuthorizationService.removeUserRight(uid, "/users/*");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get(UserConstants.KEY_LOGIN), params.get(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privileges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// delete user
		request = getDeleteUsersRequest(uid, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testUpdateUsers() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put(UserConstants.KEY_LOGIN, "user" + System.currentTimeMillis());
		params.put(UserConstants2.FULL_NAME, "username_" + System.currentTimeMillis());
		params.put("roles", "admin");
		String oldPass = "pass_" + System.currentTimeMillis();
		params.put(UserConstants2.PASSWORD, oldPass);
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user location", responseObject.has(UserConstants.KEY_LOCATION));

		String location = responseObject.getString(UserConstants.KEY_LOCATION);

		// update user
		JSONObject updateBody = new JSONObject();
		updateBody.put(UserConstants2.FULL_NAME, "usernameUpdate_" + System.currentTimeMillis());
		updateBody.put("oldPassword", oldPass);
		updateBody.put(UserConstants2.PASSWORD, "passUpdate_" + System.currentTimeMillis());
		updateBody.put("roles", "");

		request = getAuthenticatedRequest(location, METHOD_PUT, true, null, updateBody);

		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check user details
		request = getAuthenticatedRequest(location, METHOD_GET, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", params.get(UserConstants.KEY_LOGIN), responseObject.getString(UserConstants.KEY_LOGIN));
		assertEquals("Invalid user name", updateBody.getString(UserConstants2.FULL_NAME), responseObject.getString(UserConstants2.FULL_NAME));
		assertFalse("Response shouldn't contain password", responseObject.has(UserConstants2.PASSWORD));

		// check if user can authenticate and does not have admin role
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get(UserConstants.KEY_LOGIN), updateBody.getString(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privilegges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// delete user
		request = getAuthenticatedRequest(location, METHOD_DELETE, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testResetUser() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		String username = "user" + System.currentTimeMillis();
		params.put(UserConstants.KEY_LOGIN, username);
		params.put(UserConstants2.FULL_NAME, "username" + System.currentTimeMillis());
		params.put("roles", "admin");
		String oldPass = "pass_" + System.currentTimeMillis();
		params.put(UserConstants2.PASSWORD, oldPass);
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user location", responseObject.has(UserConstants.KEY_LOCATION));

		String location = responseObject.getString(UserConstants.KEY_LOCATION);

		//reset password
		String newPass = "passUpdate_" + System.currentTimeMillis();
		params = new HashMap<String, String>();
		params.put(UserConstants.KEY_LOGIN, username);
		params.put(UserConstants2.PASSWORD, newPass);
		params.put(UserConstants.KEY_RESET, "true");
		request = getPostUsersRequest("", params, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check if user can authenticate
		request = getAuthenticatedRequest(location, METHOD_GET, true);
		setAuthentication(request, params.get(UserConstants.KEY_LOGIN), newPass);
		response = webConversation.getResponse(request);
		assertEquals("User cannot log in with new credentials", HttpURLConnection.HTTP_OK, response.getResponseCode());

		// delete user
		request = getAuthenticatedRequest(location, METHOD_DELETE, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testChangeUserLogin() throws JSONException, IOException, SAXException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		String login1 = "login1" + System.currentTimeMillis();
		String password = "pass" + System.currentTimeMillis();

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put(UserConstants.KEY_LOGIN, login1);
		params.put(UserConstants2.PASSWORD, password);
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user location", responseObject.has(UserConstants.KEY_LOCATION));

		String location = responseObject.getString(UserConstants.KEY_LOCATION);

		String login2 = "login2" + System.currentTimeMillis();
		JSONObject updateBody = new JSONObject();
		updateBody.put(UserConstants.KEY_LOGIN, login2);

		request = getAuthenticatedRequest(location, METHOD_PUT, true, null, updateBody);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		if (OrionConfiguration.getMetaStore() instanceof SimpleMetaStore) {
			// When you rename the user, the location becomes /users/{newUser}
			location = "/users/" + login2;
		}

		request = getAuthenticatedRequest(location, METHOD_GET, true);
		setAuthentication(request, login2, password);
		response = webConversation.getResponse(request);
		assertEquals("User could not authenticate with new login" + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		responseObject = new JSONObject(response.getText());
		assertEquals("New login wasn't returned in user details", login2, responseObject.get(UserConstants.KEY_LOGIN));
	}

	/**
	 * @return a string representing the test users name.
	 */
	public String getTestUserName() {
		return "testNoAccess";
	}

	public String getTestUserPassword() {
		return "testNoAccess";
	}

	@Override
	public void setAdminRights(UserInfo adminUser) throws CoreException {
		//by default allow 'admin' to modify all users data
		AuthorizationService.addUserRight(adminUser.getUniqueId(), "/users");
		AuthorizationService.addUserRight(adminUser.getUniqueId(), "/users/*");
	}

	@Override
	public void setTestUserRights(UserInfo testUser) throws CoreException {
		//by default allow 'test' to modify his own data
		AuthorizationService.addUserRight(testUser.getUniqueId(), "/users/" + testUser.getUniqueId());
	}
}
