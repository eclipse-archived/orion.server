/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * A handler for Git Clone operation.
 */
public class GitCloneHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

	GitCloneHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, path);
					// case PUT :
					// return handlePut(request, response, path);
				case POST :
					return handlePost(request, response);
					// case DELETE :
					// return handleDelete(request, response, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle /git/clone request for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		// make sure required fields are set
		JSONObject toAdd = OrionServlet.readJSONRequest(request);
		String id = toAdd.optString(ProtocolConstants.KEY_ID, null);
		if (id == null)
			id = WebClone.nextCloneId();
		WebClone clone = WebClone.fromId(id);
		String url = toAdd.optString(GitConstants.KEY_URL, null);
		if (!validateCloneUrl(url, request, response))
			return true;
		String name = toAdd.optString(ProtocolConstants.KEY_NAME, null);
		if (name == null)
			name = request.getHeader(ProtocolConstants.HEADER_SLUG);
		if (name == null)
			name = url;
		if (!validateCloneName(name, request, response))
			return true;
		clone.setName(name);
		clone.setUrl(new URIish(url));
		String username = toAdd.optString(GitConstants.KEY_USERNAME, null);
		char[] password = toAdd.optString(GitConstants.KEY_PASSWORD, "").toCharArray(); //$NON-NLS-1$
		String knownHosts = toAdd.optString(GitConstants.KEY_KNOWN_HOSTS, null);
		byte[] privateKey = toAdd.optString(GitConstants.KEY_PRIVATE_KEY, "").getBytes(); //$NON-NLS-1$
		byte[] publicKey = toAdd.optString(GitConstants.KEY_PUBLIC_KEY, "").getBytes(); //$NON-NLS-1$
		byte[] passphrase = toAdd.optString(GitConstants.KEY_PASSPHRASE, "").getBytes(); //$NON-NLS-1$

		// if all went well, clone
		GitCredentialsProvider cp = new GitCredentialsProvider(new URIish(clone.getUrl()), username, password, knownHosts);
		cp.setPrivateKey(privateKey);
		cp.setPublicKey(publicKey);
		cp.setPassphrase(passphrase);

		String userArea = System.getProperty("org.eclipse.orion.server.core.userArea");
		if (userArea == null) {
			String msg = "Error persisting clone state"; //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, null));
		}

		IPath path = new Path(userArea).append(request.getRemoteUser()).append(GitConstants.CLONE_FOLDER).append(clone.getId());
		clone.setContentLocation(path.toFile().toURI());

		JSONObject cloneObject = WebCloneResourceHandler.toJSON(clone, getURI(request));
		String cloneLocation = cloneObject.optString(ProtocolConstants.KEY_LOCATION);
		CloneJob job = new CloneJob(clone, cp, request.getRemoteUser(), cloneLocation);
		job.schedule();
		TaskInfo task = job.getTask();
		JSONObject result = task.toJSON();
		//Not nice that the git service knows the location of the task servlet, but task service doesn't know this either
		String taskLocation = getURI(request).resolve("../../task/id/" + task.getTaskId()).toString(); //$NON-NLS-1$
		result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, taskLocation);
		OrionServlet.writeJSONResponse(request, response, result);
		response.setStatus(HttpServletResponse.SC_CREATED);
		return true;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String pathString) throws IOException, JSONException, ServletException, URISyntaxException {
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		URI baseLocation = getURI(request);
		if (path.segmentCount() < 1) {
			List<WebClone> clones = WebClone.allClones();
			JSONObject result = new JSONObject();
			JSONArray children = new JSONArray();
			String user = request.getRemoteUser();
			for (WebClone clone : clones) {
				if (isAccessAllowed(user, clone)) {
					JSONObject child = WebCloneResourceHandler.toJSON(clone, baseLocation);
					children.put(child);
				}
			}
			result.put(ProtocolConstants.KEY_CHILDREN, children);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} else if (path.segmentCount() == 1) {
			if (WebClone.exists(path.segment(0))) {
				WebClone clone = WebClone.fromId(path.segment(0));
				JSONObject result = WebCloneResourceHandler.toJSON(clone, baseLocation);
				response.setHeader(ProtocolConstants.HEADER_LOCATION, result.optString(ProtocolConstants.KEY_LOCATION, "")); //$NON-NLS-1$
				OrionServlet.writeJSONResponse(request, response, result);
				return true;
			} else {
				String msg = NLS.bind("Clone with the given ID not found: {0}", path.segment(0));
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		}
		//else the request is malformed
		String msg = NLS.bind("Invalid clone request: {0}", path);
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	/**
	 * Returns whether the user can access the given clone
	 */
	private boolean isAccessAllowed(String user, WebClone clone) {
		URI contentURI = clone.getContentLocation();
		//TODO Not sure if this is even possible
		if (contentURI == null)
			return true;
		String userArea = System.getProperty(Activator.PROP_USER_AREA);
		if (userArea == null)
			return false;
		//ensure the clone is in this user's user area
		IPath path = new Path(userArea).append(user);
		if (contentURI.toString().startsWith(path.toFile().toURI().toString()))
			return true;
		return false;
	}

	/**
	 * Validates that the provided clone name is valid. Returns
	 * <code>true</code> if the project name is valid, and <code>false</code>
	 * otherwise. This method takes care of setting the error response when the
	 * clone name is not valid.
	 */
	private boolean validateCloneName(String name, HttpServletRequest request, HttpServletResponse response) throws ServletException {
		// TODO: implement
		return true;
	}

	private boolean validateCloneUrl(String url, HttpServletRequest request, HttpServletResponse response) throws ServletException {
		if (url == null || url.trim().length() == 0) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Clone URL cannot be empty", null)); //$NON-NLS-1$
			return false;
		}
		try {
			new URIish(url);
		} catch (URISyntaxException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid clone URL: {0}", url), e)); //$NON-NLS-1$
			return false;
		}
		return true;
	}

}
