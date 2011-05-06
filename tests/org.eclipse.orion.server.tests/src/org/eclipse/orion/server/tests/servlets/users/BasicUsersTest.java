/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others 
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.prefs.BackingStoreException;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class BasicUsersTest extends UsersTest {

	@Before
	public void setUp() throws CoreException {
		setUpAuthorization();
	}

	@Override
	public void setUpAuthorization() throws CoreException {
		createUser("test", "test");
		createUser("admin", "admin");

		//by default allow 'admin' to modify all users data
		AuthorizationService.addUserRight("admin", "/users");
		AuthorizationService.addUserRight("admin", "/users/*");

		//by default allow 'test' to modify his own data
		AuthorizationService.addUserRight("test", "/users/test");
	}

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
		assertFalse("Returned a jsonObject in reponce where FORBIDDEN should be returned", wasJson);

	}

	@Test
	public void testCreateDeleteUsers() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put("login", "user_" + System.currentTimeMillis());
		params.put("Name", "username_" + System.currentTimeMillis());
		//		params.put("email", "test@test_" + System.currentTimeMillis());
		//		params.put("workspace", "workspace_" + System.currentTimeMillis());

		params.put("password", "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check user details
		request = getGetUsersRequest(params.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", params.get("login"), responseObject.getString("login"));
		assertEquals("Invalid user name", params.get("Name"), responseObject.getString("Name"));
		//		assertEquals("Invalid user email", params.get("email"), responseObject.getString("email"));
		//		assertEquals("Invalid user workspace", params.get("workspace"), responseObject.getString("workspace"));
		assertFalse("Response shouldn't contain password", responseObject.has("password"));

		// check if user can authenticate
		request = getGetUsersRequest("", true);

		// delete user
		request = getDeleteUsersRequest(params.get("login"), true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User could not delete his own account, response: " + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

	}

	@Test
	public void testCreateDeleteRights() throws IOException, SAXException, CoreException, JSONException, BackingStoreException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put("login", "user_" + System.currentTimeMillis());
		params.put("name", "username_" + System.currentTimeMillis());
		params.put("email", "test@test_" + System.currentTimeMillis());
		params.put("workspace", "workspace_" + System.currentTimeMillis());
		params.put("password", "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privileges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());
		// add admin rights
		//TODO

		AuthorizationService.addUserRight(params.get("login"), "/users");
		AuthorizationService.addUserRight(params.get("login"), "/users/*");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User tried to use his admin role but did not get the valid response: " + response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// delete admin rights
		AuthorizationService.removeUserRight(params.get("login"), "/users");
		AuthorizationService.removeUserRight(params.get("login"), "/users/*");

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privileges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// delete user
		request = getDeleteUsersRequest(params.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

	}

	@Test
	public void testUpdateUsers() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put("login", "user_" + System.currentTimeMillis());
		params.put("Name", "username_" + System.currentTimeMillis());
		//		params.put("email", "test@test_" + System.currentTimeMillis());
		//		params.put("workspace", "workspace_" + System.currentTimeMillis());
		params.put("roles", "admin");
		String oldPass = "pass_" + System.currentTimeMillis();
		params.put("password", oldPass);
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// update user
		JSONObject updateBody = new JSONObject();
		updateBody.put("Name", "usernameUpdate_" + System.currentTimeMillis());
		updateBody.put("oldPassword", oldPass);
		updateBody.put("password", "passUpdate_" + System.currentTimeMillis());
		updateBody.put("roles", "");

		request = getPutUsersRequest(params.get("login"), updateBody, true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check user details
		request = getGetUsersRequest(params.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
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
		request = getDeleteUsersRequest(params.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());
	}
}
