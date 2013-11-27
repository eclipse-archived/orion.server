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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		try {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$

			String command = "";
			JSONObject requestObject = OrionServlet.readJSONRequest(request);
			String dockerRequest = (String) requestObject.get("dockerCmd");
			if (dockerRequest.equals("process")) {
				command = (String) requestObject.get("line");
			}

			String user = request.getRemoteUser();
			URI dockerLocationURI = new URI("http://localhost:9443");
			DockerServer dockerServer = new DockerServer(dockerLocationURI);

			// make sure docker is running
			DockerVersion dockerVersion = dockerServer.getDockerVersion();
			if (dockerVersion.getStatusCode() != DockerResponse.StatusCode.OK) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Docker is not running at " + dockerLocationURI.toString(), null));
			}
			logger.debug("Docker Server " + dockerLocationURI.toString() + " is running version " + dockerVersion.getVersion());

			if (dockerRequest.equals("start")) {
				// get the container for the user
				DockerContainer dockerContainer = dockerServer.getDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.OK) {
					// user does not have a container, create one
					dockerContainer = dockerServer.createDockerContainer("ubuntu", user);
					if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.CREATED) {
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
					}
					logger.debug("Created Docker Container " + dockerContainer.getId() + " for user " + user);
				}

				// start the container for the user
				dockerContainer = dockerServer.startDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.STARTED) {
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
				}
				logger.debug("Started Docker Container " + dockerContainer.getId() + " for user " + user);

				return true;
			} else if (dockerRequest.equals("stop")) {
				// get the container for the user
				DockerContainer dockerContainer = dockerServer.getDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.OK) {
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
				}

				// stop the container for the user
				dockerContainer = dockerServer.stopDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.STOPPED) {
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
				}
				logger.debug("Stopped Docker Container " + dockerContainer.getId() + " for user " + user);

				return true;
			} else if (dockerRequest.equals("process")) {
				// get the container for the user
				DockerContainer dockerContainer = dockerServer.getDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.OK) {
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
				}

				// stop the container for the user
				DockerResponse dockerResponse = dockerServer.attachDockerContainer(user, command);
				if (dockerResponse.getStatusCode() != DockerResponse.StatusCode.OK) {
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
				}
				logger.debug("Attach Docker Container " + dockerContainer.getId() + "successful, result is");
				logger.debug(dockerResponse.getStatusMessage());
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("result", dockerResponse.getStatusMessage());
				return true;
			}
		} catch (URISyntaxException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "URISyntaxException with request", e));
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "JSONException with request", e));
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
