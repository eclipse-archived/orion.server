/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.objects.ConfigOption;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A handler for Git Clone operation.
 */
public class GitConfigHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

	GitConfigHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		try {
			Path p = new Path(path);
			IPath filePath = p;
			if (p.segment(0).equals(Clone.RESOURCE) && p.segment(1).equals("file")) { //$NON-NLS-1$
				filePath = p.removeFirstSegments(1);
			} else if (p.segment(1).equals(Clone.RESOURCE) && p.segment(2).equals("file")) { //$NON-NLS-1$
				filePath = p.removeFirstSegments(2);
			}
			if (!AuthorizationService.checkRights(request.getRemoteUser(), "/" + filePath.toString(), request.getMethod())) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return true;
			}

			switch (getMethod(request)) {
			case GET:
				return handleGet(request, response, path);
			case POST:
				return handlePost(request, response, path);
			case PUT:
				return handlePut(request, response, path);
			case DELETE:
				return handleDelete(request, response, path);
			default:
				return false;
			}
		} catch (Exception e) {
			String msg = NLS.bind("Failed to process an operation on commits for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException,
			URISyntaxException, CoreException, ConfigInvalidException {
		Path p = new Path(path);
		URI baseLocation = getURI(request);
		if (p.segment(0).equals(Clone.RESOURCE) && p.segment(1).equals("file")) { //$NON-NLS-1$
			// expected path /gitapi/config/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			if (gitDir == null)
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("No repository found under {0}", p.removeFirstSegments(1)),
								null));
			Repository db = null;
			try {
				db = FileRepositoryBuilder.create(gitDir);
				URI cloneLocation = BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.CONFIG);
				ConfigOption configOption = new ConfigOption(cloneLocation, db);
				OrionServlet.writeJSONResponse(request, response, configOption.toJSON(/* all */), JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			} finally {
				if (db != null) {
					db.close();
				}
			}
		} else if (p.segment(1).equals(Clone.RESOURCE) && p.segment(2).equals("file")) { //$NON-NLS-1$
			// expected path /gitapi/config/{key}/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			if (gitDir == null)
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("No repository found under {0}", p.removeFirstSegments(2)),
								null));
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.CONFIG_OPTION);
			Repository db = null;
			try {
				db = FileRepositoryBuilder.create(gitDir);
				ConfigOption configOption = new ConfigOption(cloneLocation, db, p.segment(0));
				if (!configOption.exists())
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND,
							"There is no config entry with key provided", null));
				OrionServlet.writeJSONResponse(request, response, configOption.toJSON(), JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			} catch (IllegalArgumentException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e));
			} finally {
				if (db != null) {
					db.close();
				}
			}
		}
		return false;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, JSONException,
			ServletException, URISyntaxException, ConfigInvalidException {
		Path p = new Path(path);
		if (p.segment(0).equals(Clone.RESOURCE) && p.segment(1).equals("file")) { //$NON-NLS-1$
			// expected path /gitapi/config/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			if (gitDir == null)
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("No repository found under {0}", p.removeFirstSegments(1)),
								null));
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.CONFIG);
			JSONObject toPost = OrionServlet.readJSONRequest(request);
			String key = toPost.optString(GitConstants.KEY_CONFIG_ENTRY_KEY, null);
			if (key == null || key.isEmpty())
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
						"Config entry key must be provided", null));
			String value = toPost.optString(GitConstants.KEY_CONFIG_ENTRY_VALUE, null);
			if (value == null || value.isEmpty())
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
						"Config entry value must be provided", null));
			Repository db = null;
			try {
				db = FileRepositoryBuilder.create(gitDir);
				ConfigOption configOption = new ConfigOption(cloneLocation, db, key);
				boolean present = configOption.exists();
				ArrayList<String> valList = new ArrayList<String>();
				if (present) {
					String[] val = configOption.getValue();
					valList.addAll(Arrays.asList(val));
				}
				valList.add(value);
				save(configOption, valList);

				JSONObject result = configOption.toJSON();
				OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
				response.setHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
				response.setStatus(HttpServletResponse.SC_CREATED);
				return true;
			} catch (IllegalArgumentException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e));
			} finally {
				if (db != null) {
					db.close();
				}
			}
		}
		return false;
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, JSONException,
			ServletException, URISyntaxException, ConfigInvalidException {
		Path p = new Path(path);
		if (p.segment(1).equals(Clone.RESOURCE) && p.segment(2).equals("file")) { //$NON-NLS-1$
			// expected path /gitapi/config/{key}/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			if (gitDir == null)
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("No repository found under {0}", p.removeFirstSegments(2)),
								null));
			Repository db = null;
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.CONFIG_OPTION);
			try {
				db = FileRepositoryBuilder.create(gitDir);
				ConfigOption configOption = new ConfigOption(cloneLocation, db, p.segment(0));

				JSONObject toPut = OrionServlet.readJSONRequest(request);
				JSONArray value = toPut.optJSONArray(GitConstants.KEY_CONFIG_ENTRY_VALUE);
				if (value == null || (value.length() == 1 && value.isNull(0)))
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
							"Config entry value must be provided", null));

				// PUT allows only to modify existing config entries
				if (!configOption.exists()) {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
					return true;
				}

				ArrayList<String> valList = new ArrayList<String>();
				for (int i = 0; i < value.length(); i++) {
					valList.add(value.getString(i));
				}

				save(configOption, valList);

				JSONObject result = configOption.toJSON();
				OrionServlet.writeJSONResponse(request, response, result);
				response.setHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
				return true;
			} catch (IllegalArgumentException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e));
			} finally {
				if (db != null) {
					db.close();
				}
			}
		}
		return false;
	}

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, ServletException,
			ConfigInvalidException, URISyntaxException {
		Path p = new Path(path);
		if (p.segment(1).equals(Clone.RESOURCE) && p.segment(2).equals("file")) { //$NON-NLS-1$
			// expected path /gitapi/config/{key}/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			if (gitDir == null)
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("No repository found under {0}", p.removeFirstSegments(2)),
								null));
			Repository db = null;
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.CONFIG_OPTION);
			try {
				db = FileRepositoryBuilder.create(gitDir);
				ConfigOption configOption = new ConfigOption(cloneLocation, db, GitUtils.decode(p.segment(0)));
				if (configOption.exists()) {
					String query = request.getParameter("index"); //$NON-NLS-1$
					if (query != null) {
						List<String> existing = new ArrayList<String>(Arrays.asList(configOption.getValue()));
						existing.remove(Integer.parseInt(query));
						save(configOption, existing);
					} else {
						delete(configOption);
					}
					response.setStatus(HttpServletResponse.SC_OK);
				} else {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
				return true;
			} catch (IllegalArgumentException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e));
			} finally {
				if (db != null) {
					db.close();
				}
			}
		}
		return false;
	}

	private static void save(ConfigOption co, List<String> value) throws IOException {
		co.getConfig().setStringList(co.getSection(), co.getSubsection(), co.getName(), value);
		co.getConfig().save();
	}

	private static void delete(ConfigOption co) throws IOException {
		co.getConfig().unset(co.getSection(), co.getSubsection(), co.getName());
		co.getConfig().save();
	}
}
