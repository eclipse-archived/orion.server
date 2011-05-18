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
import java.util.*;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.*;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
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
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, path);
				case PUT :
					return handlePut(request, response, path);
				case POST :
					return handlePost(request, response);
					// case DELETE :
					// return handleDelete(request, response, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle /git/clone request for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		// make sure required fields are set
		JSONObject toAdd = OrionServlet.readJSONRequest(request);
		WebClone clone = new WebClone();
		String url = toAdd.optString(GitConstants.KEY_URL, null);
		if (!validateCloneUrl(url, request, response))
			return true;
		clone.setUrl(new URIish(url));
		// expected path /file/{projectId}[/{path}]
		IPath path = new Path(toAdd.getString(ProtocolConstants.KEY_LOCATION));
		clone.setId(path.removeFirstSegments(1).toString());
		WebProject webProject = WebProject.fromId(path.segment(1));
		clone.setContentLocation(webProject.getProjectStore().getFileStore(path.removeFirstSegments(2)).toURI());
		String cloneName = path.segmentCount() > 2 ? path.lastSegment() : webProject.getName();
		if (!validateCloneName(cloneName, request, response))
			return true;
		clone.setName(cloneName);

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

		JSONObject cloneObject = WebClone.toJSON(clone, getURI(request));
		String cloneLocation = cloneObject.getString(ProtocolConstants.KEY_LOCATION);

		// if all went well, clone
		CloneJob job = new CloneJob(clone, cp, request.getRemoteUser(), cloneLocation);
		job.schedule();
		TaskInfo task = job.getTask();
		JSONObject result = task.toJSON();
		//Not nice that the git service knows the location of the task servlet, but task service doesn't know this either
		String taskLocation = getURI(request).resolve("../../task/id/" + task.getTaskId()).toString(); //$NON-NLS-1$
		result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, taskLocation);
		OrionServlet.writeJSONResponse(request, response, result);
		response.setStatus(HttpServletResponse.SC_ACCEPTED);
		return true;
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
				if (projects == null)
					projects = new JSONArray();
				JSONObject result = new JSONObject();
				JSONArray children = new JSONArray();
				for (int i = 0; i < projects.length(); i++) {
					try {
						JSONObject project = (JSONObject) projects.get(i);
						//this is the location of the project metadata
						WebProject webProject = WebProject.fromId(project.getString(ProtocolConstants.KEY_ID));
						if (isAccessAllowed(user, webProject)) {
							URI contentLocation = webProject.getContentLocation();
							IPath projectPath = new Path(contentLocation.getPath());
							Map<IPath, File> gitDirs = new HashMap<IPath, File>();
							GitUtils.getGitDirs(projectPath, gitDirs);
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
			if (isAccessAllowed(user, webProject)) {
				URI contentLocation = webProject.getContentLocation();
				IPath projectPath = new Path(contentLocation.getPath()).append(path.removeFirstSegments(2));
				Map<IPath, File> gitDirs = new HashMap<IPath, File>();
				GitUtils.getGitDirs(projectPath, gitDirs);
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
				URI contentLocation = webProject.getContentLocation();
				IPath projectPath = new Path(contentLocation.getPath()).append(path.removeFirstSegments(2));
				// FIXME: don't go up
				File gitDir = GitUtils.getGitDir(new Path("file").append(projectPath));

				// make sure required fields are set
				JSONObject toCheckout = OrionServlet.readJSONRequest(request);
				JSONArray paths = toCheckout.optJSONArray(GitConstants.KEY_PATH);
				String branch = toCheckout.optString(GitConstants.KEY_BRANCH);
				if ((paths == null || paths.length() == 0) && (branch == null || branch.isEmpty())) {
					String msg = NLS.bind("Either '" + GitConstants.KEY_PATH + "' or '" + GitConstants.KEY_BRANCH + "' should be provided: {0}", toCheckout);
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
					try {
						Ref r = git.checkout().setName(branch).call();
						// TODO: handle result and exceptions
						return true;
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

}
