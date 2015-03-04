/*******************************************************************************
 * Copyright (c) 2013, 2015 IBM Corporation and others.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import org.eclipse.orion.internal.server.hosting.SiteConfigurationConstants;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.users.UserConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Create a data set on a remote Orion server by creating users, workspaces, projects, site configurations and
 * operations. The end result is a set of operations that can be used to test a server and observe Orion metadata store
 * operations.
 *
 * This test is not intended to be added to the nightly Orion JUnit tests.
 *
 * @author Anthony Hunter
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RemoteMetaStoreTests {

	protected static String orionTestName = null;

	/**
	 * Create a file in a project on the Orion server for the test user.
	 *
	 * @param webConversation
	 * @param login
	 * @param password
	 * @param workspace
	 * @param project
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws URISyntaxException
	 * @throws SAXException
	 */
	protected int createFile(WebConversation webConversation, String login, String password, String workspace, String project) throws IOException,
			JSONException, URISyntaxException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("Directory", "false");
		jsonObject.put("Name", "file.json");
		jsonObject.put("LocalTimeStamp", "0");
		String parent = "/file/" + getWorkspaceId(login, workspace) + "/" + project + "/folder/";
		WebRequest request = new PostMethodWebRequest(getOrionServerURI(parent), IOUtilities.toInputStream(jsonObject.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, "file.json");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		String file = "/file/" + getWorkspaceId(login, workspace) + "/" + project + "/folder/file.json";
		jsonObject = new JSONObject();
		jsonObject.put("Description", "This is a simple JSON file");
		String fileContent = jsonObject.toString(4);
		request = new PutMethodWebRequest(getOrionServerURI(file), IOUtilities.toInputStream(fileContent), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		System.out.println("Created File: " + parent + "file.json");
		return response.getResponseCode();
	}

	/**
	 * Create a folder in a project on the Orion server for the test user.
	 *
	 * @param webConversation
	 * @param login
	 * @param password
	 * @param workspace
	 * @param project
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws URISyntaxException
	 * @throws SAXException
	 */
	protected int createFolder(WebConversation webConversation, String login, String password, String workspace, String project) throws IOException,
			JSONException, URISyntaxException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("Directory", "true");
		jsonObject.put("Name", "folder");
		jsonObject.put("LocalTimeStamp", "0");
		String parent = "/file/" + getWorkspaceId(login, workspace) + "/" + project;
		WebRequest request = new PostMethodWebRequest(getOrionServerURI(parent), IOUtilities.toInputStream(jsonObject.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		System.out.println("Created Folder: " + parent + "/folder");
		return response.getResponseCode();
	}

	/**
	 * Create a git close on the Orion server for the test user. Also creates an operation in the metastore for the
	 * user.
	 *
	 * @param webConversation
	 * @param login
	 * @param password
	 * @param workspace
	 * @param project
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	protected int createGitClone(WebConversation webConversation, String login, String password, String workspace, String project, String gitUrl)
			throws URISyntaxException, IOException, JSONException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		JSONObject json = new JSONObject();
		json.put("GitUrl", gitUrl);
		json.put("Name", project);
		json.put("Location", "/workspace/" + getWorkspaceId(login, workspace));
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/gitapi/clone/"), IOUtilities.toInputStream(json.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());

		JSONObject responseJsonObject = new JSONObject(response.getText());
		String location = responseJsonObject.getString("Location");
		String status = responseJsonObject.getString("type");

		// a task job has been createdby the post request, next check for completion status
		while (status != null && !status.equals("loadend")) {
			// Wait 300 milliseconds before submitting a task status request
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				break;
			}

			// Request the task status
			request = new GetMethodWebRequest(getOrionServerURI(location));
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			responseJsonObject = new JSONObject(response.getText());
			status = responseJsonObject.getString("type");
		}

		location = responseJsonObject.getJSONObject("Result").getJSONObject("JsonData").getString("Location");

		System.out.println("Created Git Clone: " + project + " at Location: " + location);
		return response.getResponseCode();
	}

	/**
	 * Create a plugins preference on the Orion server for the test user.
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
	protected int createPluginsPref(WebConversation webConversation, String login, String password) throws IOException, JSONException, URISyntaxException,
			SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("http://mamacdon.github.io/0.3/plugins/bugzilla/plugin.html", true);
		WebRequest request = new PutMethodWebRequest(getOrionServerURI("/prefs/user/plugins"), IOUtilities.toInputStream(jsonObject.toString()),
				"application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());

		System.out.println("Created Preference /prefs/user/plugins");
		return response.getResponseCode();
	}

	/**
	 * Create a project on the Orion server for the test user.
	 *
	 * @param webConversation
	 * @param login
	 * @param password
	 * @param workspace
	 * @param projectName
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws URISyntaxException
	 * @throws SAXException
	 */
	protected int createProject(WebConversation webConversation, String login, String password, String workspace, String projectName) throws IOException,
			JSONException, URISyntaxException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		JSONObject jsonObject = new JSONObject();
		InputStream inputStream = IOUtilities.toInputStream(jsonObject.toString());
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/workspace/" + getWorkspaceId(login, workspace)), inputStream, "UTF-8");
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
	 * Create a workspace on the Orion server for the test user.
	 *
	 * @param webConversation
	 * @param login
	 * @param password
	 * @param workspace
	 * @param site
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	protected int createSite(WebConversation webConversation, String login, String password, String workspace, String site) throws URISyntaxException,
			IOException, JSONException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		JSONObject json = new JSONObject();
		json.put(SiteConfigurationConstants.KEY_WORKSPACE, getWorkspaceId(login, workspace));
		json.put(ProtocolConstants.KEY_NAME, site);
		json.put(SiteConfigurationConstants.KEY_HOST_HINT, site.toLowerCase().replaceAll(" ", "-"));
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/site"), IOUtilities.toInputStream(json.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
		String location = jsonObject.getString("Location");
		String name = jsonObject.getString("Name");
		System.out.println("Created Site: " + name + " at Location: " + location);
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
	protected int createUser(WebConversation webConversation, String login, String password) throws IOException, URISyntaxException, SAXException,
			JSONException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(UserConstants.USER_NAME, login);
		jsonObject.put(UserConstants.PASSWORD, password);
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/users"), IOUtilities.toInputStream(jsonObject.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		jsonObject = new JSONObject(response.getText());
		String location = jsonObject.getString("Location");
		System.out.println("Created User: " + login + " at Location: " + location);
		return response.getResponseCode();
	}

	/**
	 * Create a workspace on the Orion server for the test user.
	 *
	 * @param webConversation
	 * @param login
	 * @param password
	 * @param workspace
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	protected int createWorkspace(WebConversation webConversation, String login, String password, String workspace) throws URISyntaxException, IOException,
			JSONException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/workspace"));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		request.setHeaderField(ProtocolConstants.HEADER_SLUG, workspace);
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
		// String orionServerHostname = "vottachrh6x64.ottawa.ibm.com";
		String orionServerHostname = "localhost";
		int orionServerPort = 8080;

		URI orionServerURI = new URI("http", null, orionServerHostname, orionServerPort, path, null, null);
		return orionServerURI.toString();
	}

	/**
	 * Get the test name used for several tests. The username and password will be this name, as well as the workspace
	 * name and project name. It is meant to be a unique name so the tests can be repeated a number of times on the same
	 * server without having to delete content.
	 *
	 * @return The test name.
	 */
	protected String getOrionTestName() {
		if (orionTestName == null) {
			orionTestName = "test" + System.currentTimeMillis();
			// orionTestName = "anthony";
		}
		return orionTestName;
	}

	/**
	 * Get the workspace id based on the login name. the simple metastore uses the login name and workspace name.
	 *
	 * @param login
	 * @return
	 */
	protected String getWorkspaceId(String login, String workspaceName) {
		return login.concat("-").concat(workspaceName.replace(" ", "").replace("#", ""));
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
		request.setParameter(UserConstants.USER_NAME.toLowerCase(), login);
		request.setParameter(UserConstants.PASSWORD.toLowerCase(), password);
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
		assertEquals(HttpURLConnection.HTTP_CREATED, createUser(webConversation, getOrionTestName(), getOrionTestName()));
	}

	/**
	 * Update a test user on the Orion server.
	 *
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	@Test
	public void testAUpdateUser() throws URISyntaxException, IOException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, updateUser(webConversation, getOrionTestName(), getOrionTestName(), "Test User"));
	}

	/**
	 * Verify the form based authentication used to login into the server. This authentication is required for each
	 * server call, so this test is run second.
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
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, getOrionTestName(), getOrionTestName(), "Orion Content"));
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
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, getOrionTestName(), getOrionTestName(), "Orion Content", "Project"));
	}

	/**
	 * Create a site on the Orion server for the test user.
	 *
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	@Test
	public void testCreateSite() throws URISyntaxException, IOException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_CREATED, createSite(webConversation, getOrionTestName(), getOrionTestName(), "Orion Content", "First Site"));
	}

	/**
	 * Create a plugins preference on the Orion server for the test user.
	 *
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	@Test
	public void testCreateTPluginsPref() throws URISyntaxException, IOException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, createPluginsPref(webConversation, getOrionTestName(), getOrionTestName()));
	}

	/**
	 * Create a folder in a project on the Orion server for the test user.
	 *
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	@Test
	public void testCreateUFolder() throws URISyntaxException, IOException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_CREATED, createFolder(webConversation, getOrionTestName(), getOrionTestName(), "Orion Content", "Project"));
	}

	/**
	 * Create a file in a project on the Orion server for the test user.
	 *
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	@Test
	public void testCreateVFile() throws URISyntaxException, IOException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createFile(webConversation, getOrionTestName(), getOrionTestName(), "Orion Content", "Project"));
	}

	/**
	 * Create a git clone in a project on the Orion server for the test user.
	 *
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 * @throws SAXException
	 */
	@Test
	public void testCreateWGitClone() throws URISyntaxException, IOException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		String gitUrl = "https://github.com/ahunter-orion/ahunter-orion.github.com.git";
		String gitProjectName = "ahunter-orion.github";
		assertEquals(HttpURLConnection.HTTP_OK,
				createGitClone(webConversation, getOrionTestName(), getOrionTestName(), "Orion Content", gitProjectName, gitUrl));
	}

	/**
	 * Get the plugins preference for the test user.
	 *
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws JSONException
	 * @throws SAXException
	 */
	@Test
	public void testGetPluginsPref() throws IOException, URISyntaxException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, getOrionTestName(), getOrionTestName()));

		WebRequest request = new GetMethodWebRequest(getOrionServerURI("/prefs/user/plugins"));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
		if (jsonObject.length() == 0) {
			System.out.println("Found zero plugin preferences for user: " + getOrionTestName());
		} else {
			System.out.print("Found plugin preferences for user: " + getOrionTestName() + " values: [ ");
			for (@SuppressWarnings("unchecked")
			Iterator<String> iterator = jsonObject.keys(); iterator.hasNext();) {
				System.out.print(iterator.next() + " ");
			}
			System.out.println("]");
		}
	}

	/**
	 * Get the list of projects in the specified workspace for the specified user.
	 *
	 * @param workspace
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws JSONException
	 * @throws SAXException
	 */
	@Test
	public void testGetProjects() throws IOException, URISyntaxException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, getOrionTestName(), getOrionTestName()));

		WebRequest request = new GetMethodWebRequest(getOrionServerURI("/workspace/" + getWorkspaceId(getOrionTestName(), "Orion Content")));
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
	public void testGetSites() throws IOException, URISyntaxException, JSONException, SAXException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, getOrionTestName(), getOrionTestName()));

		WebRequest request = new GetMethodWebRequest(getOrionServerURI("/site"));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
		JSONArray siteConfigurations = jsonObject.getJSONArray("SiteConfigurations");
		if (siteConfigurations.length() == 0) {
			System.out.println("Found zero Sites for user: " + getOrionTestName());
		} else {
			System.out.print("Found Sites for user: " + getOrionTestName() + " at locations: [ ");
			for (int i = 0; i < siteConfigurations.length(); i++) {
				JSONObject workspace = siteConfigurations.getJSONObject(i);
				System.out.print(workspace.getString("Location") + " ");
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
	 * Test search against the newly created project. This test fails every time as you need to wait five minutes for
	 * the Indexer to run before you can search on a file you just saved.
	 *
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	public void testSearch() throws URISyntaxException, IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, getOrionTestName(), getOrionTestName()));

		String query = "?sort=Path%20asc&rows=10000&start=0&q=ahunter+Location:/file/" + getOrionTestName() + "-OrionContent/ahunter-orion.github.com/*";
		WebRequest request = new GetMethodWebRequest(getOrionServerURI("/filesearch") + query);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
		assertTrue(jsonObject.has("response"));
		JSONObject responseJson = jsonObject.getJSONObject("response");
		assertTrue(responseJson.has("numFound"));
		assertEquals("No results found as the indexer has not run yet", 3, responseJson.getInt("numFound"));
	}

	/**
	 * Create additional users, workspaces and projects to test migration.
	 *
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws JSONException
	 * @throws SAXException
	 */
	// @Test
	public void testZCreateMigrationContent() throws IOException, URISyntaxException, JSONException, SAXException {
		// a user with no workspace or projects
		String none = "tnow" + System.currentTimeMillis();
		WebConversation webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, none, none));
		assertEquals(HttpURLConnection.HTTP_OK, updateUser(webConversation, none, none, "Test User No Workspace or Projects"));

		// a user with no projects
		String noprojects = "twnp" + System.currentTimeMillis();
		webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, noprojects, noprojects));
		assertEquals(HttpURLConnection.HTTP_OK, updateUser(webConversation, noprojects, noprojects, "Test User Workspace and No Projects"));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, noprojects, noprojects, "Orion Content"));

		// a user with two projects
		String twoprojects = "tpto" + System.currentTimeMillis();
		webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, twoprojects, twoprojects));
		assertEquals(HttpURLConnection.HTTP_OK, updateUser(webConversation, twoprojects, twoprojects, "Test User Two Projects"));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, twoprojects, twoprojects, "Orion Content"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twoprojects, twoprojects, "Orion Content", "Project One"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createFolder(webConversation, twoprojects, twoprojects, "Orion Content", "Project One"));
		assertEquals(HttpURLConnection.HTTP_OK, createFile(webConversation, twoprojects, twoprojects, "Orion Content", "Project One"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twoprojects, twoprojects, "Orion Content", "Project Two"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createFolder(webConversation, twoprojects, twoprojects, "Orion Content", "Project Two"));
		assertEquals(HttpURLConnection.HTTP_OK, createFile(webConversation, twoprojects, twoprojects, "Orion Content", "Project Two"));

		// a user with a project with two sites
		String twosites = "tsto" + System.currentTimeMillis();
		webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, twosites, twosites));
		assertEquals(HttpURLConnection.HTTP_OK, updateUser(webConversation, twosites, twosites, "Test User Two Sites"));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, twosites, twosites, "Orion Content"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twosites, twosites, "Orion Content", "Project"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createSite(webConversation, twosites, twosites, "Orion Content", "Site One"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createSite(webConversation, twosites, twosites, "Orion Content", "Site Two"));

		// a user with a project with two workspaces
		// do not currently support two workspaces
		// String twoworkspaces = "twto" + System.currentTimeMillis();
		// webConversation = new WebConversation();
		// assertEquals(HttpURLConnection.HTTP_OK, updateUser(webConversation,
		// twoworkspaces, twoworkspaces, "Test User Two Sites"));
		// assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation,
		// twoworkspaces, twoworkspaces));
		// assertEquals(HttpURLConnection.HTTP_OK,
		// createWorkspace(webConversation, twoworkspaces, twoworkspaces,
		// "Orion Content"));
		// assertEquals(HttpURLConnection.HTTP_OK,
		// createWorkspace(webConversation, twoworkspaces, twoworkspaces,
		// "Second Workspace"));
		// assertEquals(HttpURLConnection.HTTP_CREATED,
		// createProject(webConversation, twoworkspaces, twoworkspaces,
		// "Orion Content", "Project"));
		// assertEquals(HttpURLConnection.HTTP_CREATED,
		// createProject(webConversation, twoworkspaces, twoworkspaces,
		// "Second Workspace", "Second Project"));
	}

	public void testZZCreateLongRunningTasks() throws InterruptedException {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					String testUser = "user" + System.currentTimeMillis();
					WebConversation webConversation = new WebConversation();
					assertEquals(HttpURLConnection.HTTP_CREATED, createUser(webConversation, testUser, testUser));
					assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, testUser, testUser, "Orion Content"));
					String gitUrl = "https://github.com/ahunter-orion/ahunter-orion.github.com.git";
					String gitProjectName = "org.eclipse.orion.client";
					assertEquals(HttpURLConnection.HTTP_OK, createGitClone(webConversation, testUser, testUser, "Orion Content", gitProjectName, gitUrl));
				} catch (JSONException e) {
					fail(e.getLocalizedMessage());
				} catch (IOException e) {
					fail(e.getLocalizedMessage());
				} catch (URISyntaxException e) {
					fail(e.getLocalizedMessage());
				} catch (SAXException e) {
					fail(e.getLocalizedMessage());
				}
			}
		};

		Thread threads[] = new Thread[10];
		for (int i = 0; i < threads.length; i++) {
			Thread thread = new Thread(runnable, "LongRunningTaskKiller-" + i);
			thread.start();
			threads[i] = thread;
			Thread.sleep(2);
		}

		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				// just continue
			}
		}
	}

	/**
	 * Update the user with a email address and provided full name
	 *
	 * @param webConversation
	 * @param login
	 * @param password
	 * @param fullName
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws SAXException
	 * @throws JSONException
	 */
	protected int updateUser(WebConversation webConversation, String login, String password, String fullName) throws URISyntaxException, IOException,
			SAXException, JSONException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		String email = login + "@example.com";
		JSONObject json = new JSONObject();
		json.put(UserConstants.EMAIL, email);
		json.put(UserConstants.FULL_NAME, fullName);
		WebRequest request = new PutMethodWebRequest(getOrionServerURI("/users/" + login), IOUtilities.toInputStream(json.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		System.out.println("Updated User: " + login + " with email: " + email);
		return response.getResponseCode();

	}
}
