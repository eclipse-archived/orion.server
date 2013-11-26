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
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handler for requests to the docker server.
 *  
 * @author Anthony Hunter
 * @author Bogdan Gheorghe
 */
public class DockerHandler extends ServletResourceHandler<String> {

	// TODO: need a proper way to setup the docker location
	protected final static String dockerLocation = "http://localhost:9443";

	protected ServletResourceHandler<IStatus> statusHandler;

	public DockerHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	private boolean handleDockerVersionRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		try {
			URI dockerLocationURI = new URI(dockerLocation);
			DockerServer dockerServer = new DockerServer(dockerLocationURI);
			DockerVersion dockerVersion = dockerServer.getDockerVersion();
			switch (dockerVersion.getStatusCode()) {
				case BAD_PARAMETER :
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, dockerVersion.getStatusMessage());
					return false;
				case SERVER_ERROR :
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dockerVersion.getStatusMessage());
					return false;
				case OK :
					JSONObject jsonObject = new JSONObject();
					jsonObject.put(DockerVersion.VERSION, dockerVersion.getVersion());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				default :
					return false;
			}
		} catch (URISyntaxException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "URISyntaxException with request", e));
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "JSONException with request", e));
		}
	}

	private boolean handleGetRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		String[] pathSplit = path.split("\\/", 2);
		String dockerRequest = pathSplit[0];
		if (dockerRequest.equals(DockerVersion.VERSION_PATH)) {
			return handleDockerVersionRequest(request, response);
		}
		return false;
	}

	private boolean handlePostRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		String dockerRequest = "";
		try {
			JSONObject requestObject = OrionServlet.readJSONRequest(request);
			dockerRequest = (String) requestObject.get("dockerCmd");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		if (dockerRequest.equals("start")) {
			//start up container
			System.out.println("START CONTAINER");
			return true;
		} else if (dockerRequest.equals("stop")) {
			//stop container
			System.out.println("STOP CONTAINER");
			return true;
		}
		return false;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		switch (getMethod(request)) {
			case GET :
				return handleGetRequest(request, response, path);
			case POST :
				return handlePostRequest(request, response);
			default :
				return false;
		}
	}

}
