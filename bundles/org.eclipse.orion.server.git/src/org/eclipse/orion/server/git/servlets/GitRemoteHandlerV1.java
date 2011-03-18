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
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

public class GitRemoteHandlerV1 extends ServletResourceHandler<String> {
	private ServletResourceHandler<IStatus> statusHandler;

	GitRemoteHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
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
					// case POST :
					// return handlePost(request, response);
					// case DELETE :
					// return handleDelete(request, response, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle /git/remote request for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		Path p = new Path(path);
		// FIXME: what if a remote is named "file"?
		if (p.segment(0).equals("file")) {
			File gitDir = GitUtils.getGitDir(p, request.getRemoteUser());
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections("remote");
			JSONObject result = new JSONObject();
			JSONArray children = new JSONArray();
			URI baseLocation = getURI(request);
			for (String configName : configNames) {
				JSONObject o = new JSONObject();
				o.put(ProtocolConstants.KEY_NAME, configName);
				o.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, configName));
				children.put(o);
			}
			result.put(ProtocolConstants.KEY_CHILDREN, children);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} else {
			// TODO: return remote info
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1), request.getRemoteUser());
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections("remote");
			JSONObject result = new JSONObject();
			URI baseLocation = getURI(request);
			for (String configName : configNames) {
				if (configName.equals(p.segment(0))) {
					result.put(ProtocolConstants.KEY_NAME, configName);
					result.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, configName));
					OrionServlet.writeJSONResponse(request, response, result);
					return true;
				}
			}
			String msg = NLS.bind("Couldn't find remote : {0}", p.segment(0)); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		}
	}

	private URI baseToRemoteLocation(URI u, String remoteName) throws URISyntaxException {
		// URIUtil.append(baseLocation, configName)
		IPath p = new Path(u.getPath());
		p = p.uptoSegment(2).append(remoteName).addTrailingSeparator().append(p.removeFirstSegments(2));
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), p.toString(), u.getQuery(), u.getFragment());
	}

}
