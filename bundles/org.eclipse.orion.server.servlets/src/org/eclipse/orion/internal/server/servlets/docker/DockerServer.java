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

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
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
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerVersionURL = new URL(dockerServer.toString() + "/version");
			httpURLConnection = (HttpURLConnection) dockerVersionURL.openConnection();
			int responseCode = httpURLConnection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				dockerVersion.setStatusCode(DockerResponse.StatusCode.OK);
			} else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
				dockerVersion.setStatusCode(DockerResponse.StatusCode.BAD_PARAMETER);
			} else {
				dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
				dockerVersion.setStatusMessage(httpURLConnection.getResponseMessage());
			}
			String result = readString(httpURLConnection.getInputStream());
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
		} catch (IOException e) {
			dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerVersion.setStatusMessage(e.getLocalizedMessage());
		} catch (JSONException e) {
			dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerVersion.setStatusMessage(e.getLocalizedMessage());
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerVersion;
	}

	private String readString(InputStream inputStream) {
		try {
			char[] chars = new char[1024];
			Charset utf8 = Charset.forName("UTF-8");
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream, utf8);
			StringBuilder stringBuilder = new StringBuilder();
			int count;
			while ((count = inputStreamReader.read(chars, 0, chars.length)) != -1) {
				stringBuilder.append(chars, 0, count);
			}
			inputStreamReader.close();
			return stringBuilder.toString();
		} catch (IOException e) {
			throw new RuntimeException(e.getLocalizedMessage());
		}
	}
}
