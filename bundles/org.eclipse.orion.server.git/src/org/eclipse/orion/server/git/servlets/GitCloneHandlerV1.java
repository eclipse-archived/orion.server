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
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.*;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
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
				case PUT :
					return handlePut(request, response, path);
				case POST :
					return handlePost(request, response);
				case DELETE :
					return handleDelete(request, response, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle /git/clone request for {0}", path);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response) throws IOException, JSONException, ServletException, URISyntaxException, CoreException, NoHeadException, NoMessageException, ConcurrentRefUpdateException, JGitInternalException, WrongRepositoryStateException {
		// make sure required fields are set
		JSONObject toAdd = OrionServlet.readJSONRequest(request);
		WebClone clone = new WebClone();
		String url = toAdd.optString(GitConstants.KEY_URL, null);
		// method handles repository clone or just repository init
		// decision is based on existence of GitUrl argument 
		boolean initOnly;
		if (url == null || url.isEmpty())
			initOnly = true;
		else {
			initOnly = false;
			if (!validateCloneUrl(url, request, response))
				return true;
			clone.setUrl(new URIish(url));
		}
		String cloneName = toAdd.optString(ProtocolConstants.KEY_NAME, null);
		if (cloneName == null)
			cloneName = request.getHeader(ProtocolConstants.HEADER_SLUG);
		// expected path /workspace/{workspaceId}
		String workspacePath = toAdd.optString(ProtocolConstants.KEY_LOCATION, null);
		// expected path /file/{projectId}[/{path}]
		String filePath = toAdd.optString(ProtocolConstants.KEY_PATH, null);
		if (filePath == null && workspacePath == null) {
			String msg = NLS.bind("Either {0} or {1} should be provided: {2}", new Object[] {ProtocolConstants.KEY_PATH, ProtocolConstants.KEY_LOCATION, toAdd});
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}
		// only during init operation filePath or cloneName must be provided
		// during clone operation, name can be obtained from URL
		if (initOnly && filePath == null && cloneName == null) {
			String msg = NLS.bind("Either {0} or {1} should be provided: {2}", new Object[] {ProtocolConstants.KEY_PATH, GitConstants.KEY_NAME, toAdd});
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}
		if (!validateCloneName(cloneName, request, response))
			return true;

		// prepare the WebClone object, create a new project if necessary
		WebProject webProject = null;
		boolean webProjectExists = false;
		if (filePath != null) {
			IPath path = new Path(filePath);
			clone.setId(path.removeFirstSegments(1).toString());
			webProject = WebProject.fromId(path.segment(1));
			webProjectExists = WebProject.exists(path.segment(1));
			clone.setContentLocation(webProject.getProjectStore().getFileStore(path.removeFirstSegments(2)).toURI());
			if (cloneName == null)
				cloneName = path.segmentCount() > 2 ? path.lastSegment() : webProject.getName();
		} else if (workspacePath != null) {
			IPath path = new Path(workspacePath);
			// TODO: move this to CloneJob
			// if so, modify init part to create a new project if necessary
			String id = WebProject.nextProjectId();
			webProjectExists = false;
			webProject = WebProject.fromId(id);
			if (cloneName == null)
				cloneName = new URIish(url).getHumanishName();
			webProject.setName(cloneName);

			try {
				WorkspaceResourceHandler.computeProjectLocation(webProject, null, request.getRemoteUser(), false);
			} catch (CoreException e) {
				//we are unable to write in the platform location!
				String msg = NLS.bind("Server content location could not be written: {0}", Activator.getDefault().getRootLocationURI());
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			} catch (URISyntaxException e) {
				// should not happen, we do not allow linking at this point
			}
			WebWorkspace workspace = WebWorkspace.fromId(path.segment(1));
			//If all went well, add project to workspace
			workspace.addProject(webProject);

			//save the workspace and project metadata
			try {
				webProject.save();
				workspace.save();
			} catch (CoreException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error persisting project state", e));
			}

			URI baseLocation = getURI(request);
			baseLocation = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), workspacePath, baseLocation.getQuery(), baseLocation.getFragment());
			JSONObject jsonProject = WebProjectResourceHandler.toJSON(webProject, baseLocation);
			// give the clone creator access rights to the new project
			addProjectRights(request, response, jsonProject.optString(ProtocolConstants.KEY_LOCATION));
			addProjectRights(request, response, jsonProject.optString(ProtocolConstants.KEY_CONTENT_LOCATION));

			clone.setId(webProject.getId());
			clone.setContentLocation(webProject.getProjectStore().toURI());
		}
		clone.setName(cloneName);
		JSONObject cloneObject = WebClone.toJSON(clone, getURI(request));
		String cloneLocation = cloneObject.getString(ProtocolConstants.KEY_LOCATION);

		if (initOnly) {
			// git init
			InitJob job = new InitJob(clone, request.getRemoteUser(), cloneLocation);
			job.schedule();
			TaskInfo task = job.getTask();
			JSONObject result = task.toJSON();
			// Not nice that the git service knows the location of the task servlet, but task service doesn't know this either
			String taskLocation = getURI(request).resolve("../../task/id/" + task.getTaskId()).toString(); //$NON-NLS-1$
			result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, taskLocation);
			OrionServlet.writeJSONResponse(request, response, result);
			response.setStatus(HttpServletResponse.SC_ACCEPTED);
			return true;
		} else {
			// git clone
			// prepare creds
			String username = toAdd.optString(GitConstants.KEY_USERNAME, null);
			char[] password = toAdd.optString(GitConstants.KEY_PASSWORD, "").toCharArray(); //$NON-NLS-1$
			String knownHosts = toAdd.optString(GitConstants.KEY_KNOWN_HOSTS, null);
			byte[] privateKey = toAdd.optString(GitConstants.KEY_PRIVATE_KEY, "").getBytes(); //$NON-NLS-1$
			byte[] publicKey = toAdd.optString(GitConstants.KEY_PUBLIC_KEY, "").getBytes(); //$NON-NLS-1$
			byte[] passphrase = toAdd.optString(GitConstants.KEY_PASSPHRASE, "").getBytes(); //$NON-NLS-1$

			GitCredentialsProvider cp = new GitCredentialsProvider(new URIish(clone.getUrl()), username, password, knownHosts);
			cp.setPrivateKey(privateKey);
			cp.setPublicKey(publicKey);
			cp.setPassphrase(passphrase);

			// if all went well, clone
			CloneJob job = new CloneJob(clone, cp, request.getRemoteUser(), cloneLocation, webProjectExists ? null : webProject /* used for cleaning up, so null when not needed */);
			job.schedule();
			TaskInfo task = job.getTask();
			JSONObject result = task.toJSON();
			// Not nice that the git service knows the location of the task servlet, but task service doesn't know this either
			String taskLocation = getURI(request).resolve("../../task/id/" + task.getTaskId()).toString(); //$NON-NLS-1$
			result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, taskLocation);
			OrionServlet.writeJSONResponse(request, response, result);
			response.setStatus(HttpServletResponse.SC_ACCEPTED);
			return true;
		}
	}

	static void doConfigureClone(Git git, String user) throws IOException, CoreException {
		StoredConfig config = git.getRepository().getConfig();
		IOrionUserProfileNode userNode = UserServiceHelper.getDefault().getUserProfileService().getUserProfileNode(user, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
		if (userNode.get(GitConstants.KEY_NAME, null) != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, userNode.get(GitConstants.KEY_NAME, null));
		if (userNode.get(GitConstants.KEY_MAIL, null) != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, userNode.get(GitConstants.KEY_MAIL, null));
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_FILEMODE, false);
		config.save();
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String pathString) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		URI baseLocation = getURI(request);
		String user = request.getRemoteUser();
		// expected path format is 'workspace/{workspaceId}' or 'file/{projectId}[/{path}]'
		if (path.segment(0).equals("workspace") && path.segmentCount() == 2) { //$NON-NLS-1$
			// all clones in the workspace
			if (WebWorkspace.exists(path.segment(1))) {
				WebWorkspace workspace = WebWorkspace.fromId(path.segment(1));
				JSONArray projects = workspace.getProjectsJSON();
				JSONObject result = new JSONObject();
				JSONArray children = new JSONArray();
				for (int i = 0; i < projects.length(); i++) {
					try {
						JSONObject project = (JSONObject) projects.get(i);
						//this is the location of the project metadata
						WebProject webProject = WebProject.fromId(project.getString(ProtocolConstants.KEY_ID));
						if (isAccessAllowed(user, webProject)) {
							URI contentLocation = URI.create(webProject.getId());
							IPath projectPath = new Path(contentLocation.getPath());
							Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
							for (Map.Entry<IPath, File> entry : gitDirs.entrySet()) {
								JSONObject child = toJSON(entry, baseLocation);
								children.put(child);
							}
						}
					} catch (JSONException e) {
						//ignore malformed children
					}
				}
				result.put(ProtocolConstants.KEY_CHILDREN, children);
				OrionServlet.writeJSONResponse(request, response, result);
				return true;
			} else {
				String msg = NLS.bind("Nothing found for the given ID: {0}", path);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		} else if (path.segment(0).equals("file") && path.segmentCount() > 1) { //$NON-NLS-1$
			// clones under given path
			WebProject webProject = WebProject.fromId(path.segment(1));
			if (isAccessAllowed(user, webProject) && webProject.getProjectStore().getFileStore(path.removeFirstSegments(2)).fetchInfo().exists()) {
				URI contentLocation = URI.create(webProject.getId());
				IPath projectPath = new Path(contentLocation.getPath()).append(path.removeFirstSegments(2));
				Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
				JSONObject result = new JSONObject();
				JSONArray children = new JSONArray();
				for (Map.Entry<IPath, File> entry : gitDirs.entrySet()) {
					JSONObject child = toJSON(entry, baseLocation);
					children.put(child);
				}
				result.put(ProtocolConstants.KEY_CHILDREN, children);
				OrionServlet.writeJSONResponse(request, response, result);
				return true;
			} else {
				String msg = NLS.bind("Nothing found for the given ID: {0}", path);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		}
		//else the request is malformed
		String msg = NLS.bind("Invalid clone request: {0}", path);
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, String pathString) throws IOException, JSONException, ServletException, URISyntaxException, CoreException, JGitInternalException, GitAPIException {
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		if (path.segment(0).equals("file") && path.segmentCount() > 1) { //$NON-NLS-1$

			// make sure a clone is addressed
			WebProject webProject = WebProject.fromId(path.segment(1));
			if (isAccessAllowed(request.getRemoteUser(), webProject)) {
				URI contentLocation = URI.create(webProject.getId());
				IPath projectPath = new Path(contentLocation.getPath()).append(path.removeFirstSegments(2));
				projectPath = path.hasTrailingSeparator() ? projectPath : projectPath.removeLastSegments(1);
				File gitDir = GitUtils.getGitDirs(new Path("file").append(projectPath), Traverse.CURRENT).values().iterator().next(); //$NON-NLS-1$

				// make sure required fields are set
				JSONObject toCheckout = OrionServlet.readJSONRequest(request);
				JSONArray paths = toCheckout.optJSONArray(ProtocolConstants.KEY_PATH);
				String branch = toCheckout.optString(GitConstants.KEY_BRANCH_NAME);
				if ((paths == null || paths.length() == 0) && (branch == null || branch.isEmpty())) {
					String msg = NLS.bind("Either '{0}' or '{1}' should be provided: {2}", new Object[] {ProtocolConstants.KEY_PATH, GitConstants.KEY_BRANCH_NAME, toCheckout});
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				}

				Git git = new Git(new FileRepository(gitDir));
				if (paths != null) {
					CheckoutCommand checkout = git.checkout();
					for (int i = 0; i < paths.length(); i++) {
						checkout.addPath(paths.getString(i));
					}
					checkout.call();
					return true;
				} else if (branch != null) {
					CheckoutCommand co = git.checkout();
					try {
						co.setName(branch).call();
						return true;
					} catch (JGitInternalException e) {
						if (Status.CONFLICTS.equals(co.getResult().getStatus())) {
							return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CONFLICT, "Checkout aborted.", e));
						}
						// TODO: handle other exceptions
					} catch (RefNotFoundException e) {
						String msg = NLS.bind("Branch name not found: {0}", branch);
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, e));
					}

				}
			} else {
				String msg = NLS.bind("Nothing found for the given ID: {0}", path);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		}
		String msg = NLS.bind("Invalid checkout request {0}", pathString);
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, String pathString) throws IOException, JSONException, ServletException, URISyntaxException, CoreException, JGitInternalException, GitAPIException {
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		if (path.segment(0).equals("file") && path.segmentCount() > 1) { //$NON-NLS-1$

			// make sure a clone is addressed
			WebProject webProject = WebProject.fromId(path.segment(1));
			if (isAccessAllowed(request.getRemoteUser(), webProject)) {
				URI contentLocation = URI.create(webProject.getId());
				IPath projectPath = new Path(contentLocation.getPath()).append(path.removeFirstSegments(2));
				File gitDir = GitUtils.getGitDirs(new Path("file").append(projectPath), Traverse.CURRENT).values().iterator().next();

				Repository repo = new FileRepository(gitDir);
				repo.close();
				FileUtils.delete(repo.getWorkTree(), FileUtils.RECURSIVE | FileUtils.RETRY);
				if (path.segmentCount() == 2)
					return statusHandler.handleRequest(request, response, removeProject(request.getRemoteUser(), webProject));
				return true;
			} else {
				String msg = NLS.bind("Nothing found for the given ID: {0}", path);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		}
		String msg = NLS.bind("Invalid delete request {0}", pathString);
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	/**
	 * Returns whether the user can access the given project
	 */
	private boolean isAccessAllowed(String userName, WebProject webProject) {
		try {
			WebUser webUser = WebUser.fromUserName(userName);
			JSONArray workspacesJSON = webUser.getWorkspacesJSON();
			for (int i = 0; i < workspacesJSON.length(); i++) {
				JSONObject workspace = workspacesJSON.getJSONObject(i);
				String workspaceId = workspace.getString(ProtocolConstants.KEY_ID);
				WebWorkspace webWorkspace = WebWorkspace.fromId(workspaceId);
				JSONArray projectsJSON = webWorkspace.getProjectsJSON();
				for (int j = 0; j < projectsJSON.length(); j++) {
					JSONObject project = projectsJSON.getJSONObject(j);
					String projectId = project.getString(ProtocolConstants.KEY_ID);
					if (projectId.equals(webProject.getId()))
						return true;
				}
			}
		} catch (JSONException e) {
			// ignore, deny access
		}
		return false;
	}

	/**
	 * Looks for the project in all workspaces of the user and removes it when found.
	 * 
	 * @see WorkspaceResourceHandler#handleRemoveProject(HttpServletRequest, HttpServletResponse, WebWorkspace)
	 * 
	 * @param userName the user name
	 * @param webProject the project to remove
	 * @return ServerStatus <code>OK</code> if the project has been found and successfully removed,
	 * <code>ERROR</code> if an error occurred or the project couldn't be found
	 */
	static ServerStatus removeProject(String userName, WebProject webProject) {
		try {
			WebUser webUser = WebUser.fromUserName(userName);
			JSONArray workspacesJSON = webUser.getWorkspacesJSON();
			for (int i = 0; i < workspacesJSON.length(); i++) {
				JSONObject workspace = workspacesJSON.getJSONObject(i);
				String workspaceId = workspace.getString(ProtocolConstants.KEY_ID);
				WebWorkspace webWorkspace = WebWorkspace.fromId(workspaceId);
				JSONArray projectsJSON = webWorkspace.getProjectsJSON();
				for (int j = 0; j < projectsJSON.length(); j++) {
					JSONObject project = projectsJSON.getJSONObject(j);
					String projectId = project.getString(ProtocolConstants.KEY_ID);
					if (projectId.equals(webProject.getId())) {

						//If found, remove project from workspace
						webWorkspace.removeProject(webProject);

						// remove the project folder
						try {
							WorkspaceResourceHandler.removeProject(webProject, userName);
						} catch (CoreException e) {
							//we are unable to write in the platform location!
							String msg = NLS.bind("Server content location could not be written: {0}", Activator.getDefault().getRootLocationURI());
							return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
						}

						//save the workspace and project metadata
						try {
							webProject.save();
							webWorkspace.save();
						} catch (CoreException e) {
							String msg = "Error persisting project state";
							return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
						}

						return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null);
					}
				}
			}
		} catch (JSONException e) {
			// ignore, no project will be harmed
		}
		// FIXME: not sure about this one
		return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null);
	}

	/**
	 * Validates that the provided clone name is valid. Returns
	 * <code>true</code> if the clone name is valid, and <code>false</code>
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

	private JSONObject toJSON(Entry<IPath, File> entry, URI baseLocation) throws URISyntaxException {
		IPath k = entry.getKey();
		JSONObject result = new JSONObject();
		try {
			result.put(ProtocolConstants.KEY_ID, k);

			result.put(ProtocolConstants.KEY_NAME, k.segmentCount() == 1 ? WebProject.fromId(k.segment(0)).getName() : k.lastSegment());

			IPath np = new Path(GitServlet.GIT_URI).append(GitConstants.CLONE_RESOURCE).append("file").append(k); //$NON-NLS-1$
			URI location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(ProtocolConstants.KEY_LOCATION, location);

			np = new Path("file").append(k).makeAbsolute(); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(ProtocolConstants.KEY_CONTENT_LOCATION, location);

			np = new Path(GitServlet.GIT_URI).append(GitConstants.REMOTE_RESOURCE).append("file").append(k); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_REMOTE, location);

			np = new Path(GitServlet.GIT_URI).append(GitConstants.CONFIG_RESOURCE).append(GitConstants.CLONE_RESOURCE).append("file").append(k); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_CONFIG, location);

			np = new Path(GitServlet.GIT_URI).append(GitConstants.COMMIT_RESOURCE).append(Constants.HEAD).append("file").append(k); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_HEAD, location);

			np = new Path(GitServlet.GIT_URI).append(GitConstants.BRANCH_RESOURCE).append("file").append(k); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_BRANCH, location);

			try {
				FileBasedConfig config = new FileRepository(entry.getValue()).getConfig();
				String remoteUri = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL);
				if (remoteUri != null)
					result.put(GitConstants.KEY_URL, remoteUri);
			} catch (IOException e) {
				// ignore and skip Git URL
			}
		} catch (JSONException e) {
			//cannot happen, we know keys and values are valid
		}
		return result;
	}

	/**
	 * It's a copy @see WorkspaceResourceHandler#addProjectRights().
	 * TODO: We can do better than that.
	 *
	 * @param request
	 * @param response
	 * @param location
	 * @throws ServletException
	 */
	private void addProjectRights(HttpServletRequest request, HttpServletResponse response, String location) throws ServletException {
		if (location == null)
			return;
		try {
			String locationPath = URI.create(location).getPath();
			//right to access the location
			AuthorizationService.addUserRight(request.getRemoteUser(), locationPath);
			//right to access all children of the location
			if (locationPath.endsWith("/")) //$NON-NLS-1$
				locationPath += "*"; //$NON-NLS-1$
			else
				locationPath += "/*"; //$NON-NLS-1$
			AuthorizationService.addUserRight(request.getRemoteUser(), locationPath);
		} catch (CoreException e) {
			statusHandler.handleRequest(request, response, e.getStatus());
		}
	}
}
