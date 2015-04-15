/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
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
import java.net.URI;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.objects.Status;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A handler for Git Status operation.
 */
public class GitStatusHandlerV1 extends ServletResourceHandler<String> {

	private static final int GIT_PERF_THRESHOLD = 10000;// 10 seconds
	private ServletResourceHandler<IStatus> statusHandler;

	GitStatusHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String gitPathInfo) throws ServletException {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.git");
		Repository db = null;
		try {
			Path path = new Path(gitPathInfo);
			if (!path.hasTrailingSeparator()) {
				String msg = NLS.bind("Cannot get status on a file: {0}", gitPathInfo);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			if (!AuthorizationService.checkRights(request.getRemoteUser(), "/" + path.toString(), request.getMethod())) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return true;
			}

			long t0 = System.currentTimeMillis();
			Set<Entry<IPath, File>> set = GitUtils.getGitDirs(path, Traverse.GO_UP).entrySet();
			File gitDir = set.iterator().next().getValue();
			if (gitDir == null) {
				logger.error("***** Git status failed to find Git directory for request: " + path);
				return false; // TODO: or an error response code, 405?
			}
			long t1 = System.currentTimeMillis();
			db = FileRepositoryBuilder.create(gitDir);
			Git git = new Git(db);
			org.eclipse.jgit.api.Status gitStatus = git.status().call();
			long t2 = System.currentTimeMillis();

			URI baseLocation = getURI(request);
			String relativePath = GitUtils.getRelativePath(path, set.iterator().next().getKey());
			IPath basePath = new Path(relativePath);
			Status status = new Status(baseLocation, db, gitStatus, basePath);
			OrionServlet.writeJSONResponse(request, response, status.toJSON(), JsonURIUnqualificationStrategy.ALL_NO_GIT);
			if (logger.isDebugEnabled() && (t2 - t0) > GIT_PERF_THRESHOLD) {
				logger.debug("Slow git status. Finding git dir: " + (t1 - t0) + "ms. JGit status call: " + (t2 - t1) + "ms");
			}
			return true;

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating status response", e));
		} finally {
			if (db != null) {
				// close the git repository
				db.close();
			}
		}
	}
}
