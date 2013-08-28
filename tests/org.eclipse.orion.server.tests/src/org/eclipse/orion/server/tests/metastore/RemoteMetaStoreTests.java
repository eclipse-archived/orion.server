/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Test the MetaStore API on a remote Orion server by creating users, workspaces and projects.
 * This test is not intended to be added to the nightly Orion JUnit tests, 
 * it is a test tool for the server API as well as being useful to create test data for migration
 * tests.
 *  
 * @author Anthony Hunter
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RemoteMetaStoreTests {

	/**
	 * The metastore is either legacy or simple. The values in orion.conf are:
	 * orion.core.metastore=legacy (Orion 3.0)
	 * orion.core.metastore=simple (Orion 4.0)
	 */
	protected final static boolean orionMetastoreLegacy = true;

	protected static String orionTestName = null;

	/**
	 * Create a project on the Orion server for the test user.
	 * 
	 * @param webConversation
	 * @param login
	 * @param password
	 * @param projectName
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws URISyntaxException
	 * @throws SAXException 
	 */
	protected int createProject(WebConversation webConversation, String login, String password, String projectName) throws IOException, JSONException, URISyntaxException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		JSONObject jsonObject = new JSONObject();
		InputStream inputStream = IOUtilities.toInputStream(jsonObject.toString());
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/workspace/" + getWorkspaceId(login)), inputStream, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		jsonObject = new JSONObject(response.getText());
		String location = jsonObject.getString("ContentLocation");
		String name = jsonObject.getString("Name");
		System.out.println("Created Project: " + name + " at Location: " + location);
		return response.getResponseCode();
	}

	/**
	 * Create a test user on the Orion server.
	 * 
	 * @param webConversation
	 * @param login
	 * @param password
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws SAXException 
	 * @throws JSONException
	 */
	protected int createUser(WebConversation webConversation, String login, String password) throws IOException, URISyntaxException, SAXException, JSONException {
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/users"));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		request.setParameter("login", login);
		request.setParameter("password", password);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
		String location = jsonObject.getString("Location");
		String name = jsonObject.getString("Name");
		System.out.println("Created User: " + name + " at Location: " + location);
		return response.getResponseCode();
	}

	/**
	 * Create a workspace on the Orion server for the test user.
	 * 
	 * @param webConversation
	 * @param login
	 * @param password
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException 
	 */
	protected int createWorkspace(WebConversation webConversation, String login, String password) throws URISyntaxException, IOException, JSONException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/workspace"));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, "Orion Content");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
		String location = jsonObject.getString("Location");
		String name = jsonObject.getString("Name");
		System.out.println("Created Workspace: " + name + " at Location: " + location);
		return response.getResponseCode();
	}

	/**
	 * Get the URI of the remote Orion server. Replace the URI to test different servers.
	 * 
	 * @param path
	 * @return
	 * @throws URISyntaxException
	 */
	protected String getOrionServerURI(String path) throws URISyntaxException {
		//String orionServerHostname = "vottachrh6x64.ottawa.ibm.com";
		String orionServerHostname = "localhost";
		int orionServerPort = 8080;

		URI orionServerURI = new URI("http", null, orionServerHostname, orionServerPort, path, null, null);
		return orionServerURI.toString();
	}

	/**
	 * Get the test name used for several tests. The username and password will be this name, 
	 * as well as the workspace name and project name. It is meant to be a unique name so the tests
	 * can be repeated a number of times on the same server without having to delete content.
	 * 
	 * @return The test name.
	 */
	protected String getOrionTestName() {
		if (orionTestName == null) {
			orionTestName = "test" + System.currentTimeMillis();
			//orionTestName = "test" + "123456";
		}
		return orionTestName;
	}

	/**
	 * Get the workspace id based on the login name. The legacy metastore uses the login name, the
	 * simple metastore uses the login name and workspace name.
	 * @param login
	 * @return
	 */
	protected String getWorkspaceId(String login) {
		if (orionMetastoreLegacy) {
			return login;
		}
		return login + "-Orion Content";
	}

	/**
	* Login to the Orion server with the provided login and password.
	* 
	* @param webConversation
	* @param login
	* @param password
	* @return
	* @throws URISyntaxException
	* @throws IOException
	* @throws SAXException
	*/
	protected int login(WebConversation webConversation, String login, String password) throws URISyntaxException, IOException, SAXException {
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/login/form"));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		request.setParameter("login", login);
		request.setParameter("password", password);
		WebResponse response = webConversation.getResponse(request);
		return response.getResponseCode();
	}

	/**
	 * Create a test user on the Orion server. This test needs to run first.
	 * 
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException 
	 */
	@Test
	public void testACreateUser() throws URISyntaxException, IOException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, getOrionTestName(), getOrionTestName()));
	}

	/**
	 * Verify the form based authentication used to login into the server. 
	 * This authentication is required for each server call, so this test is run second.
	 *  
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws SAXException 
	 */
	@Test
	public void testBVerifyFormBasedLogin() throws IOException, URISyntaxException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, getOrionTestName(), getOrionTestName()));
	}

	/**
	 * Create a workspace on the Orion server for the test user.
	 * 
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException 
	 */
	@Test
	public void testCreateAWorkspace() throws URISyntaxException, IOException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, getOrionTestName(), getOrionTestName()));
	}

	/**
	 * Create a project on the Orion server for the test user.
	 * 
	 * @throws IOException
	 * @throws JSONException
	 * @throws URISyntaxException
	 * @throws SAXException 
	 */
	@Test
	public void testCreateProject() throws IOException, JSONException, URISyntaxException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, getOrionTestName(), getOrionTestName(), getOrionTestName()));
	}

	/**
	 * Get the list of projects for the test user.
	 * 
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws JSONException
	 * @throws SAXException 
	 */
	@Test
	public void testGetProjects() throws IOException, URISyntaxException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, getOrionTestName(), getOrionTestName()));

		WebRequest request = new GetMethodWebRequest(getOrionServerURI("/workspace/" + getWorkspaceId(getOrionTestName())));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
		JSONArray projects = jsonObject.getJSONArray("Projects");
		String name = jsonObject.getString("Name");
		if (projects.length() == 0) {
			System.out.println("Found zero Projects in workspace named: " + name);
		} else {
			System.out.print("Found Projects in workspace named: " + name + " at locations: [ ");
			for (int i = 0; i < projects.length(); i++) {
				JSONObject project = projects.getJSONObject(i);
				System.out.print(project.getString("Location") + " ");
			}
			System.out.println("]");
		}
	}

	/**
	 * Get the list of workspaces for the test user.
	 * 
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws JSONException
	 * @throws SAXException 
	 */
	@Test
	public void testGetWorkspaces() throws IOException, URISyntaxException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, getOrionTestName(), getOrionTestName()));

		WebRequest request = new GetMethodWebRequest(getOrionServerURI("/workspace"));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
		JSONArray workspaces = jsonObject.getJSONArray("Workspaces");
		String name = jsonObject.getString("Name");
		if (workspaces.length() == 0) {
			System.out.println("Found zero Workspaces for user: " + name);
		} else {
			System.out.print("Found Workspaces for user: " + name + " at locations: [ ");
			for (int i = 0; i < workspaces.length(); i++) {
				JSONObject workspace = workspaces.getJSONObject(i);
				System.out.print(workspace.getString("Location") + " ");
			}
			System.out.println("]");
		}
	}

	/**
	 * Create additional users, workspaces and projects to test migration.
	 * 
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws JSONException
	 * @throws SAXException 
	 */
	@Test
	public void testZCreateMigrationContent() throws IOException, URISyntaxException, JSONException, SAXException {
		// a user with no workspace or projects
		String none = "n" + getOrionTestName();
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, none, none));

		// a user with no projects
		String noprojects = "np" + getOrionTestName();
		webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, noprojects, noprojects));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, noprojects, noprojects));

		// a user with two projects
		String twoprojects = "tp" + getOrionTestName();
		webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, twoprojects, twoprojects));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, twoprojects, twoprojects));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twoprojects, twoprojects, twoprojects + 1));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twoprojects, twoprojects, twoprojects + 2));
	}
}
