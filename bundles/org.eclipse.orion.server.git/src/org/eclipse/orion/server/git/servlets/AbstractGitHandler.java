/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation and others.
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
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class AbstractGitHandler extends ServletResourceHandler<String> {

	class RequestInfo {
		HttpServletRequest request;
		HttpServletResponse response;
		Repository db;
		String gitSegment;
		String relativePath;
		IPath filePath;
		Git git;
		JSONObject jsonRequest;

		public RequestInfo(HttpServletRequest request, HttpServletResponse response, Repository db, String gitSegment, String relativePath, IPath filePath) {
			this.request = request;
			this.response = response;
			this.db = db;
			this.gitSegment = gitSegment;
			this.relativePath = relativePath;
			this.filePath = filePath;
			this.git = new Git(db);
		}

		public JSONObject getJSONRequest() {
			if (jsonRequest == null)
				jsonRequest = readJSONRequest();
			return jsonRequest;
		}

		private JSONObject readJSONRequest() {
			try {
				return OrionServlet.readJSONRequest(request);
			} catch (IOException e) {
				LogHelper.log(new Status(IStatus.WARNING, GitActivator.PI_GIT, 1, "An error occured when getting JSON request", e));
				return new JSONObject();
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.WARNING, GitActivator.PI_GIT, 1, "An error occured when getting JSON request", e));
				return new JSONObject();
			}
		}
	}

	protected ServletResourceHandler<IStatus> statusHandler;

	AbstractGitHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		Repository db = null;
		try {
			IPath p = new Path(path);
			String gitSegment = null;
			if (p.segment(1).equals("file")) { //$NON-NLS-1$
				gitSegment = GitUtils.decode(p.segment(0));
				p = p.removeFirstSegments(1);
			}
			IPath filePath = p;
			if (!AuthorizationService.checkRights(request.getRemoteUser(), "/" + filePath.toString(), request.getMethod())) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return true;
			}
			IPath gitSearchPath = filePath.hasTrailingSeparator() ? filePath : filePath.removeLastSegments(1);
			Set<Entry<IPath, File>> gitDirsFound = GitUtils.getGitDirs(gitSearchPath, Traverse.GO_UP).entrySet();
			if (gitDirsFound.size() == 0) {
				String msg = NLS.bind("Could not find repository for {0}", filePath);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			Entry<IPath, File> firstGitDir = gitDirsFound.iterator().next();
			File gitDir = firstGitDir.getValue();
			if (gitDir == null) {
				String msg = NLS.bind("Could not find repository for {0}", filePath);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			String relativePath = GitUtils.getRelativePath(filePath, firstGitDir.getKey());
			db = FileRepositoryBuilder.create(gitDir);
			RequestInfo requestInfo = new RequestInfo(request, response, db, gitSegment, relativePath, filePath);
			switch (getMethod(request)) {
			case GET:
				return handleGet(requestInfo);
			case POST:
				return handlePost(requestInfo);
			case PUT:
				return handlePut(requestInfo);
			case DELETE:
				return handleDelete(requestInfo);
			case OPTIONS:
			case HEAD:
			default:
				return false;
			}
		} catch (IOException e) {
			String msg = NLS.bind("Failed to process a git request for {0}", path);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} catch (CoreException e) {
			String msg = NLS.bind("Failed to process a git request for {0}", path);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			if (db != null) {
				// close the git repository
				db.close();
			}
		}
	}

	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {
		return false;
	}

	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {
		return false;
	}

	protected boolean handlePut(RequestInfo requestInfo) throws ServletException {
		return false;
	}

	protected boolean handleDelete(RequestInfo requestInfo) throws ServletException {
		return false;
	}
}
