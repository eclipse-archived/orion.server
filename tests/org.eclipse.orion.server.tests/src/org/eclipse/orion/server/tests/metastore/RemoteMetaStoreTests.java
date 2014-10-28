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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import org.eclipse.orion.internal.server.hosting.SiteConfigurationConstants;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.eclipse.orion.server.useradmin.UserConstants;
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
 * Create a data set on a remote Orion server by creating users, workspaces, projects, 
 * site configurations and operations. The end result is a set of data that can be used to test
 * migration.  
 * This test is not intended to be added to the nightly Orion JUnit tests, 
 *  
 * @author Anthony Hunter
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RemoteMetaStoreTests {

	/**
	 * The historical versions of Orion metadata, with orion.conf settings in parentheses:
	 * Orion 3.0 (orion.core.metastore=legacy)
	 * Orion 4.0 (orion.core.metastore=simple)
	 * Orion 6.0 (orion.core.metastore=simple2)
	 * Orion 7.0 (only one version supported so no longer an orion.conf setting.)
	 */
	protected static int orionMetastoreVersion = 7;

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
	protected int createFile(WebConversation webConversation, String login, String password, String workspace, String project) throws IOException, JSONException, URISyntaxException, SAXException {
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
	protected int createFolder(WebConversation webConversation, String login, String password, String workspace, String project) throws IOException, JSONException, URISyntaxException, SAXException {
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
	 * Create a git close on the Orion server for the test user. Also creates an operation in the metastore for the user.
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
	protected int createGitClone(WebConversation webConversation, String login, String password, String workspace, String project) throws URISyntaxException, IOException, JSONException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		String name = "ahunter orion";
		JSONObject json = new JSONObject();
		json.put("GitUrl", "https://github.com/ahunter-orion/ahunter-orion.github.com.git");
		json.put("Location", "/workspace/" + getWorkspaceId(login, workspace));
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/gitapi/clone/"), IOUtilities.toInputStream(json.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.getResponseCode());

		JSONObject responseJsonObject = new JSONObject(response.getText());
		String location = responseJsonObject.getString("Location");
		JSONObject task = new JSONObject();
		task.put("expires", System.currentTimeMillis() + 86400000);
		task.put("Name", "Cloning repository " + name);
		json = new JSONObject();
		json.put(location, task);
		request = new PutMethodWebRequest(getOrionServerURI("/prefs/user/operations/"), IOUtilities.toInputStream(json.toString()), "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());

		System.out.println("Created Git Clone: " + name + " at Location: " + location);
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
	protected int createPluginsPref(WebConversation webConversation, String login, String password) throws IOException, JSONException, URISyntaxException, SAXException {
		assertEquals(HttpURLConnection.HTTP_OK, login(webConversation, login, password));

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("http://mamacdon.github.io/0.3/plugins/bugzilla/plugin.html", true);
		WebRequest request = new PutMethodWebRequest(getOrionServerURI("/prefs/user/plugins"), IOUtilities.toInputStream(jsonObject.toString()), "application/json");
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
	protected int createProject(WebConversation webConversation, String login, String password, String workspace, String projectName) throws IOException, JSONException, URISyntaxException, SAXException {
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
	protected int createSite(WebConversation webConversation, String login, String password, String workspace, String site) throws URISyntaxException, IOException, JSONException, SAXException {
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
	protected int createUser(WebConversation webConversation, String login, String password) throws IOException, URISyntaxException, SAXException, JSONException {
		WebRequest request = new PostMethodWebRequest(getOrionServerURI("/users"));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		request.setParameter(UserConstants.KEY_LOGIN, login);
		request.setParameter(UserConstants2.PASSWORD, password);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject jsonObject = new JSONObject(response.getText());
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
	protected int createWorkspace(WebConversation webConversation, String login, String password, String workspace) throws URISyntaxException, IOException, JSONException, SAXException {
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
			//orionTestName = "anthony";
		}
		return orionTestName;
	}

	/**
	 * Get the workspace id based on the login name. The legacy metastore uses the login name, the
	 * simple metastore uses the login name and workspace name.
	 * @param login
	 * @return
	 */
	protected String getWorkspaceId(String login, String workspaceName) {
		if (RemoteMetaStoreTests.orionMetastoreVersion == 3) {
			return login;
		}
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
		request.setParameter(UserConstants.KEY_LOGIN, login);
		request.setParameter(UserConstants2.PASSWORD, password);
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
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, createGitClone(webConversation, getOrionTestName(), getOrionTestName(), "Orion Content", "Project"));
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
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, noprojects, noprojects, "Orion Content"));

		// a user with two projects
		String twoprojects = "tp" + getOrionTestName();
		webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, twoprojects, twoprojects));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, twoprojects, twoprojects, "Orion Content"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twoprojects, twoprojects, "Orion Content", "Project One"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createFolder(webConversation, twoprojects, twoprojects, "Orion Content", "Project One"));
		assertEquals(HttpURLConnection.HTTP_OK, createFile(webConversation, twoprojects, twoprojects, "Orion Content", "Project One"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twoprojects, twoprojects, "Orion Content", "Project Two"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createFolder(webConversation, twoprojects, twoprojects, "Orion Content", "Project Two"));
		assertEquals(HttpURLConnection.HTTP_OK, createFile(webConversation, twoprojects, twoprojects, "Orion Content", "Project Two"));

		// a user with a project with two sites
		String twosites = "ts" + getOrionTestName();
		webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, twosites, twosites));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, twosites, twosites, "Orion Content"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twosites, twosites, "Orion Content", "Project"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createSite(webConversation, twosites, twosites, "Orion Content", "Site One"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createSite(webConversation, twosites, twosites, "Orion Content", "Site Two"));

		// a user with a project with two workspaces
		String twoworkspaces = "tw" + getOrionTestName();
		webConversation = new WebConversation();
		assertEquals(HttpURLConnection.HTTP_OK, createUser(webConversation, twoworkspaces, twoworkspaces));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, twoworkspaces, twoworkspaces, "Orion Content"));
		assertEquals(HttpURLConnection.HTTP_OK, createWorkspace(webConversation, twoworkspaces, twoworkspaces, "Second Workspace"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twoworkspaces, twoworkspaces, "Orion Content", "Project"));
		assertEquals(HttpURLConnection.HTTP_CREATED, createProject(webConversation, twoworkspaces, twoworkspaces, "Second Workspace", "Second Project"));
	}
}
