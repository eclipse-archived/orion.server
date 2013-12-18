/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
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
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WorkspaceResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.core.metastore.*;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.jobs.*;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
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
			IPath filePath = new Path(path);
			if (filePath.segmentCount() > 0 && filePath.segment(0).equals("file") && !AuthorizationService.checkRights(request.getRemoteUser(), "/" + filePath.toString(), request.getMethod())) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return true;
			}

			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, path);
				case PUT :
					return handlePut(request, response, path);
				case POST :
					return handlePost(request, response, path);
				case DELETE :
					return handleDelete(request, response, path);
				default :
					//we don't know how to handle this request
					return false;
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle /git/clone request for {0}", path);
			ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
			LogHelper.log(status);
			return statusHandler.handleRequest(request, response, status);
		}
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String pathString) throws IOException, JSONException, ServletException, URISyntaxException, CoreException, NoHeadException, NoMessageException, ConcurrentRefUpdateException, WrongRepositoryStateException {
		// make sure required fields are set
		JSONObject toAdd = OrionServlet.readJSONRequest(request);
		if (toAdd.optBoolean(GitConstants.KEY_PULL, false)) {
			GitUtils.createGitCredentialsProvider(toAdd);
			GitCredentialsProvider cp = GitUtils.createGitCredentialsProvider(toAdd);
			boolean force = toAdd.optBoolean(GitConstants.KEY_FORCE, false);
			return pull(request, response, cp, pathString, force);
		}

		Clone clone = new Clone();
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
		String workspacePath = ServletResourceHandler.toOrionLocation(request, toAdd.optString(ProtocolConstants.KEY_LOCATION, null));
		// expected path /file/{workspaceId}/{projectName}[/{path}]
		String filePathString = ServletResourceHandler.toOrionLocation(request, toAdd.optString(ProtocolConstants.KEY_PATH, null));
		IPath filePath = filePathString == null ? null : new Path(filePathString);
		if (filePath != null && filePath.segmentCount() < 3)
			filePath = null;
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
		ProjectInfo project = null;
		boolean webProjectExists = false;
		if (filePath != null) {
			//path format is /file/{workspaceId}/{projectName}/[filePath]
			clone.setId(filePath.toString());
			project = GitUtils.projectFromPath(filePath);
			//workspace path format needs to be used if project does not exist
			if (project == null) {
				String msg = NLS.bind("Specified project does not exist: {0}", filePath.segment(2));
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			webProjectExists = true;
			clone.setContentLocation(project.getProjectStore().getFileStore(filePath.removeFirstSegments(3)).toURI());
			if (cloneName == null)
				cloneName = filePath.segmentCount() > 2 ? filePath.lastSegment() : project.getFullName();
		} else if (workspacePath != null) {
			IPath path = new Path(workspacePath);
			// TODO: move this to CloneJob
			// if so, modify init part to create a new project if necessary
			final IMetaStore metaStore = OrionConfiguration.getMetaStore();
			WorkspaceInfo workspace = metaStore.readWorkspace(path.segment(1));
			if (cloneName == null)
				cloneName = new URIish(url).getHumanishName();
			cloneName = getUniqueProjectName(workspace, cloneName);
			webProjectExists = false;
			project = new ProjectInfo();
			project.setFullName(cloneName);
			project.setWorkspaceId(workspace.getUniqueId());

			try {
				//creating project in the backing store will assign a project id
				metaStore.createProject(project);
			} catch (CoreException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error persisting project state", e));
			}
			try {
				WorkspaceResourceHandler.computeProjectLocation(request, project, null, false);
				metaStore.updateProject(project);
			} catch (CoreException e) {
				//delete the project so we don't end up with a project in a bad location
				try {
					metaStore.deleteProject(workspace.getUniqueId(), project.getFullName());
				} catch (CoreException e1) {
					//swallow secondary error
					LogHelper.log(e1);
				}
				//we are unable to write in the platform location!
				String msg = NLS.bind("Failed to create project: {0}", project.getFullName());
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			}

			URI baseLocation = getURI(request);
			baseLocation = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), workspacePath, baseLocation.getQuery(), baseLocation.getFragment());
			clone.setId(GitUtils.pathFromProject(workspace, project).toString());
			clone.setContentLocation(project.getProjectStore().toURI());
		}
		clone.setName(cloneName);
		clone.setBaseLocation(getURI(request));
		JSONObject cloneObject = clone.toJSON();
		String cloneLocation = cloneObject.getString(ProtocolConstants.KEY_LOCATION);

		String gitUserName = toAdd.optString(GitConstants.KEY_NAME, null);
		String gitUserMail = toAdd.optString(GitConstants.KEY_MAIL, null);
		Boolean initProject = toAdd.optBoolean(GitConstants.KEY_INIT_PROJECT, false);
		if (initOnly) {
			// git init
			InitJob job = new InitJob(clone, TaskJobHandler.getUserId(request), request.getRemoteUser(), cloneLocation, gitUserName, gitUserMail);
			return TaskJobHandler.handleTaskJob(request, response, job, statusHandler);
		}
		// git clone
		// prepare creds
		GitCredentialsProvider cp = GitUtils.createGitCredentialsProvider(toAdd);
		cp.setUri(new URIish(clone.getUrl()));

		// if all went well, clone
		CloneJob job = new CloneJob(clone, TaskJobHandler.getUserId(request), cp, request.getRemoteUser(), cloneLocation, webProjectExists ? null : project /* used for cleaning up, so null when not needed */, gitUserName, gitUserMail, initProject);
		return TaskJobHandler.handleTaskJob(request, response, job, statusHandler);
	}

	/**
	 * Returns a unique project name that does not exist in the given workspace,
	 * for the given clone name.
	 */
	private String getUniqueProjectName(WorkspaceInfo workspace, String cloneName) {
		int i = 1;
		String uniqueName = cloneName;
		IMetaStore store = OrionConfiguration.getMetaStore();
		try {
			while (store.readProject(workspace.getUniqueId(), uniqueName) != null) {
				//add an incrementing counter suffix until we arrive at a unique name
				uniqueName = cloneName + '-' + ++i;
			}
		} catch (CoreException e) {
			//let it proceed with current name
		}
		return uniqueName;
	}

	public static void doConfigureClone(Git git, String user, String gitUserName, String gitUserMail) throws IOException, CoreException {
		StoredConfig config = git.getRepository().getConfig();
		if (gitUserName != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, gitUserName);
		if (gitUserMail != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, gitUserMail);
		/*
		IOrionUserProfileNode userNode = UserServiceHelper.getDefault().getUserProfileService().getUserProfileNode(user, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
		if (userNode != null && userNode.get(GitConstants.KEY_NAME, null) != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, userNode.get(GitConstants.KEY_NAME, null));
		if (userNode != null && userNode.get(GitConstants.KEY_MAIL, null) != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, userNode.get(GitConstants.KEY_MAIL, null));
		*/
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_FILEMODE, false);
		config.save();
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String pathString) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		URI baseLocation = getURI(request);
		String user = request.getRemoteUser();
		// expected path format is 'workspace/{workspaceId}' or 'file/{workspaceId}/{projectName}/{path}]'
		if ("workspace".equals(path.segment(0)) && path.segmentCount() == 2) { //$NON-NLS-1$
			// all clones in the workspace
			WorkspaceInfo workspace = OrionConfiguration.getMetaStore().readWorkspace(path.segment(1));
			if (workspace != null) {
				JSONObject result = new JSONObject();
				JSONArray children = new JSONArray();
				for (String projectName : workspace.getProjectNames()) {
					ProjectInfo project = OrionConfiguration.getMetaStore().readProject(workspace.getUniqueId(), projectName);
					//this is the location of the project metadata
					if (isAccessAllowed(user, project)) {
						IPath projectPath = GitUtils.pathFromProject(workspace, project);
						Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
						for (Map.Entry<IPath, File> entry : gitDirs.entrySet()) {
							children.put(new Clone().toJSON(entry, baseLocation));
						}
					}
				}
				result.put(ProtocolConstants.KEY_TYPE, Clone.TYPE);
				result.put(ProtocolConstants.KEY_CHILDREN, children);
				OrionServlet.writeJSONResponse(request, response, result);
				return true;
			}
			String msg = NLS.bind("Nothing found for the given ID: {0}", path);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		} else if ("file".equals(path.segment(0)) && path.segmentCount() > 1) { //$NON-NLS-1$
			// clones under given path
			ProjectInfo webProject = GitUtils.projectFromPath(path);
			IPath projectRelativePath = path.removeFirstSegments(3);
			if (webProject != null && isAccessAllowed(user, webProject) && webProject.getProjectStore().getFileStore(projectRelativePath).fetchInfo().exists()) {
				Map<IPath, File> gitDirs = GitUtils.getGitDirs(path, Traverse.GO_DOWN);
				JSONObject result = new JSONObject();
				JSONArray children = new JSONArray();
				for (Map.Entry<IPath, File> entry : gitDirs.entrySet()) {
					children.put(new Clone().toJSON(entry, baseLocation));
				}
				result.put(ProtocolConstants.KEY_TYPE, Clone.TYPE);
				result.put(ProtocolConstants.KEY_CHILDREN, children);
				OrionServlet.writeJSONResponse(request, response, result);
				return true;
			}
			String msg = NLS.bind("Nothing found for the given ID: {0}", path);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		}
		//else the request is malformed
		String msg = NLS.bind("Invalid clone request: {0}", path);
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, String pathString) throws GitAPIException, CoreException, IOException, JSONException, ServletException {
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		if (path.segment(0).equals("file") && path.segmentCount() > 1) { //$NON-NLS-1$

			// make sure a clone is addressed
			ProjectInfo webProject = GitUtils.projectFromPath(path);
			if (isAccessAllowed(request.getRemoteUser(), webProject)) {
				Map<IPath, File> gitDirs = GitUtils.getGitDirs(path, Traverse.CURRENT);
				if (gitDirs.isEmpty()) {
					String msg = NLS.bind("Request path is not a git repository: {0}", path);
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				}
				File gitDir = gitDirs.values().iterator().next();

				// make sure required fields are set
				JSONObject toCheckout = OrionServlet.readJSONRequest(request);
				JSONArray paths = toCheckout.optJSONArray(ProtocolConstants.KEY_PATH);
				String branch = toCheckout.optString(GitConstants.KEY_BRANCH_NAME, null);
				String tag = toCheckout.optString(GitConstants.KEY_TAG_NAME, null);
				boolean removeUntracked = toCheckout.optBoolean(GitConstants.KEY_REMOVE_UNTRACKED, false);
				if ((paths == null || paths.length() == 0) && branch == null && tag == null) {
					String msg = NLS.bind("Either '{0}' or '{1}' or '{2}' should be provided, got: {3}", new Object[] {ProtocolConstants.KEY_PATH, GitConstants.KEY_BRANCH_NAME, GitConstants.KEY_TAG_NAME, toCheckout});
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				}

				Git git = new Git(FileRepositoryBuilder.create(gitDir));
				if (paths != null) {
					Set<String> toRemove = new HashSet<String>();
					CheckoutCommand checkout = git.checkout();
					for (int i = 0; i < paths.length(); i++) {
						String p = paths.getString(i);
						if (removeUntracked && !isInIndex(git.getRepository(), p))
							toRemove.add(p);
						checkout.addPath(p);
					}
					checkout.call();
					for (String p : toRemove) {
						File f = new File(git.getRepository().getWorkTree(), p);
						f.delete();
					}
					return true;
				} else if (tag != null && branch != null) {
					CheckoutCommand co = git.checkout();
					try {
						co.setName(branch).setStartPoint(tag).setCreateBranch(true).call();
						return true;
					} catch (RefNotFoundException e) {
						String msg = NLS.bind("Tag not found: {0}", tag);
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, e));
					} catch (GitAPIException e) {
						if (org.eclipse.jgit.api.CheckoutResult.Status.CONFLICTS.equals(co.getResult().getStatus())) {
							return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CONFLICT, "Checkout aborted.", e));
						}
						// TODO: handle other exceptions
					}
				} else if (branch != null) {

					if (!isLocalBranch(git, branch)) {
						String msg = NLS.bind("{0} is not a branch.", branch);
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
					}

					CheckoutCommand co = git.checkout();
					try {
						co.setName(Constants.R_HEADS + branch).call();
						return true;
					} catch (CheckoutConflictException e) {
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CONFLICT, "Checkout aborted.", e));
					} catch (RefNotFoundException e) {
						String msg = NLS.bind("Branch name not found: {0}", branch);
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, e));
					} // TODO: handle other exceptions
				}
			} else {
				String msg = NLS.bind("Nothing found for the given ID: {0}", path);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		}
		String msg = NLS.bind("Invalid checkout request {0}", pathString);
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	private boolean isLocalBranch(Git git, String branch) throws GitAPIException {
		List<Ref> branches = git.branchList().call();
		for (Ref ref : branches) {
			if (Repository.shortenRefName(ref.getName()).equals(branch))
				return true;
		}
		return false;
	}

	private boolean isInIndex(Repository db, String path) throws IOException {
		DirCache dc = DirCache.read(db.getIndexFile(), db.getFS());
		return dc.getEntry(path) != null;
	}

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, String pathString) throws GitAPIException, CoreException, IOException, ServletException {
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		//expected path format is /file/{workspaceId}/{projectId}[/{directoryPath}]
		if (path.segment(0).equals("file") && path.segmentCount() > 2) { //$NON-NLS-1$

			// make sure a clone is addressed
			ProjectInfo webProject = GitUtils.projectFromPath(path);
			if (webProject != null && isAccessAllowed(request.getRemoteUser(), webProject)) {
				File gitDir = GitUtils.getGitDirs(path, Traverse.CURRENT).values().iterator().next();
				Repository repo = FileRepositoryBuilder.create(gitDir);
				repo.close();
				FileUtils.delete(repo.getWorkTree(), FileUtils.RECURSIVE | FileUtils.RETRY);
				if (path.segmentCount() == 3)
					return statusHandler.handleRequest(request, response, removeProject(request.getRemoteUser(), webProject));
				return true;
			}
			String msg = NLS.bind("Nothing found for the given ID: {0}", path);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		}
		String msg = NLS.bind("Invalid delete request {0}", pathString);
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	/**
	 * Returns whether the user can access the given project
	 */
	private boolean isAccessAllowed(String userName, ProjectInfo webProject) {
		try {
			UserInfo user = OrionConfiguration.getMetaStore().readUser(userName);
			for (String workspaceId : user.getWorkspaceIds()) {
				WorkspaceInfo workspace = OrionConfiguration.getMetaStore().readWorkspace(workspaceId);
				if (workspace != null && workspace.getProjectNames().contains(webProject.getFullName()))
					return true;
			}
		} catch (Exception e) {
			// fall through and deny access
			LogHelper.log(e);
		}
		return false;
	}

	/**
	 * Looks for the project in all workspaces of the user and removes it when found.
	 *
	 * @see WorkspaceResourceHandler#handleRemoveProject(HttpServletRequest, HttpServletResponse, WorkspaceInfo)
	 *
	 * @param userId the user name
	 * @param project the project to remove
	 * @return ServerStatus <code>OK</code> if the project has been found and successfully removed,
	 * <code>ERROR</code> if an error occurred or the project couldn't be found
	 */
	public static ServerStatus removeProject(String userId, ProjectInfo project) {
		try {
			UserInfo user = OrionConfiguration.getMetaStore().readUser(userId);
			for (String workspaceId : user.getWorkspaceIds()) {
				WorkspaceInfo workspace = OrionConfiguration.getMetaStore().readWorkspace(workspaceId);
				if (workspace == null)
					continue;
				for (String projectName : workspace.getProjectNames()) {
					if (projectName.equals(project.getFullName())) {
						//If found, remove project from workspace
						try {
							WorkspaceResourceHandler.removeProject(userId, workspace, project);
						} catch (CoreException e) {
							//we are unable to write in the platform location!
							String msg = NLS.bind("Failed to remove project: {0}", project.getFullName());
							return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
						}

						return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null);
					}
				}
			}
		} catch (Exception e) {
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
		if (name == null) {
			return true;
		}
		if (name.trim().length() == 0) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Repository name cannot be empty", null));
			return false;
		}
		if (name.contains("/")) { //$NON-NLS-1$
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid repository name: {0}", name), null));
			return false;
		}
		return true;
	}

	private boolean validateCloneUrl(String url, HttpServletRequest request, HttpServletResponse response) throws ServletException {
		if (url == null || url.trim().length() == 0) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Clone URL cannot be empty", null)); //$NON-NLS-1$
			return false;
		}
		try {
			URIish uri = new URIish(url);
			if (GitUtils.isForbiddenGitUri(uri)) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Clone URL {0} does not appear to be a git repository", uri), null)); //$NON-NLS-1$
				return false;
			}
		} catch (URISyntaxException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid clone URL: {0}", url), e)); //$NON-NLS-1$
			return false;
		}
		return true;
	}

	private boolean pull(HttpServletRequest request, HttpServletResponse response, GitCredentialsProvider cp, String path, boolean force) throws URISyntaxException, JSONException, IOException, ServletException {
		Path p = new Path(path); // /{file}/{path}
		PullJob job = new PullJob(TaskJobHandler.getUserId(request), cp, p, force);
		return TaskJobHandler.handleTaskJob(request, response, job, statusHandler);
	}

}
