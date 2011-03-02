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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.Util;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A handler for Git Clone operation.
 */
public class GitCloneHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

	GitCloneHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, String path)
			throws ServletException {
		try {
			switch (getMethod(request)) {
			case GET:
				return handleGet(request, response, path);
				// case PUT :
				// return handlePut(request, response, path);
			case POST:
				return handlePost(request, response);
				// case DELETE :
				// return handleDelete(request, response, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind(
					"Failed to handle /git/clone request for {0}", path); //$NON-NLS-1$
			statusHandler.handleRequest(request, response, new ServerStatus(
					IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handlePost(HttpServletRequest request,
			HttpServletResponse response) throws IOException, JSONException,
			ServletException, URISyntaxException {
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
		
		doClone(clone, cp);

		// save the clone metadata
		try {
			clone.save();
		} catch (CoreException e) {
			String msg = "Error persisting clone state"; //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}

		URI baseLocation = getURI(request);
		JSONObject result = WebCloneResourceHandler.toJSON(clone, baseLocation);
		OrionServlet.writeJSONResponse(request, response, result);

		//add project location to response header
		response.setHeader(ProtocolConstants.HEADER_LOCATION, result.optString(ProtocolConstants.KEY_LOCATION));
		response.setStatus(HttpServletResponse.SC_CREATED);

		return true;
	}

	private boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, String path) throws IOException,
			JSONException, ServletException, URISyntaxException {
		List<WebClone> clones = WebClone.allClones();
		JSONObject result = new JSONObject();
		URI baseLocation = getURI(request);
		JSONArray children = new JSONArray();
		for (WebClone clone : clones) {
			JSONObject child = WebCloneResourceHandler.toJSON(clone,
					baseLocation);
			children.put(child);
		}
		result.put(ProtocolConstants.KEY_CHILDREN, children);
		OrionServlet.writeJSONResponse(request, response, result);
		return true;
	}

	private void doClone(WebClone clone, CredentialsProvider cp) throws URISyntaxException {
		File cloneFolder = new File(clone.getContentLocation().getPath());
		if (!cloneFolder.exists())
			cloneFolder.mkdir();
		CloneCommand cc = Git.cloneRepository();
		cc.setBare(false);
		cc.setCredentialsProvider(cp);
		cc.setDirectory(cloneFolder);
		cc.setRemote(Constants.DEFAULT_REMOTE_NAME);
		cc.setURI(clone.getUrl());
		cc.call();
	}

	/**
	 * Validates that the provided clone name is valid. Returns
	 * <code>true</code> if the project name is valid, and <code>false</code>
	 * otherwise. This method takes care of setting the error response when the
	 * project name is not valid.
	 */
	private boolean validateCloneName(String name, HttpServletRequest request,
			HttpServletResponse response) throws ServletException {
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
