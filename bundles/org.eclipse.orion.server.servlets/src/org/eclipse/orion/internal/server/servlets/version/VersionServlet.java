/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.internal.server.servlets.version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servlet to handle requests for information about the orion server.
 * 
 * @author Anthony Hunter
 */
public class VersionServlet extends OrionServlet {

	private static final long serialVersionUID = -1426745453574711075L;

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		OrionServlet.writeJSONResponse(req, resp, getVersionJson());
	}

	/**
	 * Build the JSONObject to be returned in the response.
	 */
	private Object getVersionJson() {
		JSONObject jsonObject = new JSONObject();
		try {
		String build = getBuildId();
		jsonObject.put("build", build);
		} catch (JSONException e) {/* ignore */
		}
		return jsonObject;
	}

	/**
	 * Get the build id for the orion application by using the about.properties from the org.eclipse.orion.server.core plugin. The maven build assigns a
	 * property with the build timestamp when the build runs.
	 * 
	 * @return the build id.
	 */
	private String getBuildId() {
		String version = System.getProperty("eclipse.buildId", "unknown"); //$NON-NLS-1$
		try {
			URL url = new URL("platform:/plugin/org.eclipse.orion.server.core/about.properties");
			InputStream inputStream = url.openConnection().getInputStream();
			BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
			String inputLine;

			while ((inputLine = in.readLine()) != null) {
				if (inputLine.startsWith("Build id:")) {
					break;
				}
			}
			in.close();
			// The Build Id line is in the format: "Build id: 8.0.0-v20150223-1056\n\"
			if (inputLine.length() > 30) {
				version = inputLine.substring(inputLine.indexOf(':') + 2, inputLine.length() - 3);
			}
		} catch (IOException e) {
			// just ignore and use the calculated version from the property
		}
		return version;
	}

}
