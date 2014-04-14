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
package org.eclipse.orion.server.docker.servlets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.docker.server.DockerContainer;
import org.eclipse.orion.server.docker.server.DockerContainers;
import org.eclipse.orion.server.docker.server.DockerImage;
import org.eclipse.orion.server.docker.server.DockerImages;
import org.eclipse.orion.server.docker.server.DockerResponse;
import org.eclipse.orion.server.docker.server.DockerServer;
import org.eclipse.orion.server.docker.server.DockerVersion;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
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

	private DockerServer dockerServer = null;

	protected ServletResourceHandler<IStatus> statusHandler;

	public DockerHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
		initDockerServer();
	}

	private DockerServer getDockerServer() {
		return dockerServer;
	}

	private List<String> getDockerVolumes(String user) {
		try {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$
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

			// create volumes for each of the projects
			for (String projectName : projectNames) {
				ProjectInfo projectInfo = metaStore.readProject(workspaceId, projectName);
				URI contentLocation = projectInfo.getContentLocation();
				if (contentLocation.getScheme().equals(EFS.SCHEME_FILE)) {
					// the docker volume /home/user/project mounts local volume
					// serverworkspace/us/user/OrionContent/project
					String localVolume = EFS.getStore(contentLocation).toLocalFile(EFS.NONE, null).getAbsolutePath();
					if (localVolume.indexOf("/OrionContent") != -1) {
						String dockerVolume = localVolume.substring(localVolume.indexOf("/OrionContent") + 13);
						String volume = localVolume + ":/home/" + user + dockerVolume + ":rw";
						volumes.add(volume);
					} else {
						// we do not handle mapped projects right now
						if (logger.isWarnEnabled()) {
							logger.warn("Cannot handle mapped project " + contentLocation.toString() + " for user " + user);
						}
					}
				} else {
					// we do not handle ftp projects right now
					if (logger.isWarnEnabled()) {
						logger.warn("Cannot handle ftp project " + contentLocation.toString() + " for user " + user);
					}
				}
			}
			return volumes;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Handle the connect request for a user. The request creates an image for the user, a container for the user based 
	 * on that image, starts the container and then attaches to the container via a web socket. An existing container 
	 * for the user is reused if it already exists. if the singleton container for a user is already attached, 
	 * no operation is needed.
	 * @param request
	 * @param response
	 * @return true if the connect was successful.
	 * @throws ServletException
	 */
	private boolean handleConnectDockerContainerRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		try {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$

			// get the Orion user from the request
			String user = request.getRemoteUser();

			DockerServer dockerServer = getDockerServer();

			// check if the user is already attached to a docker container
			if (dockerServer.isAttachedDockerContainer(user)) {

				// detach the connection for the user
				dockerServer.detachDockerContainer(user);
			}

			// make sure the image for the user has been created
			String userBase = user + "-base";
			DockerImage dockerImage = dockerServer.getDockerImage(userBase);
			if (dockerImage.getStatusCode() != DockerResponse.StatusCode.OK) {

				// user does not have a image, create one
				dockerImage = dockerServer.createDockerUserBaseImage(user);
				if (dockerImage.getStatusCode() != DockerResponse.StatusCode.CREATED) {
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerImage.getStatusMessage(), null));
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Created Docker Image " + userBase + " for user " + user);
				}
			}

			// get the volumes (projects) for the user
			List<String> volumes = getDockerVolumes(user);

			// get the container for the user
			DockerContainer dockerContainer = dockerServer.getDockerContainer(user);
			if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.OK) {

				// user does not have a container, create one
				dockerContainer = dockerServer.createDockerContainer(userBase, user, volumes);
				if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.CREATED) {
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Created Docker Container " + dockerContainer.getIdShort() + " for user " + user);
				}
			}

			// get the exposed ports from the docker image
			List<String> portNumbers = new ArrayList<String>();
			for (String port : dockerImage.getPorts()) {
				if (port.contains("/tcp")) {
					port = port.substring(0, port.indexOf("/tcp"));
				}
				portNumbers.add(port);
			}

			// start the container for the user
			dockerContainer = dockerServer.startDockerContainer(user, volumes, portNumbers);
			if (dockerContainer.getStatusCode() == DockerResponse.StatusCode.STARTED) {
				if (logger.isDebugEnabled()) {
					logger.debug("Started Docker Container " + dockerContainer.getIdShort() + " for user " + user);
				}
			} else if (dockerContainer.getStatusCode() == DockerResponse.StatusCode.RUNNING) {
				if (logger.isDebugEnabled()) {
					logger.debug("Docker Container " + dockerContainer.getIdShort() + " for user " + user + " is already running");
				}
			} else {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
			}

			// attach to the container for the user 
			String originURL = request.getRequestURL().toString();
			DockerResponse dockerResponse = dockerServer.attachDockerContainer(user, originURL);
			if (dockerResponse.getStatusCode() != DockerResponse.StatusCode.ATTACHED) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Attach Docker Container " + dockerContainer.getIdShort() + " for user " + user + " successful");
			}

			JSONObject jsonObject = new JSONObject();
			jsonObject.put(DockerContainer.ATTACH_WS, dockerResponse.getStatusMessage());
			OrionServlet.writeJSONResponse(request, response, jsonObject);
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "JSONException with request", e));
		}
	}

	/**
	 * Handle the disconnect request for the user. The request detaches the web socket from the container for the user
	 * @param request
	 * @param response
	 * @return true if the disconnect was successful.
	 * @throws ServletException
	 */
	private boolean handleDisconnectDockerContainerRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		String user = request.getRemoteUser();

		DockerServer dockerServer = getDockerServer();

		// get the container for the user
		DockerContainer dockerContainer = dockerServer.getDockerContainer(user);
		if (dockerContainer.getStatusCode() != DockerResponse.StatusCode.OK) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, dockerContainer.getStatusMessage(), null));
		}

		// detach if we have an open connection for the user
		if (dockerServer.isAttachedDockerContainer(user)) {
			dockerServer.detachDockerContainer(user);
		}

		return true;
	}

	private boolean handleDockerContainerRequest(HttpServletRequest request, HttpServletResponse response, String string) throws ServletException {
		try {
			DockerServer dockerServer = getDockerServer();
			DockerContainer dockerContainer = dockerServer.getDockerContainer(string);
			switch (dockerContainer.getStatusCode()) {
				case SERVER_ERROR :
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dockerContainer.getStatusMessage());
					return false;
				case NO_SUCH_IMAGE :
					JSONObject jsonObject = new JSONObject();
					jsonObject.put(DockerContainer.IMAGE, dockerContainer.getStatusMessage());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				case CONNECTION_REFUSED :
					jsonObject = new JSONObject();
					jsonObject.put(DockerContainer.IMAGE, dockerContainer.getStatusMessage());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				case OK :
					jsonObject = new JSONObject();
					jsonObject.put(DockerContainer.ID, dockerContainer.getIdShort());
					jsonObject.put(DockerContainer.IMAGE, dockerContainer.getImage());
					jsonObject.put(DockerContainer.COMMAND, dockerContainer.getCommand());
					jsonObject.put(DockerContainer.CREATED, dockerContainer.getCreated());
					jsonObject.put(DockerContainer.STATUS, dockerContainer.getStatus());
					jsonObject.put(DockerContainer.PORTS, dockerContainer.getPorts());
					jsonObject.put(DockerContainer.NAME, dockerContainer.getName());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				default :
					return false;
			}
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "JSONException with request", e));
		}
	}

	private boolean handleDockerContainersRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		try {
			DockerServer dockerServer = getDockerServer();
			DockerContainers dockerContainers = dockerServer.getDockerContainers();
			switch (dockerContainers.getStatusCode()) {
				case SERVER_ERROR :
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dockerContainers.getStatusMessage());
					return false;
				case CONNECTION_REFUSED :
					JSONObject jsonObject = new JSONObject();
					jsonObject.put(DockerContainers.CONTAINERS, dockerContainers.getStatusMessage());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				case OK :
					JSONArray jsonArray = new JSONArray();
					for (DockerContainer dockerContainer : dockerContainers.getContainers()) {
						jsonObject = new JSONObject();
						jsonObject.put(DockerContainer.ID, dockerContainer.getIdShort());
						jsonObject.put(DockerContainer.IMAGE, dockerContainer.getImage());
						jsonObject.put(DockerContainer.COMMAND, dockerContainer.getCommand());
						jsonObject.put(DockerContainer.CREATED, dockerContainer.getCreated());
						jsonObject.put(DockerContainer.STATUS, dockerContainer.getStatus());
						jsonObject.put(DockerContainer.PORTS, dockerContainer.getPorts());
						jsonObject.put(DockerContainer.NAME, dockerContainer.getName());
						jsonArray.put(jsonObject);
					}
					jsonObject = new JSONObject();
					jsonObject.put(DockerContainers.CONTAINERS, jsonArray);
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				default :
					return false;
			}
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "JSONException with request", e));
		}
	}

	private boolean handleDockerImageRequest(HttpServletRequest request, HttpServletResponse response, String string) throws ServletException {
		try {
			DockerServer dockerServer = getDockerServer();
			DockerImage dockerImage = dockerServer.getDockerImage(string);
			switch (dockerImage.getStatusCode()) {
				case SERVER_ERROR :
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dockerImage.getStatusMessage());
					return false;
				case NO_SUCH_IMAGE :
					JSONObject jsonObject = new JSONObject();
					jsonObject.put(DockerImage.IMAGE, dockerImage.getStatusMessage());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				case CONNECTION_REFUSED :
					jsonObject = new JSONObject();
					jsonObject.put(DockerImage.IMAGE, dockerImage.getStatusMessage());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				case OK :
					jsonObject = new JSONObject();
					jsonObject.put(DockerImage.REPOSITORY, dockerImage.getRepository());
					jsonObject.put(DockerImage.TAG, dockerImage.getTag());
					jsonObject.put(DockerImage.ID, dockerImage.getId());
					jsonObject.put(DockerImage.CREATED, dockerImage.getCreated());
					jsonObject.put(DockerImage.SIZE, dockerImage.getSize());
					JSONObject ports = new JSONObject();
					for (String port : dockerImage.getPorts()) {
						ports.put(port, new JSONObject());
					}
					jsonObject.put(DockerImage.EXPOSED_PORTS, ports);
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				default :
					return false;
			}
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "JSONException with request", e));
		}
	}

	private boolean handleDockerImagesRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		try {
			DockerServer dockerServer = getDockerServer();
			DockerImages dockerImages = dockerServer.getDockerImages();
			switch (dockerImages.getStatusCode()) {
				case SERVER_ERROR :
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dockerImages.getStatusMessage());
					return false;
				case CONNECTION_REFUSED :
					JSONObject jsonObject = new JSONObject();
					jsonObject.put(DockerImages.IMAGES, dockerImages.getStatusMessage());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				case OK :
					JSONArray jsonArray = new JSONArray();
					for (DockerImage dockerImage : dockerImages.getImages()) {
						jsonObject = new JSONObject();
						jsonObject.put(DockerImage.REPOSITORY, dockerImage.getRepository());
						jsonObject.put(DockerImage.TAG, dockerImage.getTag());
						jsonObject.put(DockerImage.ID, dockerImage.getId());
						jsonObject.put(DockerImage.CREATED, dockerImage.getCreated());
						jsonObject.put(DockerImage.SIZE, dockerImage.getSize());
						jsonArray.put(jsonObject);
					}
					jsonObject = new JSONObject();
					jsonObject.put(DockerImages.IMAGES, jsonArray);
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				default :
					return false;
			}
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "IOException with request", e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "JSONException with request", e));
		}
	}

	private boolean handleDockerVersionRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		try {
			DockerServer dockerServer = getDockerServer();
			DockerVersion dockerVersion = dockerServer.getDockerVersion();
			switch (dockerVersion.getStatusCode()) {
				case BAD_PARAMETER :
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, dockerVersion.getStatusMessage());
					return false;
				case SERVER_ERROR :
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dockerVersion.getStatusMessage());
					return false;
				case CONNECTION_REFUSED :
					JSONObject jsonObject = new JSONObject();
					jsonObject.put(DockerVersion.VERSION, dockerVersion.getStatusMessage());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				case OK :
					jsonObject = new JSONObject();
					jsonObject.put(DockerVersion.VERSION, dockerVersion.getVersion());
					OrionServlet.writeJSONResponse(request, response, jsonObject);
					return true;
				default :
					return false;
			}
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
		} else if (dockerRequest.equals(DockerImages.IMAGES_PATH)) {
			return handleDockerImagesRequest(request, response);
		} else if (dockerRequest.equals(DockerImage.IMAGE_PATH)) {
			return handleDockerImageRequest(request, response, pathSplit[1]);
		} else if (dockerRequest.equals(DockerContainers.CONTAINERS_PATH)) {
			return handleDockerContainersRequest(request, response);
		} else if (dockerRequest.equals(DockerContainer.CONTAINER_PATH)) {
			return handleDockerContainerRequest(request, response, pathSplit[1]);
		}
		return false;
	}

	private boolean handlePostRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		String[] pathSplit = path.split("\\/", 2);
		String dockerRequest = pathSplit[0];
		if (dockerRequest.equals(DockerContainer.CONTAINER_CONNECT_PATH)) {
			return handleConnectDockerContainerRequest(request, response);
		} else if (dockerRequest.equals(DockerContainer.CONTAINER_DISCONNECT_PATH)) {
			return handleDisconnectDockerContainerRequest(request, response);
		}
		return false;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		if (dockerServer == null) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "A Docker server required for terminal support is not enabled on this Orion server.", null));
		}
		switch (getMethod(request)) {
			case GET :
				return handleGetRequest(request, response, path);
			case POST :
				return handlePostRequest(request, response, path);
			default :
				return false;
		}
	}

	private void initDockerServer() {
		try {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$
			String dockerLocation = PreferenceHelper.getString(ServerConstants.CONFIG_DOCKER_URI, "none").toLowerCase(); //$NON-NLS-1$
			if ("none".equals(dockerLocation)) {
				// there is no docker URI value in the orion.conf, so no docker support
				dockerServer = null;
				logger.debug("No Docker Server specified by \"" + ServerConstants.CONFIG_DOCKER_URI + "\" in orion.conf");
				return;
			}
			URI dockerLocationURI = new URI(dockerLocation);
			URI dockerProxyURI = dockerLocationURI;

			String dockerProxy = PreferenceHelper.getString(ServerConstants.CONFIG_DOCKER_PROXY_URI, "none").toLowerCase(); //$NON-NLS-1$
			if (!"none".equals(dockerProxy)) {
				// there is a docker proxy URI value in the orion.conf
				dockerProxyURI = new URI(dockerProxy);
				logger.debug("Docker Proxy Server " + dockerProxy + " is enabled");
			}

			String portStart = PreferenceHelper.getString(ServerConstants.CONFIG_DOCKER_PORT_START, "none").toLowerCase(); //$NON-NLS-1$
			String portEnd = PreferenceHelper.getString(ServerConstants.CONFIG_DOCKER_PORT_END, "none").toLowerCase(); //$NON-NLS-1$
			if ("none".equals(portStart) || "none".equals(portEnd)) {
				// there is a no docker port start value in the orion.conf
				portStart = null;
				portEnd = null;
				logger.info("Docker Server does not have port mapping enabled, start and end host ports not specified");
			} else {
				logger.debug("Docker Server using ports " + portStart + " to " + portEnd + " for host port mapping");
			}

			String userId = PreferenceHelper.getString(ServerConstants.CONFIG_DOCKER_UID, "1000").toLowerCase(); //$NON-NLS-1$
			logger.debug("Orion Server running as UID " + userId);

			String groupId = PreferenceHelper.getString(ServerConstants.CONFIG_DOCKER_GID, "1000").toLowerCase(); //$NON-NLS-1$
			logger.debug("Orion Server running as GID " + groupId);

			dockerServer = new DockerServer(dockerLocationURI, dockerProxyURI, portStart, portEnd, userId, groupId);
			DockerVersion dockerVersion = dockerServer.getDockerVersion();
			if (logger.isDebugEnabled()) {
				if (dockerVersion.getStatusCode() != DockerResponse.StatusCode.OK) {
					logger.error("Cound not connect to docker server " + dockerLocation + ": " + dockerVersion.getStatusMessage());
				} else {
					logger.debug("Docker Server " + dockerLocation + " is running version " + dockerVersion.getVersion());
				}
			}

		} catch (URISyntaxException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e);
			dockerServer = null;
		}
	}
}
