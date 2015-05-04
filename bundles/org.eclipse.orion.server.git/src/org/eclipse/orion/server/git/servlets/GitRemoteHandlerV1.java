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
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.jobs.FetchJob;
import org.eclipse.orion.server.git.jobs.PushJob;
import org.eclipse.orion.server.git.jobs.RemoteDetailsJob;
import org.eclipse.orion.server.git.objects.Remote;
import org.eclipse.orion.server.git.objects.RemoteBranch;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GitRemoteHandlerV1 extends ServletResourceHandler<String> {
	private ServletResourceHandler<IStatus> statusHandler;
	public static int PAGE_SIZE = 50;

	GitRemoteHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		try {
			Path p = new Path(path);
			IPath filePath = p;
			if (p.segment(1).equals("file")) { //$NON-NLS-1$
				filePath = p.removeFirstSegments(1);
			} else if (p.segment(2).equals("file")) { //$NON-NLS-1$
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
			case DELETE:
				return handleDelete(request, response, path);
			default:
				return false;
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle /git/remote request for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException,
			URISyntaxException, CoreException {
		Path p = new Path(path);
		// FIXME: what if a remote or branch is named "file"?
		if (p.segment(0).equals("file")) { //$NON-NLS-1$
			// /git/remote/file/{path}
			File gitDir = GitUtils.getGitDir(p);
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.REMOTE_LIST);
			Repository db = null;
			try {
				db = FileRepositoryBuilder.create(gitDir);
				Set<String> configNames = db.getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);
				JSONObject result = new JSONObject();
				JSONArray children = new JSONArray();
				for (String configName : configNames) {
					Remote remote = new Remote(cloneLocation, db, configName);
					children.put(remote.toJSON(false));
				}
				result.put(ProtocolConstants.KEY_CHILDREN, children);
				result.put(ProtocolConstants.KEY_TYPE, Remote.TYPE);
				OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			} finally {
				if (db != null) {
					db.close();
				}
			}
		} else if (p.segment(1).equals("file")) { //$NON-NLS-1$
			// /git/remote/{remote}/file/{path}
			RemoteDetailsJob job;
			String commits = request.getParameter(GitConstants.KEY_TAG_COMMITS);
			int commitsNumber = commits == null ? 0 : Integer.parseInt(commits);
			String nameFilter = request.getParameter("filter");
			String page = request.getParameter("page");
			if (page != null) {
				int pageNo = Integer.parseInt(page);
				int pageSize = request.getParameter("pageSize") == null ? PAGE_SIZE : Integer.parseInt(request.getParameter("pageSize"));
				job = new RemoteDetailsJob(TaskJobHandler.getUserId(request), p.segment(0), p.removeFirstSegments(1), BaseToCloneConverter.getCloneLocation(
						getURI(request), BaseToCloneConverter.REMOTE), commitsNumber, pageNo, pageSize, request.getRequestURI(), nameFilter);
			} else {
				job = new RemoteDetailsJob(TaskJobHandler.getUserId(request), p.segment(0), p.removeFirstSegments(1), BaseToCloneConverter.getCloneLocation(
						getURI(request), BaseToCloneConverter.REMOTE), commitsNumber, nameFilter);
			}
			return TaskJobHandler.handleTaskJob(request, response, job, statusHandler, JsonURIUnqualificationStrategy.ALL_NO_GIT);
		} else if (p.segment(2).equals("file")) { //$NON-NLS-1$
			// /git/remote/{remote}/{branch}/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.REMOTE_BRANCH);
			Repository db = null;
			try {
				db = FileRepositoryBuilder.create(gitDir);
				Remote remote = new Remote(cloneLocation, db, p.segment(0));
				RemoteBranch remoteBranch = new RemoteBranch(cloneLocation, db, remote, GitUtils.decode(p.segment(1)));
				if (remoteBranch.exists()) {
					JSONObject result = remoteBranch.toJSON();
					OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
					return true;
				}
			} finally {
				if (db != null) {
					db.close();
				}
			}
			JSONObject errorData = new JSONObject();
			errorData.put(GitConstants.KEY_CLONE, cloneLocation);
			return statusHandler.handleRequest(request, response, new ServerStatus(new Status(IStatus.ERROR, GitActivator.PI_GIT, "No remote branch found: "
					+ p.uptoSegment(2).removeTrailingSeparator()), HttpServletResponse.SC_NOT_FOUND, errorData));
		}
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
				"Bad request, \"/git/remote/{remote}/{branch}/file/{path}\" expected", null));
	}

	// remove remote
	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, URISyntaxException,
			JSONException, ServletException {
		Path p = new Path(path);
		if (p.segment(1).equals("file")) { //$NON-NLS-1$
			// expected path: /gitapi/remote/{remote}/file/{path}
			String remoteName = p.segment(0);

			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			Repository db = null;
			try {
				db = FileRepositoryBuilder.create(gitDir);
				StoredConfig config = db.getConfig();
				config.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName);
				config.save();
				// TODO: handle result
				return true;
			} finally {
				if (db != null) {
					db.close();
				}
			}
		}
		return false;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException,
			URISyntaxException, CoreException {
		Path p = new Path(path);
		if (p.segment(0).equals("file")) { //$NON-NLS-1$
			// handle adding new remote
			// expected path: /git/remote/file/{path}
			return addRemote(request, response, path);
		}
		JSONObject requestObject = OrionServlet.readJSONRequest(request);
		boolean fetch = Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_FETCH, null));
		String srcRef = requestObject.optString(GitConstants.KEY_PUSH_SRC_REF, null);
		boolean tags = requestObject.optBoolean(GitConstants.KEY_PUSH_TAGS, false);
		boolean force = requestObject.optBoolean(GitConstants.KEY_FORCE, false);

		// prepare creds
		GitCredentialsProvider cp = GitUtils.createGitCredentialsProvider(requestObject, request);

		// if all went well, continue with fetch or push
		if (fetch) {
			return fetch(request, response, cp, path, force);
		} else if (srcRef != null) {
			return push(request, response, path, cp, srcRef, tags, force);
		} else {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
					"Only Fetch:true is currently supported.", null));
		}
	}

	// add new remote
	private boolean addRemote(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException,
			CoreException, URISyntaxException {
		// expected path: /git/remote/file/{path}
		Path p = new Path(path);
		JSONObject toPut = OrionServlet.readJSONRequest(request);
		String remoteName = toPut.optString(GitConstants.KEY_REMOTE_NAME, null);
		// remoteName is required
		if (remoteName == null || remoteName.isEmpty() || remoteName.contains(" ")) { //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
					"Remote name must be provided", null));
		}
		String remoteURI = toPut.optString(GitConstants.KEY_REMOTE_URI, null);
		// remoteURI is required
		if (remoteURI == null || remoteURI.isEmpty()) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
					"Remote URI must be provided", null));
		}

		try {
			URIish uri = new URIish(remoteURI);
			if (GitUtils.isForbiddenGitUri(uri)) {
				statusHandler.handleRequest(
						request,
						response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind(
								"Remote URI {0} does not appear to be a git repository", remoteURI), null)); //$NON-NLS-1$
				return false;
			}
		} catch (URISyntaxException e) {
			statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid remote URI: {0}", remoteURI), e)); //$NON-NLS-1$
			return false;
		}

		String fetchRefSpec = toPut.optString(GitConstants.KEY_REMOTE_FETCH_REF, null);
		String remotePushURI = toPut.optString(GitConstants.KEY_REMOTE_PUSH_URI, null);
		String pushRefSpec = toPut.optString(GitConstants.KEY_REMOTE_PUSH_REF, null);

		File gitDir = GitUtils.getGitDir(p);
		URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.REMOTE_LIST);
		Repository db = null;
		try {
			db = FileRepositoryBuilder.create(gitDir);
			StoredConfig config = db.getConfig();

			RemoteConfig rc = new RemoteConfig(config, remoteName);
			rc.addURI(new URIish(remoteURI));
			// FetchRefSpec is required, but default version can be generated
			// if it isn't provided
			if (fetchRefSpec == null || fetchRefSpec.isEmpty()) {
				fetchRefSpec = String.format("+refs/heads/*:refs/remotes/%s/*", remoteName); //$NON-NLS-1$
			}
			rc.addFetchRefSpec(new RefSpec(fetchRefSpec));
			// pushURI is optional
			if (remotePushURI != null && !remotePushURI.isEmpty())
				rc.addPushURI(new URIish(remotePushURI));
			// PushRefSpec is optional
			if (pushRefSpec != null && !pushRefSpec.isEmpty())
				rc.addPushRefSpec(new RefSpec(pushRefSpec));

			rc.update(config);
			config.save();

			Remote remote = new Remote(cloneLocation, db, remoteName);
			JSONObject result = new JSONObject();
			result.put(ProtocolConstants.KEY_LOCATION, remote.getLocation());
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
			response.setStatus(HttpServletResponse.SC_CREATED);
		} finally {
			if (db != null) {
				db.close();
			}
		}
		return true;
	}

	private boolean fetch(HttpServletRequest request, HttpServletResponse response, GitCredentialsProvider cp, String path, boolean force)
			throws URISyntaxException, JSONException, IOException, ServletException {
		// {remote}/{branch}/{file}/{path}
		Path p = new Path(path);
		// check for SSO token
		Object cookie = request.getAttribute(GitConstants.KEY_SSO_TOKEN);
		FetchJob job = new FetchJob(TaskJobHandler.getUserId(request), cp, p, force, cookie);
		return TaskJobHandler.handleTaskJob(request, response, job, statusHandler, JsonURIUnqualificationStrategy.ALL_NO_GIT);
	}

	private boolean push(HttpServletRequest request, HttpServletResponse response, String path, GitCredentialsProvider cp, String srcRef, boolean tags,
			boolean force) throws ServletException, CoreException, IOException, JSONException, URISyntaxException {
		Path p = new Path(path);
		// FIXME: what if a remote or branch is named "file"?
		if (p.segment(2).equals("file")) { //$NON-NLS-1$
			// /git/remote/{remote}/{branch}/file/{path}
			Object cookie = request.getAttribute(GitConstants.KEY_SSO_TOKEN);
			PushJob job = new PushJob(TaskJobHandler.getUserId(request), cp, p, srcRef, tags, force, cookie);
			return TaskJobHandler.handleTaskJob(request, response, job, statusHandler, JsonURIUnqualificationStrategy.ALL_NO_GIT);
		}
		return false;
	}
}
