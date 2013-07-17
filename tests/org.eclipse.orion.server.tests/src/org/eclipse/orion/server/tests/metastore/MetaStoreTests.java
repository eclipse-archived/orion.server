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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MetaStoreTests {

	protected static String orionTestName = null;

	protected HttpClient createHttpClient() {
		ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager();
		cm.setMaxTotal(100);
		return new DefaultHttpClient(cm);
	}

	protected URI getOrionServerURI(String path) throws URISyntaxException {
		String orionServerHostname = "vottachrh6x64.ottawa.ibm.com";
		//String orionServerHostname = "localhost";
		int orionServerPort = 8080;

		return new URI("http", null, orionServerHostname, orionServerPort, path, null, null);
	}

	protected String getOrionTestName() {
		if (orionTestName == null) {
			orionTestName = "test" + System.currentTimeMillis();
		}
		return orionTestName;
	}

	protected int login(HttpClient httpClient) throws ClientProtocolException, IOException, URISyntaxException {
		HttpPost httpPost = new HttpPost(getOrionServerURI("/login/form"));
		httpPost.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("login", getOrionTestName()));
		nvps.add(new BasicNameValuePair("password", getOrionTestName()));
		httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));

		HttpResponse httpResponse = httpClient.execute(httpPost);
		EntityUtils.consume(httpResponse.getEntity());
		return httpResponse.getStatusLine().getStatusCode();
	}

	@Test
	public void testCreateAUser() throws URISyntaxException, ParseException, IOException, JSONException {

		HttpClient httpClient = createHttpClient();
		try {
			HttpPost httpPost = new HttpPost(getOrionServerURI("/users"));
			httpPost.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("login", getOrionTestName()));
			nvps.add(new BasicNameValuePair("password", getOrionTestName()));
			nvps.add(new BasicNameValuePair("email", getOrionTestName() + "@test.com"));
			httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
			HttpResponse httpResponse = httpClient.execute(httpPost);
			HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) {
				String result = EntityUtils.toString(httpEntity);
				JSONObject jsonObject = new JSONObject(result);
				String location = jsonObject.getString("Location");
				String name = jsonObject.getString("Name");
				System.out.println("Created User: " + name + " at Location: " + location);
			}
			assertEquals(HttpStatus.SC_OK, httpResponse.getStatusLine().getStatusCode());
		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testCreateAWorkspace() throws URISyntaxException, ParseException, IOException, JSONException {

		HttpClient httpClient = createHttpClient();
		try {
			assertEquals(HttpStatus.SC_OK, login(httpClient));

			HttpPost httpPost = new HttpPost(getOrionServerURI("/workspace"));
			httpPost.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
			httpPost.setHeader(ProtocolConstants.HEADER_SLUG, "Orion Content");
			HttpResponse httpResponse = httpClient.execute(httpPost);
			HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) {
				String result = EntityUtils.toString(httpEntity);
				JSONObject jsonObject = new JSONObject(result);
				String location = jsonObject.getString("Location");
				String name = jsonObject.getString("Name");
				System.out.println("Created Workspace: " + name + " at Location: " + location);
			}
			assertEquals(HttpStatus.SC_OK, httpResponse.getStatusLine().getStatusCode());
		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testCreateProject() throws ClientProtocolException, IOException, JSONException, URISyntaxException {

		HttpClient httpClient = createHttpClient();
		try {
			assertEquals(HttpStatus.SC_OK, login(httpClient));

			HttpPost httpPost = new HttpPost(getOrionServerURI("/workspace/" + getOrionTestName()));
			httpPost.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
			httpPost.setHeader(HTTP.CONTENT_TYPE, "application/json");
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("Name", getOrionTestName());
			StringEntity stringEntity = new StringEntity(jsonObject.toString());
			stringEntity.setContentType("application/json");
			httpPost.setEntity(stringEntity);
			HttpResponse httpResponse = httpClient.execute(httpPost);
			HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) {
				String result = EntityUtils.toString(httpEntity);
				jsonObject = new JSONObject(result);
				String location = jsonObject.getString("Location");
				String name = jsonObject.getString("Name");
				System.out.println("Created Project: " + name + " at Location: " + location);
			}
			assertEquals(HttpStatus.SC_CREATED, httpResponse.getStatusLine().getStatusCode());
		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testGetProjects() throws ClientProtocolException, IOException, URISyntaxException, JSONException {

		HttpClient httpClient = createHttpClient();
		try {
			assertEquals(HttpStatus.SC_OK, login(httpClient));

			HttpGet httpGet = new HttpGet(getOrionServerURI("/workspace/" + getOrionTestName()));
			httpGet.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
			HttpResponse httpResponse = httpClient.execute(httpGet);
			HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) {
				String result = EntityUtils.toString(httpEntity);
				JSONObject jsonObject = new JSONObject(result);
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
			assertEquals(HttpStatus.SC_OK, httpResponse.getStatusLine().getStatusCode());

		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testGetWorkspaces() throws ClientProtocolException, IOException, URISyntaxException, JSONException {

		HttpClient httpClient = createHttpClient();
		try {
			assertEquals(HttpStatus.SC_OK, login(httpClient));

			HttpGet httpGet = new HttpGet(getOrionServerURI("/workspace"));
			httpGet.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
			HttpResponse httpResponse = httpClient.execute(httpGet);
			HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) {
				String result = EntityUtils.toString(httpEntity);
				JSONObject jsonObject = new JSONObject(result);
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
			assertEquals(HttpStatus.SC_OK, httpResponse.getStatusLine().getStatusCode());

		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testVerifyFormBasedLogin() throws ClientProtocolException, IOException, URISyntaxException {

		HttpClient httpClient = createHttpClient();
		try {
			assertEquals(HttpStatus.SC_OK, login(httpClient));
		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

}
