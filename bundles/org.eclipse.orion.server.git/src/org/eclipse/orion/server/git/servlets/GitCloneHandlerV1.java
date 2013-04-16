/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.internal.server.servlets.workspace.*;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.git.*;
import org.eclipse.orion.server.git.jobs.*;
import org.eclipse.orion.server.git.objects.Clone;
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
		String workspacePath = toAdd.optString(ProtocolConstants.KEY_LOCATION, null);
		// expected path /file/{workspaceId}/{projectName}[/{path}]
		String filePathString = toAdd.optString(ProtocolConstants.KEY_PATH, null);
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
		WebProject webProject = null;
		boolean webProjectExists = false;
		if (filePath != null) {
			//path format is /file/{workspaceId}/{projectName}/[filePath]
			clone.setId(filePath.toString());
			webProject = GitUtils.projectFromPath(filePath);
			//workspace path format needs to be used if project does not exist
			if (webProject == null) {
				String msg = NLS.bind("Specified project does not exist: {0}", filePath.segment(2));
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			webProjectExists = true;
			clone.setContentLocation(webProject.getProjectStore().getFileStore(filePath.removeFirstSegments(3)).toURI());
			if (cloneName == null)
				cloneName = filePath.segmentCount() > 2 ? filePath.lastSegment() : webProject.getName();
		} else if (workspacePath != null) {
			IPath path = new Path(workspacePath);
			// TODO: move this to CloneJob
			// if so, modify init part to create a new project if necessary
			WebWorkspace workspace = WebWorkspace.fromId(path.segment(1));
			String id = WebProject.nextProjectId();
			if (cloneName == null)
				cloneName = new URIish(url).getHumanishName();
			cloneName = getUniqueProjectName(workspace, cloneName);
			webProjectExists = false;
			webProject = WebProject.fromId(id);
			webProject.setName(cloneName);

			try {
				WorkspaceResourceHandler.computeProjectLocation(request, webProject, null, false);
			} catch (CoreException e) {
				//we are unable to write in the platform location!
				String msg = NLS.bind("Server content location could not be written: {0}", Activator.getDefault().getRootLocationURI());
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			} catch (URISyntaxException e) {
				// should not happen, we do not allow linking at this point
			}

			try {
				//If all went well, add project to workspace
				WorkspaceResourceHandler.addProject(request.getRemoteUser(), workspace, webProject);
			} catch (CoreException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error persisting project state", e));
			}

			URI baseLocation = getURI(request);
			baseLocation = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), workspacePath, baseLocation.getQuery(), baseLocation.getFragment());
			clone.setId(GitUtils.pathFromProject(workspace, webProject).toString());
			clone.setContentLocation(webProject.getProjectStore().toURI());
		}
		clone.setName(cloneName);
		clone.setBaseLocation(getURI(request));
		JSONObject cloneObject = clone.toJSON();
		String cloneLocation = cloneObject.getString(ProtocolConstants.KEY_LOCATION);

		if (initOnly) {
			// git init
			InitJob job = new InitJob(clone, TaskJobHandler.getUserId(request), request.getRemoteUser(), cloneLocation);
			return TaskJobHandler.handleTaskJob(request, response, job, statusHandler);
		}
		// git clone
		// prepare creds
		GitCredentialsProvider cp = GitUtils.createGitCredentialsProvider(toAdd);
		cp.setUri(new URIish(clone.getUrl()));

		// if all went well, clone
		CloneJob job = new CloneJob(clone, TaskJobHandler.getUserId(request), cp, request.getRemoteUser(), cloneLocation, webProjectExists ? null : webProject /* used for cleaning up, so null when not needed */);
		return TaskJobHandler.handleTaskJob(request, response, job, statusHandler);
	}

	/**
	 * Returns a unique project name that does not exist in the given workspace,
	 * for the given clone name.
	 */
	private String getUniqueProjectName(WebWorkspace workspace, String cloneName) {
		int i = 1;
		String uniqueName = cloneName;
		while (workspace.getProjectByName(uniqueName) != null) {
			//add an incrementing counter suffix until we arrive at a unique name
			uniqueName = cloneName + '-' + ++i;
		}
		return uniqueName;
	}

	public static void doConfigureClone(Git git, String user) throws IOException, CoreException {
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
		// expected path format is 'workspace/{workspaceId}' or 'file/{workspaceId}/{projectName}/{path}]'
		if ("workspace".equals(path.segment(0)) && path.segmentCount() == 2) { //$NON-NLS-1$
			// all clones in the workspace
			if (WebWorkspace.exists(path.segment(1))) {
				WebWorkspace workspace = WebWorkspace.fromId(path.segment(1));
				JSONObject result = new JSONObject();
				JSONArray children = new JSONArray();
				for (WebProject webProject : workspace.getProjects()) {
					//this is the location of the project metadata
					if (isAccessAllowed(user, webProject)) {
						IPath projectPath = GitUtils.pathFromProject(workspace, webProject);
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
			WebProject webProject = GitUtils.projectFromPath(path);
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
			WebProject webProject = GitUtils.projectFromPath(path);
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

				Git git = new Git(new FileRepository(gitDir));
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
			WebProject webProject = GitUtils.projectFromPath(path);
			if (webProject != null && isAccessAllowed(request.getRemoteUser(), webProject)) {
				File gitDir = GitUtils.getGitDirs(path, Traverse.CURRENT).values().iterator().next();
				Repository repo = new FileRepository(gitDir);
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
	private boolean isAccessAllowed(String userName, WebProject webProject) {
		try {
			UserInfo user = GitActivator.getDefault().getMetastore().readUser(userName);
			for (String workspaceId : user.getWorkspaceIds()) {
				WebWorkspace webWorkspace = WebWorkspace.fromId(workspaceId);
				JSONArray projectsJSON = webWorkspace.getProjectsJSON();
				for (int j = 0; j < projectsJSON.length(); j++) {
					JSONObject project = projectsJSON.getJSONObject(j);
					String projectId = project.getString(ProtocolConstants.KEY_ID);
					if (projectId.equals(webProject.getId()))
						return true;
				}
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
	 * @see WorkspaceResourceHandler#handleRemoveProject(HttpServletRequest, HttpServletResponse, WebWorkspace)
	 * 
	 * @param userName the user name
	 * @param webProject the project to remove
	 * @return ServerStatus <code>OK</code> if the project has been found and successfully removed,
	 * <code>ERROR</code> if an error occurred or the project couldn't be found
	 */
	public static ServerStatus removeProject(String userName, WebProject webProject) {
		try {
			WebUser webUser = WebUser.fromUserId(userName);
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
						try {
							WorkspaceResourceHandler.removeProject(userName, webWorkspace, webProject);
						} catch (CoreException e) {
							//we are unable to write in the platform location!
							String msg = NLS.bind("Server content location could not be written: {0}", Activator.getDefault().getRootLocationURI());
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

	private boolean pull(HttpServletRequest request, HttpServletResponse response, GitCredentialsProvider cp, String path, boolean force) throws URISyntaxException, JSONException, IOException, ServletException {
		Path p = new Path(path); // /{file}/{path}
		PullJob job = new PullJob(TaskJobHandler.getUserId(request), cp, p, force);
		return TaskJobHandler.handleTaskJob(request, response, job, statusHandler);
	}

}
