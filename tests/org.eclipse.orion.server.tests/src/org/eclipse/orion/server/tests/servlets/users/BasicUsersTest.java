/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
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
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject responceObject = new JSONObject(response.getText());
		assertTrue("Invalid format of responce.", responceObject.has("users"));
		JSONArray usersArray = responceObject.getJSONArray("users");
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
		params.put("name", "username_" + System.currentTimeMillis());
		params.put("email", "test@test_" + System.currentTimeMillis());
		params.put("workspace", "workspace_" + System.currentTimeMillis());
		params.put("roles", "admin");
		params.put("password", "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check user details
		request = getGetUsersRequest(params.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responceObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", params.get("login"), responceObject.getString("login"));
		assertEquals("Invalid user name", params.get("name"), responceObject.getString("name"));
		assertEquals("Invalid user email", params.get("email"), responceObject.getString("email"));
		assertEquals("Invalid user workspace", params.get("workspace"), responceObject.getString("workspace"));
		JSONArray roles = responceObject.getJSONArray("roles");
		assertEquals("Invalid number of user roles", 1, roles.length());
		assertEquals("User does not have role given", "admin", roles.get(0).toString());
		assertFalse("Responce shouldn't contain password", responceObject.has("password"));

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User tried to use his admin role but did not get the valid responce", HttpURLConnection.HTTP_OK, response.getResponseCode());

		// delete user
		request = getDeleteUsersRequest(params.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

	}

	@Test
	public void testCreateDeleteRoles() throws IOException, SAXException {
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
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privilidges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());
		// add admin role
		Map<String, String> rolesParams = new HashMap<String, String>();
		rolesParams.put("roles", "admin");
		request = getPutUsersRequest("roles/" + params.get("login"), rolesParams, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User tried to use his admin role but did not get the valid responce", HttpURLConnection.HTTP_OK, response.getResponseCode());

		// delete admin role
		request = getDeleteUsersRequest("roles/" + params.get("login"), rolesParams, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check if user can authenticate
		request = getGetUsersRequest("", true);
		setAuthentication(request, params.get("login"), params.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privilidges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// delete user
		request = getDeleteUsersRequest(params.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

	}

	@Test
	public void testUpdateUsers() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		// create user
		Map<String, String> params = new HashMap<String, String>();
		params.put("login", "user_" + System.currentTimeMillis());
		params.put("name", "username_" + System.currentTimeMillis());
		params.put("email", "test@test_" + System.currentTimeMillis());
		params.put("workspace", "workspace_" + System.currentTimeMillis());
		params.put("roles", "admin");
		params.put("password", "pass_" + System.currentTimeMillis());
		WebRequest request = getPostUsersRequest("", params, true);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// update user
		Map<String, String> updatedParams = new HashMap<String, String>();
		updatedParams.put("login", "userUpdate_" + System.currentTimeMillis());
		updatedParams.put("name", "usernameUpdate_" + System.currentTimeMillis());
		updatedParams.put("email", "testUpdate@test_" + System.currentTimeMillis());
		updatedParams.put("workspace", "workspaceUpdate_" + System.currentTimeMillis());
		updatedParams.put("password", "passUpdate_" + System.currentTimeMillis());
		updatedParams.put("roles", "");
		request = getPutUsersRequest(params.get("login"), updatedParams, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// check user details
		request = getGetUsersRequest(updatedParams.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responceObject = new JSONObject(response.getText());
		assertEquals("Invalid user login", updatedParams.get("login"), responceObject.getString("login"));
		assertEquals("Invalid user name", updatedParams.get("name"), responceObject.getString("name"));
		assertEquals("Invalid user email", updatedParams.get("email"), responceObject.getString("email"));
		assertEquals("Invalid user workspace", updatedParams.get("workspace"), responceObject.getString("workspace"));
		JSONArray roles = responceObject.getJSONArray("roles");
		assertEquals("Invalid number of user roles", 0, roles.length());
		assertFalse("Responce shouldn't contain password", responceObject.has("password"));

		// check if user can authenticate and does not have admin role
		request = getGetUsersRequest("", true);
		setAuthentication(request, updatedParams.get("login"), updatedParams.get("password"));
		response = webConversation.getResponse(request);
		assertEquals("User with no roles has admin privilidges", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

		// delete user
		request = getDeleteUsersRequest(updatedParams.get("login"), true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

	}

}
