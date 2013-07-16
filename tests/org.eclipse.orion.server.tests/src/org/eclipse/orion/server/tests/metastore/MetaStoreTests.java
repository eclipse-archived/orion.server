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
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class MetaStoreTests {

	protected String orionUser;

	protected URI getOrionServerURI(String path) throws URISyntaxException {
		//String orionServerHostname = "vottachrh6x64.ottawa.ibm.com";
		String orionServerHostname = "localhost";
		int orionServerPort = 8080;

		return new URI("http", "", orionServerHostname, orionServerPort, path, "", "");
	}

	protected String getOrionUser() {
		if (orionUser == null) {
			orionUser = "test" + System.currentTimeMillis();
		}
		return orionUser;
	}

	protected int login(HttpClient httpClient) throws ClientProtocolException, IOException, URISyntaxException {
		HttpPost httpPost = new HttpPost(getOrionServerURI("/login/form"));
		httpPost.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("login", "test"));
		nvps.add(new BasicNameValuePair("password", "test"));
		httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));

		HttpResponse httpResponse = httpClient.execute(httpPost);
		HttpEntity httpEntity = httpResponse.getEntity();
		if (httpEntity != null) {
			String result = EntityUtils.toString(httpEntity);
			System.out.println("- " + result);
		}
		return httpResponse.getStatusLine().getStatusCode();
	}

	@Test
	public void testVerifyFormBasedLogin() throws ClientProtocolException, IOException, URISyntaxException {

		HttpClient httpClient = new DefaultHttpClient();
		try {
			assertEquals(HttpStatus.SC_OK, login(httpClient));
		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testVerifyWorkspace() throws ClientProtocolException, IOException, URISyntaxException {

		HttpClient httpClient = new DefaultHttpClient();
		try {
			assertEquals(HttpStatus.SC_OK, login(httpClient));

			HttpGet httpGet = new HttpGet(getOrionServerURI("/workspace"));
			httpGet.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
			HttpResponse httpResponse = httpClient.execute(httpGet);
			System.out.println("Workspace Get: " + httpResponse.getStatusLine());
			HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) {
				String done = EntityUtils.toString(httpEntity);
				System.out.println("- " + done);
			}
			assertEquals(HttpStatus.SC_OK, httpResponse.getStatusLine().getStatusCode());

		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testCreateProject() throws ClientProtocolException, IOException, JSONException, URISyntaxException {

		HttpClient httpClient = new DefaultHttpClient();
		try {
			assertEquals(HttpStatus.SC_OK, login(httpClient));

			HttpPost httpPost = new HttpPost(getOrionServerURI("/workspace/test"));
			httpPost.setHeader(ProtocolConstants.HEADER_ORION_VERSION, "1");
			httpPost.setHeader(HTTP.CONTENT_TYPE, "application/json");
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("Name", "project12");
			StringEntity stringEntity = new StringEntity(jsonObject.toString());
			stringEntity.setContentType("application/json");
			httpPost.setEntity(stringEntity);
			HttpResponse httpResponse = httpClient.execute(httpPost);
			System.out.println("Create Project Post: " + httpResponse.getStatusLine());
			HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) {
				String done = EntityUtils.toString(httpEntity);
				System.out.println("- " + done);
			}
			assertEquals(HttpStatus.SC_CREATED, httpResponse.getStatusLine().getStatusCode());
		} finally {
			httpClient.getConnectionManager().shutdown();
		}
	}

}
