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
package org.eclipse.orion.internal.server.servlets.docker;

import java.io.IOException;
import java.net.URI;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility to handle requests to and from the Docker Remote API.
 * 
 * @author Anthony Hunter
 */
public class DockerServer {

	private URI dockerServer;

	public DockerServer(URI dockerServer) {
		super();
		this.dockerServer = dockerServer;
	}

	/**
	 * Create the HTTP client session.
	 * 
	 * @return the HTTP client session.
	 */
	protected HttpClient createHttpClient() {
		ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager();
		cm.setMaxTotal(100);
		return new DefaultHttpClient(cm);
	}

	public URI getDockerServer() {
		return dockerServer;
	}

	/**
	 * Get the Docker version.
	 * 
	 * @return the docker version.
	 */
	public DockerVersion getDockerVersion() {
		DockerVersion dockerVersion = new DockerVersion();
		HttpClient httpClient = createHttpClient();
		try {
			HttpGet httpGet = new HttpGet(dockerServer.toString() + "/version");
			HttpResponse httpResponse = httpClient.execute(httpGet);
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				dockerVersion.setStatusCode(DockerResponse.StatusCode.OK);
			} else if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
				dockerVersion.setStatusCode(DockerResponse.StatusCode.BAD_PARAMETER);
			} else {
				dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
				dockerVersion.setStatusMessage(httpResponse.getStatusLine().getReasonPhrase());
			}
			HttpEntity httpEntity = httpResponse.getEntity();
			if (httpEntity != null) {
				String result = EntityUtils.toString(httpEntity);
				JSONObject jsonObject = new JSONObject(result);
				if (jsonObject.has(DockerVersion.VERSION)) {
					dockerVersion.setVersion(jsonObject.getString(DockerVersion.VERSION));
				}
				if (jsonObject.has(DockerVersion.GIT_COMMIT)) {
					dockerVersion.setGitCommit(jsonObject.getString(DockerVersion.GIT_COMMIT));
				}
				if (jsonObject.has(DockerVersion.GO_VERSION)) {
					dockerVersion.setGoVersion(jsonObject.getString(DockerVersion.GO_VERSION));
				}
			}
		} catch (ClientProtocolException e) {
			dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerVersion.setStatusMessage(e.getLocalizedMessage());
		} catch (IOException e) {
			dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerVersion.setStatusMessage(e.getLocalizedMessage());
		} catch (JSONException e) {
			dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerVersion.setStatusMessage(e.getLocalizedMessage());
		} finally {
			httpClient.getConnectionManager().shutdown();
		}
		return dockerVersion;
	}
}
