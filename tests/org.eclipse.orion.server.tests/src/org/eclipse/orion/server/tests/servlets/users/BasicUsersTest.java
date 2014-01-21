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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
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
		assertTrue("Invalid format of response.", responseObject.has("users"));
		JSONArray usersArray = responseObject.getJSONArray("users");
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
		params.put("login", "testDupUser");
		params.put("Name", "username_testCreateDuplicateUser");

		params.put("password", "pass_" + System.currentTimeMillis());
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
		params.put("login", "testDupEmail");
		params.put("Name", "username_testCreateUserDuplicateEmail");
		params.put("password", "pass_" + System.currentTimeMillis());
		params.put("email", "username@example.com");
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		//try creating another user with same email address
		params.put("login", "usertestCreateUserDuplicateEmail2");
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
		params.put("login", "testCaseEmail");
		params.put("Name", "username_testCreateUserEmailDifferentCase");
		params.put("password", "pass_" + System.currentTimeMillis());
		params.put("email", "duplicateemail@example.com");
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		//try creating another user with same email address but different case
		params.put("email", "DUPLICATEEMAIL@example.com");
		params.put("login", "testCaseEmail2");
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
			params.put("login", name);
			params.put("Name", "Tom");

			params.put("password", "pass_" + System.currentTimeMillis());
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
		params.put("login", "user" + System.currentTimeMillis());
		params.put("Name", "username_" + System.currentTimeMillis());
		//		params.put("email", "test@test_" + System.currentTimeMillis());
		//		params.put("workspace", "workspace_" + System.currentTimeMillis());

		params.put("password", "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contain user location", responseObject.has(ProtocolConstants.KEY_LOCATION));

		// check user details
		String location = responseObject.getString(ProtocolConstants.KEY_LOCATION);

		request = getAuthenticatedRequest(location, METHOD_GET, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", params.get("login"), responseObject.getString("login"));
		assertEquals("Invalid user name", params.get("Name"), responseObject.getString("Name"));
		//		assertEquals("Invalid user email", params.get("email"), responseObject.getString("email"));
		//		assertEquals("Invalid user workspace", params.get("workspace"), responseObject.getString("workspace"));
		assertFalse("Response shouldn't contain password", responseObject.has("password"));

		// check if user can authenticate
		request = getGetUsersRequest("", true);

		// delete user
		request = getAuthenticatedRequest(location, METHOD_DELETE, true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User could not delete his own account, response: " + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testCreateDeleteRights() throws IOException, SAXException, CoreException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put("login", "testCrDelRights");
		params.put("name", "username_" + System.currentTimeMillis());
		params.put("email", "test@test_" + System.currentTimeMillis());
		params.put("workspace", "workspace_" + System.currentTimeMillis());
		params.put("password", "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user uid", responseObject.has("uid"));

		String uid = responseObject.getString("uid");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privileges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());
		// add admin rights
		//TODO

		AuthorizationService.addUserRight(uid, "/users");
		AuthorizationService.addUserRight(uid, "/users/*");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User tried to use his admin role but did not get the valid response: " + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// delete admin rights
		AuthorizationService.removeUserRight(uid, "/users");
		AuthorizationService.removeUserRight(uid, "/users/*");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
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
		params.put("login", "user" + System.currentTimeMillis());
		params.put("Name", "username_" + System.currentTimeMillis());
		//		params.put("email", "test@test_" + System.currentTimeMillis());
		//		params.put("workspace", "workspace_" + System.currentTimeMillis());
		params.put("roles", "admin");
		String oldPass = "pass_" + System.currentTimeMillis();
		params.put("password", oldPass);
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user location", responseObject.has(ProtocolConstants.KEY_LOCATION));

		String location = responseObject.getString(ProtocolConstants.KEY_LOCATION);

		// update user
		JSONObject updateBody = new JSONObject();
		updateBody.put("Name", "usernameUpdate_" + System.currentTimeMillis());
		updateBody.put("oldPassword", oldPass);
		updateBody.put("password", "passUpdate_" + System.currentTimeMillis());
		updateBody.put("roles", "");

		request = getAuthenticatedRequest(location, METHOD_PUT, true, null, updateBody);

		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check user details
		request = getAuthenticatedRequest(location, METHOD_GET, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", params.get("login"), responseObject.getString("login"));
		assertEquals("Invalid user name", updateBody.getString("Name"), responseObject.getString("Name"));
		//		assertEquals("Invalid user email", updatedParams.get("email"), responseObject.getString("email"));
		//		assertEquals("Invalid user workspace", updatedParams.get("workspace"), responseObject.getString("workspace"));
		//		JSONArray roles = responseObject.getJSONArray("roles");
		//		assertEquals("Invalid number of user roles", 0, roles.length());
		assertFalse("Response shouldn't contain password", responseObject.has("password"));

		// check if user can authenticate and does not have admin role
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), updateBody.getString("password"));
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
		params.put("login", username);
		params.put("Name", "username" + System.currentTimeMillis());
		//		params.put("email", "test@test_" + System.currentTimeMillis());
		//		params.put("workspace", "workspace_" + System.currentTimeMillis());
		params.put("roles", "admin");
		String oldPass = "pass_" + System.currentTimeMillis();
		params.put("password", oldPass);
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user location", responseObject.has(ProtocolConstants.KEY_LOCATION));

		String location = responseObject.getString(ProtocolConstants.KEY_LOCATION);

		//reset password
		String newPass = "passUpdate_" + System.currentTimeMillis();
		params = new HashMap<String, String>();
		params.put("login", username);
		params.put("password", newPass);
		params.put(UserConstants.KEY_RESET, "true");
		request = getPostUsersRequest("", params, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check if user can authenticate
		request = getAuthenticatedRequest(location, METHOD_GET, true);
		setAuthentication(request, params.get("login"), newPass);
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
		params.put("login", login1);
		params.put("password", password);
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responseObject = new JSONObject(response.getText());

		assertTrue("Response should contian user location", responseObject.has(ProtocolConstants.KEY_LOCATION));

		String location = responseObject.getString(ProtocolConstants.KEY_LOCATION);

		String login2 = "login2" + System.currentTimeMillis();
		JSONObject updateBody = new JSONObject();
		updateBody.put("login", login2);

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
		assertEquals("New login wasn't returned in user details", login2, responseObject.get("login"));
	}

	@Test
	public void testUserProperties() {
		String sampleUser1 = "login1" + System.currentTimeMillis();
		String sampleUser2 = "login2" + System.currentTimeMillis();
		String login = "login" + System.currentTimeMillis();
		String password = "password" + System.currentTimeMillis();
		User user = createUser(login, password);
		createUser(sampleUser1, "password");
		createUser(sampleUser2, "password");

		IOrionCredentialsService userAdmin = UserServiceHelper.getDefault().getUserStore();

		String propertyName = "property" + System.currentTimeMillis();
		String propertyValue = "value" + System.currentTimeMillis();
		user.addProperty(propertyName, propertyValue);
		userAdmin.updateUser(user.getUid(), user);
		User updatedUser = userAdmin.getUser("uid", user.getUid());
		assertEquals("The property was not set", propertyValue, updatedUser.getProperty(propertyName));
		Set<User> foundUsers = userAdmin.getUsersByProperty(propertyName, propertyValue, false, false);
		assertEquals("Invalid number of users found", 1, foundUsers.size());
		User foundUser = foundUsers.iterator().next();
		assertEquals("Invalid user found", user.getUid(), foundUser.getUid());
		assertEquals("Found user doesn't have the property expected", propertyValue, foundUser.getProperty(propertyName));
		String valuePattern = ".*" + propertyValue.substring(3, propertyValue.length() - 1) + ".";
		foundUsers = userAdmin.getUsersByProperty(propertyName, valuePattern, true, false);
		assertEquals("Invalid number of users found", 1, foundUsers.size());
		foundUser = foundUsers.iterator().next();
		assertEquals("Invalid user found", user.getUid(), foundUser.getUid());
		assertEquals("Found user doesn't have the property expected", propertyValue, foundUser.getProperty(propertyName));
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
	public void setAdminRights(User adminUser) throws CoreException {
		//by default allow 'admin' to modify all users data
		AuthorizationService.addUserRight(adminUser.getUid(), "/users");
		AuthorizationService.addUserRight(adminUser.getUid(), "/users/*");
	}

	@Override
	public void setTestUserRights(User testUser) throws CoreException {
		//by default allow 'test' to modify his own data
		AuthorizationService.addUserRight(testUser.getUid(), "/users/" + testUser.getUid());
	}
}
