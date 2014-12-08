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

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.useradmin.UserHandlerV1;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.users.UserConstants2;
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
		assertTrue("Invalid format of response.", responseObject.has(UserHandlerV1.USERS));
		JSONArray usersArray = responseObject.getJSONArray(UserHandlerV1.USERS);
		assertTrue("Too small number of users returned", usersArray.length() > 1);
		assertTrue(responseObject.has(UserHandlerV1.USERS_START));
		assertTrue(responseObject.has(UserHandlerV1.USERS_ROWS));
		assertTrue(responseObject.has(UserHandlerV1.USERS_LENGTH));
		assertEquals(0, responseObject.getInt(UserHandlerV1.USERS_START));
		if (responseObject.getInt(UserHandlerV1.USERS_LENGTH) >= 20) {
			assertTrue(responseObject.getInt(UserHandlerV1.USERS_ROWS) == 20);
		} else {
			assertTrue(responseObject.getInt(UserHandlerV1.USERS_LENGTH) < 20);
		}
	}

	@Test
	public void testGetUsersForbidden() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		WebRequest request = getGetUsersRequest("", false);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		assertTrue(responseObject.has("HttpCode"));
		assertEquals(HttpURLConnection.HTTP_FORBIDDEN, responseObject.getInt("HttpCode"));
	}

	@Test
	public void testCreateDuplicateUser() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		JSONObject json = new JSONObject();
		json.put(UserConstants2.USER_NAME, "testDupUser");
		json.put(UserConstants2.FULL_NAME, "username_testCreateDuplicateUser");
		json.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//try creating same user again
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCreateUserDuplicateEmail() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		JSONObject json = new JSONObject();
		json.put(UserConstants2.USER_NAME, "testDupEmail");
		json.put(UserConstants2.FULL_NAME, "username_testCreateUserDuplicateEmail");
		json.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		json.put(UserConstants2.EMAIL, "username@example.com");
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//try creating another user with same email address
		json.put(UserConstants2.USER_NAME, "usertestCreateUserDuplicateEmail2");
		request = getPostUsersRequest("", json, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testCreateUserEmailDifferentCase() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		JSONObject json = new JSONObject();
		json.put(UserConstants2.USER_NAME, "testCaseEmail");
		json.put(UserConstants2.FULL_NAME, "username_testCreateUserEmailDifferentCase");
		json.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		json.put(UserConstants2.EMAIL, "duplicateemail@example.com");
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//try creating another user with same email address but different case
		json.put(UserConstants2.EMAIL, "DUPLICATEEMAIL@example.com");
		json.put(UserConstants2.USER_NAME, "testCaseEmail2");
		request = getPostUsersRequest("", json, true);
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
			String username = "bad" + invalidChars.charAt(i) + "name";

			// create user
			JSONObject json = new JSONObject();
			json.put(UserConstants2.USER_NAME, username);
			json.put(UserConstants2.FULL_NAME, "Tom");
			json.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
			WebRequest request = getPostUsersRequest("", json, true);
			WebResponse response = webConversation.getResponse(request);
			assertEquals("Should fail with name: " + username, HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

		}
	}

	@Test
	public void testCreateDeleteUsers() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		JSONObject json = new JSONObject();
		json.put(UserConstants2.USER_NAME, "user" + System.currentTimeMillis());
		json.put(UserConstants2.FULL_NAME, "username_" + System.currentTimeMillis());
		json.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contain user location", responseObject.has(UserConstants2.LOCATION));

		// check user details
		String location = responseObject.getString(UserConstants2.LOCATION);

		request = getGetUsersRequest(location, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", json.getString(UserConstants2.USER_NAME), responseObject.getString(UserConstants2.USER_NAME));
		assertEquals("Invalid user name", json.getString(UserConstants2.FULL_NAME), responseObject.getString(UserConstants2.FULL_NAME));
		assertFalse("Response shouldn't contain password", responseObject.has(UserConstants2.PASSWORD));

		// check if user can authenticate
		request = getGetUsersRequest("", true);

		// create some project contents to test that delete user also deletes all project contents
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUser(json.getString(UserConstants2.USER_NAME));
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
		request = getDeleteUsersRequest(location, true);
		setAuthentication(request, json.getString(UserConstants2.USER_NAME), json.getString(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User could not delete his own account, response: " + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testCreateDeleteRights() throws IOException, SAXException, CoreException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		JSONObject json = new JSONObject();
		json.put(UserConstants2.USER_NAME, "testCrDelRights");
		json.put(UserConstants2.FULL_NAME, "username_" + System.currentTimeMillis());
		json.put(UserConstants2.EMAIL, "test@test_" + System.currentTimeMillis());
		json.put("workspace", "workspace_" + System.currentTimeMillis());
		json.put(UserConstants2.PASSWORD, "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should container user name", responseObject.has(UserConstants2.USER_NAME));

		String username = responseObject.getString(UserConstants2.USER_NAME);
		String location = responseObject.getString(UserConstants2.LOCATION);

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, json.getString(UserConstants2.USER_NAME), json.getString(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User has no admin privileges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// add admin rights
		AuthorizationService.addUserRight(username, "/users");
		AuthorizationService.addUserRight(username, "/users/*");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, json.getString(UserConstants2.USER_NAME), json.getString(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User has no admin privileges", HttpURLConnection.HTTP_OK, response.getResponseCode());

		// delete admin rights
		AuthorizationService.removeUserRight(username, "/users");
		AuthorizationService.removeUserRight(username, "/users/*");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, json.getString(UserConstants2.USER_NAME), json.getString(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User has no admin privileges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// delete user
		request = getDeleteUsersRequest(location, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testUpdateUsers() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		JSONObject json = new JSONObject();
		json.put(UserConstants2.USER_NAME, "user" + System.currentTimeMillis());
		json.put(UserConstants2.FULL_NAME, "username_" + System.currentTimeMillis());
		String oldPass = "pass_" + System.currentTimeMillis();
		json.put(UserConstants2.PASSWORD, oldPass);
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contain user location", responseObject.has(UserConstants2.LOCATION));

		String location = responseObject.getString(UserConstants2.LOCATION);

		// update user
		JSONObject updateBody = new JSONObject();
		updateBody.put(UserConstants2.FULL_NAME, "usernameUpdate_" + System.currentTimeMillis());
		updateBody.put(UserConstants2.OLD_PASSWORD, oldPass);
		updateBody.put(UserConstants2.PASSWORD, "passUpdate_" + System.currentTimeMillis());

		request = getPutUsersRequest(location, updateBody, true);

		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check user details
		request = getGetUsersRequest(location, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", json.get(UserConstants2.USER_NAME), responseObject.getString(UserConstants2.USER_NAME));
		assertEquals("Invalid user name", updateBody.getString(UserConstants2.FULL_NAME), responseObject.getString(UserConstants2.FULL_NAME));
		assertFalse("Response shouldn't contain password", responseObject.has(UserConstants2.PASSWORD));

		// check if user can authenticate and does not have admin role
		request = getGetUsersRequest("", true);
		setAuthentication(request, json.getString(UserConstants2.USER_NAME), updateBody.getString(UserConstants2.PASSWORD));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privilegges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// delete user
		request = getDeleteUsersRequest(location, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testResetUser() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		JSONObject json = new JSONObject();
		String username = "user" + System.currentTimeMillis();
		json.put(UserConstants2.USER_NAME, username);
		json.put(UserConstants2.FULL_NAME, "username" + System.currentTimeMillis());
		json.put("roles", "admin");
		String oldPass = "pass_" + System.currentTimeMillis();
		json.put(UserConstants2.PASSWORD, oldPass);
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user location", responseObject.has(UserConstants2.LOCATION));

		String location = responseObject.getString(UserConstants2.LOCATION);

		//reset password
		String newPass = "passUpdate_" + System.currentTimeMillis();
		json = new JSONObject();
		json.put(UserConstants2.USER_NAME, username);
		json.put(UserConstants2.PASSWORD, newPass);
		json.put(UserConstants2.RESET, "true");
		request = getPostUsersRequest(location, json, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check if user can authenticate
		request = getGetUsersRequest(location, true);
		setAuthentication(request, json.getString(UserConstants2.USER_NAME), newPass);
		response = webConversation.getResponse(request);
		assertEquals("User cannot log in with new credentials", HttpURLConnection.HTTP_OK, response.getResponseCode());

		// delete user
		request = getDeleteUsersRequest(location, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testCreateUser() throws JSONException, IOException, SAXException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		String username1 = "user1" + System.currentTimeMillis();
		String password = "pass" + System.currentTimeMillis();

		// create user
		JSONObject json = new JSONObject();
		json.put(UserConstants2.USER_NAME, username1);
		json.put(UserConstants2.PASSWORD, password);
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue(responseObject.has(UserConstants2.LOCATION));
		assertEquals("/users/" + username1, responseObject.getString(UserConstants2.LOCATION));
		assertTrue(responseObject.getBoolean(UserConstants2.HAS_PASSWORD));
		assertFalse(responseObject.getBoolean(UserConstants2.EMAIL_CONFIRMED));
		assertTrue(responseObject.has(UserConstants2.USER_NAME));
		assertEquals(username1, responseObject.getString(UserConstants2.USER_NAME));
		assertTrue(responseObject.has(UserConstants2.FULL_NAME));
		assertEquals(username1, responseObject.getString(UserConstants2.FULL_NAME));
	}

	@Test
	public void testChangeUserName() throws JSONException, IOException, SAXException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		String username1 = "user1" + System.currentTimeMillis();
		String password = "pass" + System.currentTimeMillis();

		// create user
		JSONObject json = new JSONObject();
		json.put(UserConstants2.USER_NAME, username1);
		json.put(UserConstants2.PASSWORD, password);
		WebRequest request = getPostUsersRequest("", json, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user location", responseObject.has(UserConstants2.LOCATION));

		String location = responseObject.getString(UserConstants2.LOCATION);

		String username2 = "user2" + System.currentTimeMillis();
		JSONObject updateBody = new JSONObject();
		updateBody.put(UserConstants2.USER_NAME, username2);

		request = getPutUsersRequest(location, updateBody, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// When you rename the user, the location becomes /users/{newUser}
		location = "/users/" + username2;

		request = getGetUsersRequest(location, true);
		setAuthentication(request, username2, password);
		response = webConversation.getResponse(request);
		assertEquals("User could not authenticate with new login" + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		responseObject = new JSONObject(response.getText());
		assertEquals("New login wasn't returned in user details", username2, responseObject.get(UserConstants2.USER_NAME));
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
