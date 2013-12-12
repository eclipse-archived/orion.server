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
package org.eclipse.orion.server.docker.servlets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.docker.ClientSocket;
import org.eclipse.orion.server.docker.DockerContainer;
import org.eclipse.orion.server.docker.DockerServer;
import org.eclipse.orion.server.docker.DockerVersion;
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
	private final static String dockerLocation = "http://localhost:2424";

	private Map<String, ClientSocket> sockets;

	private String boundary = "byuTZNvet4LtXx5jdzVbHP";
	protected ServletResourceHandler<IStatus> statusHandler;

	public DockerHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
		this.sockets = new HashMap<String, ClientSocket>();
	}

	private boolean handleDockerVersionRequest(HttpServletRequest request,
			HttpServletResponse response) throws ServletException {
		try {
			URI dockerLocationURI = new URI(dockerLocation);
			DockerServer dockerServer = new DockerServer(dockerLocationURI);
			DockerVersion dockerVersion = dockerServer.getDockerVersion();
			switch (dockerVersion.getStatusCode()) {
			case BAD_PARAMETER:
				response.sendError(HttpServletResponse.SC_BAD_REQUEST,
						dockerVersion.getStatusMessage());
				return false;
			case SERVER_ERROR:
				response.sendError(
						HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						dockerVersion.getStatusMessage());
				return false;
			case OK:
				JSONObject jsonObject = new JSONObject();
				jsonObject.put(DockerVersion.VERSION,
						dockerVersion.getVersion());
				OrionServlet.writeJSONResponse(request, response, jsonObject);
				return true;
			default:
				return false;
			}
		} catch (URISyntaxException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"URISyntaxException with request", e));
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"JSONException with request", e));
		}
	}

	private boolean handleGetRequest(HttpServletRequest request,
			HttpServletResponse response, String path) throws ServletException {
		String[] pathSplit = path.split("\\/", 2);
		String dockerRequest = pathSplit[0];
		if (dockerRequest.equals(DockerVersion.VERSION_PATH)) {
			return handleDockerVersionRequest(request, response);
		}
		return false;
	}

	private boolean handlePostRequest(HttpServletRequest request,
			HttpServletResponse response) throws ServletException {
		try {
			Logger logger = LoggerFactory
					.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$

			String command = "";
			JSONObject requestObject = OrionServlet.readJSONRequest(request);
			String dockerRequest = (String) requestObject.get("dockerCmd");
			if (dockerRequest.equals("process")) {
				command = (String) requestObject.get("line");
			}

			String user = request.getRemoteUser();

			URI dockerLocationURI = new URI(dockerLocation);
			DockerServer dockerServer = new DockerServer(dockerLocationURI);

			// make sure docker is running
			DockerVersion dockerVersion = dockerServer.getDockerVersion();
			if (dockerVersion.getStatusCode() != DockerResponse.StatusCode.OK) {
				return statusHandler.handleRequest(
						request,
						response,
						new ServerStatus(IStatus.ERROR,
								HttpServletResponse.SC_BAD_REQUEST,
								"Docker is not running at "
										+ dockerLocationURI.toString(), null));
			}
			logger.debug("Docker Server " + dockerLocation
					+ " is running version " + dockerVersion.getVersion());

			if (dockerRequest.equals("start")) {
				// get the volumes (projects) for the user
				List<String> volumes = getDockerVolumes(user);
				// get the container for the user
				DockerContainer dockerContainer = dockerServer
						.getDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.OK) {
					// user does not have a container, create one
					dockerContainer = dockerServer.createDockerContainer(
							"ubuntu", user, volumes);
					if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.CREATED) {
						return statusHandler.handleRequest(request, response,
								new ServerStatus(IStatus.ERROR,
										HttpServletResponse.SC_BAD_REQUEST,
										dockerContainer.getStatusMessage(),
										null));
					}
					logger.debug("Created Docker Container "
							+ dockerContainer.getId() + " for user " + user);
				}

				// start the container for the user
				dockerContainer = dockerServer.startDockerContainer(user,
						volumes);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.STARTED) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR,
									HttpServletResponse.SC_BAD_REQUEST,
									dockerContainer.getStatusMessage(), null));
				}
				logger.debug("Started Docker Container "
						+ dockerContainer.getId() + " for user " + user);

				return true;
			} else if (dockerRequest.equals("stop")) {
				// get the container for the user
				DockerContainer dockerContainer = dockerServer
						.getDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.OK) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR,
									HttpServletResponse.SC_BAD_REQUEST,
									dockerContainer.getStatusMessage(), null));
				}
				// detach if connected
				if (sockets.containsKey(user)) {
					ClientSocket socket = sockets.get(user);
					DockerResponse detachResponse = dockerServer
							.detachWSDockerContainer(socket);
					if (detachResponse.getStatusCode() == DockerResponse.StatusCode.DETACHED) {
						logger.debug("Detached from Docker Container "
								+ dockerContainer.getId() + " for user " + user);
					} else {
						logger.debug("Problem detaching from Docker Container "
								+ dockerContainer.getId() + " for user " + user);
					}
					sockets.remove(user);
				}
				// stop the container for the user
				dockerContainer = dockerServer.stopDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.STOPPED) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR,
									HttpServletResponse.SC_BAD_REQUEST,
									dockerContainer.getStatusMessage(), null));
				}
				logger.debug("Stopped Docker Container "
						+ dockerContainer.getId() + " for user " + user);

				return true;
			} else if (dockerRequest.equals("attach")) {
				// get the container for the user
				DockerContainer dockerContainer = dockerServer
						.getDockerContainer(user);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.OK) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR,
									HttpServletResponse.SC_BAD_REQUEST,
									dockerContainer.getStatusMessage(), null));
				}

				if (sockets.containsKey(user)) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR,
									HttpServletResponse.SC_BAD_REQUEST,
									"Already attached to container", null));
				}

				DockerResponse dockerResponse = dockerServer
						.attachWSDockerContainer(user, sockets);
				if (dockerResponse.getStatusCode() != DockerResponse.StatusCode.ATTACHED) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR,
									HttpServletResponse.SC_BAD_REQUEST,
									dockerContainer.getStatusMessage(), null));
				}
				logger.debug("Attach Docker Container "
						+ dockerContainer.getId() + "successful, result is");
				logger.debug(dockerResponse.getStatusMessage());
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("result", dockerResponse.getStatusMessage());
				return true;
			} else if (dockerRequest.equals("process")) {
				DockerContainer dockerContainer = dockerServer
						.getDockerContainer(user);
				if (!sockets.containsKey(user)) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR,
									HttpServletResponse.SC_BAD_REQUEST,
									"No socket connection", null));
				}
				ClientSocket clientSocket = sockets.get(user);
				
				DockerResponse dockerResponse = dockerServer.processCommand(
						clientSocket, command);
				if (dockerResponse.getStatusCode() != DockerResponse.StatusCode.OK) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR,
									HttpServletResponse.SC_BAD_REQUEST,
									dockerContainer.getStatusMessage(), null));
				}
				logger.debug("Process command " + dockerContainer.getId()
						+ "successful, result is");
				logger.debug(dockerResponse.getStatusMessage());
				
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("result", dockerResponse.getStatusMessage());
				OrionServlet.writeJSONResponse(request, response, jsonObject);
				
				return true;
			}
		} catch (URISyntaxException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"URISyntaxException with request", e));
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"JSONException with request", e));
		}
		return false;
	}

	private List<String> getDockerVolumes(String user) {
		try {
			List<String> volumes = new ArrayList<String>();
			IMetaStore metaStore = OrionConfiguration.getMetaStore();
			UserInfo userInfo = metaStore.readUser(user);
			List<String> workspaceIds = userInfo.getWorkspaceIds();
			if (workspaceIds.isEmpty()) {
				// the user has no workspaces so no projects
				return volumes;
			}
			String workspaceId = workspaceIds.get(0);
			WorkspaceInfo workspaceInfo = metaStore.readWorkspace(workspaceId);
			List<String> projectNames = workspaceInfo.getProjectNames();
			for (String projectName : projectNames) {
				ProjectInfo projectInfo = metaStore.readProject(workspaceId,
						projectName);
				URI contentLocation = projectInfo.getContentLocation();
				if (contentLocation.getScheme().equals(EFS.SCHEME_FILE)) {
					// the orion volume /OrionContent/project mounts
					// /serverworkspace/us/user/OrionContent/project
					String localVolume = EFS.getStore(contentLocation)
							.toLocalFile(EFS.NONE, null).getAbsolutePath();
					String orionVolume = localVolume.substring(localVolume
							.indexOf("/OrionContent"));
					String volume = localVolume + ":" + orionVolume + ":rw";
					volumes.add(volume);
				}
			}
			return volumes;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, String path) throws ServletException {
		switch (getMethod(request)) {
		case GET:
			return handleGetRequest(request, response, path);
		case POST:
			return handlePostRequest(request, response);
		default:
			return false;
		}
	}

}
