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
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.server.git.GitConstants;
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
				case POST :
					return handlePost(request, response, path);
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
			File gitDir = GitUtils.getGitDir(p);
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections("remote");
			JSONObject result = new JSONObject();
			JSONArray children = new JSONArray();
			URI baseLocation = getURI(request);
			for (String configName : configNames) {
				JSONObject o = new JSONObject();
				o.put(ProtocolConstants.KEY_NAME, configName);
				o.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, 2, configName));
				children.put(o);
			}
			result.put(ProtocolConstants.KEY_CHILDREN, children);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} else {
			// TODO: return remote info
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections("remote");
			JSONObject result = new JSONObject();
			URI baseLocation = getURI(request);
			for (String configName : configNames) {
				if (configName.equals(p.segment(0))) {
					result.put(ProtocolConstants.KEY_NAME, configName);
					result.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, 3, configName));

					JSONArray children = new JSONArray();
					for (Entry<String, Ref> refEntry : db.getRefDatabase().getRefs(Constants.R_REMOTES).entrySet()) {
						if (!refEntry.getValue().isSymbolic()) {
							Ref ref = refEntry.getValue();
							JSONObject o = new JSONObject();
							String name = ref.getName();
							o.put(ProtocolConstants.KEY_NAME, name);
							o.put(ProtocolConstants.KEY_ID, ref.getObjectId().name());
							o.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, 3, name.substring(Constants.R_REMOTES.length())));
							children.put(o);
						}
					}
					result.put(ProtocolConstants.KEY_CHILDREN, children);
					OrionServlet.writeJSONResponse(request, response, result);
					return true;
				}
			}
			String msg = NLS.bind("Couldn't find remote : {0}", p.segment(0)); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		}
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		JSONObject requestObject = OrionServlet.readJSONRequest(request);
		boolean fetch = Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_FETCH, null));
		if (fetch) {
			// {remote}/{branch}/{file}/{path}
			Path p = new Path(path);
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			Repository db = new FileRepository(gitDir);
			Git git = new Git(db);
			try {
				// TODO: set branch to fetch -- p.segment(1)
				git.fetch().setRemote(p.segment(0)).call();
				return true;
			} catch (GitAPIException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "An error occured when fetching.", e));
			} catch (JGitInternalException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occured when fetching.", e));
			}
		} else {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Only Fetch:true is currently supported.", null));
		}
	}

	private URI baseToRemoteLocation(URI u, int count, String remoteName) throws URISyntaxException {
		// URIUtil.append(baseLocation, configName)
		IPath p = new Path(u.getPath());
		p = p.uptoSegment(2).append(remoteName).addTrailingSeparator().append(p.removeFirstSegments(count));
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), p.toString(), u.getQuery(), u.getFragment());
	}
}
