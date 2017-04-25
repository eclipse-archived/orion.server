/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
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
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.UserInfoResourceHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.git.objects.Tree;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GitTreeHandlerV1 extends AbstractGitHandler {

	GitTreeHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	private JSONObject listEntry(String name, long timeStamp, boolean isDir, long length, URI location, String appendName) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ProtocolConstants.KEY_NAME, name);
			jsonObject.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, timeStamp);
			jsonObject.put(ProtocolConstants.KEY_DIRECTORY, isDir);
			jsonObject.put(ProtocolConstants.KEY_LENGTH, length);
			if (location != null) {
				if (isDir && !location.getPath().endsWith("/")) { //$NON-NLS-1$
					location = URIUtil.append(location, ""); //$NON-NLS-1$
				}
				if (appendName != null) {
					if (!appendName.startsWith("/") && !location.getPath().endsWith("/")) //$NON-NLS-1$  //$NON-NLS-2$
						appendName = "/" + appendName; //$NON-NLS-1$
					location = new URI(location.getScheme(), location.getAuthority(), location.getPath() + appendName, null, location.getFragment());
					if (isDir) {
						location = URIUtil.append(location, ""); //$NON-NLS-1$
					}
				}
				jsonObject.put(ProtocolConstants.KEY_LOCATION, location);
				if (isDir) {
					try {
						jsonObject.put(ProtocolConstants.KEY_CHILDREN_LOCATION, new URI(location.getScheme(), location.getAuthority(), location.getPath(),
								"depth=1", location.getFragment())); //$NON-NLS-1$
					} catch (URISyntaxException e) {
						throw new RuntimeException(e);
					}
				}
			}
			JSONObject attributes = new JSONObject();
			attributes.put("ReadOnly", true); //$NON-NLS-1$
			jsonObject.put(ProtocolConstants.KEY_ATTRIBUTES, attributes);

		} catch (JSONException e) {
		} catch (URISyntaxException e) {
			// cannot happen because the key is non-null and the values are strings
			throw new RuntimeException(e);
		}
		return jsonObject;
	}

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

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String pathString) throws ServletException {
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		int segmentCount = path.segmentCount();
		if (getMethod(request) == Method.GET && (
				segmentCount == 0 || 
				("workspace".equals(path.segment(0)) && path.segmentCount() == 2) || //$NON-NLS-1$
				("file".equals(path.segment(0)) && path.segmentCount() == 2) //$NON-NLS-1$
		)) {
			String userId = request.getRemoteUser();
			if (userId == null) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "User name not specified",
						null));
				return false;
			}
			try {
				UserInfo user = OrionConfiguration.getMetaStore().readUser(userId);
				URI baseLocation = new URI("orion", null, request.getServletPath(), null, null);
				baseLocation = URIUtil.append(baseLocation, Tree.RESOURCE);
				baseLocation = URIUtil.append(baseLocation, "file"); //$NON-NLS-N$
				if (segmentCount == 0) {
					OrionServlet.writeJSONResponse(request, response, UserInfoResourceHandler.toJSON(user, baseLocation), JsonURIUnqualificationStrategy.ALL_NO_GIT);
					return true;
				}
				WorkspaceInfo workspace = OrionConfiguration.getMetaStore().readWorkspace(path.segment(1));
				if (workspace != null) {
					JSONArray children = new JSONArray();
					for (String projectName : workspace.getProjectNames()) {
						ProjectInfo project = OrionConfiguration.getMetaStore().readProject(workspace.getUniqueId(), projectName);
						if (isAccessAllowed(user.getUserName(), project)) {
							IPath projectPath = GitUtils.pathFromProject(workspace, project);
							Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
							for (Map.Entry<IPath, File> entry : gitDirs.entrySet()) {
								JSONObject repo = listEntry(entry.getKey().lastSegment(), 0, true, 0, baseLocation, entry.getKey().toPortableString());
								children.put(repo);
							}
						}
					}
					JSONObject result = listEntry(workspace.getFullName(), 0, true, 0, baseLocation, workspace.getUniqueId()); //$NON-NLS-1$
					result.put(ProtocolConstants.KEY_ID, workspace.getUniqueId());
					result.put(ProtocolConstants.KEY_CHILDREN, children);
					OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
					return true;
				}
			} catch (Exception e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"An error occurred while obtaining workspace data.", e));
			}
			return true;
		}
		return super.handleRequest(request, response, pathString);
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		String gitSegment = requestInfo.gitSegment;
		Repository repo = requestInfo.db;
		String pattern = requestInfo.relativePath;
		IPath filePath = requestInfo.filePath;
		String meta = request.getParameter("parts"); //$NON-NLS-1$
		RevWalk walk = null;
		TreeWalk treeWalk = null;
		IPath filterPath = new Path(pattern);
		try {
			if (filterPath.segmentCount() == 0) {
				JSONArray children = new JSONArray();
				URI baseLocation = getURI(request);
				List<Ref> call = Git.wrap(repo).branchList().setListMode(ListMode.ALL).call();
				for (Ref ref : call) {
					String branchName = Repository.shortenRefName(ref.getName());
					JSONObject branch = listEntry(branchName, 0, true, 0, baseLocation, GitUtils.encode(branchName));
					children.put(branch);
				}
				JSONObject result = listEntry(filePath.segment(0), 0, true, 0, baseLocation, null);
				result.put(ProtocolConstants.KEY_CHILDREN, children);
				OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			}

			gitSegment = GitUtils.decode(filterPath.segment(0));
			filterPath = filterPath.removeFirstSegments(1);
			pattern = filterPath.toPortableString();
			ObjectId head = repo.resolve(gitSegment);
			if (head == null) {
				throw new Exception("Missing ref in git segment");
			}
			walk = new RevWalk(repo);
			// add try catch to catch failures

			RevCommit commit = walk.parseCommit(head);
			RevTree tree = commit.getTree();
			treeWalk = new TreeWalk(repo);
			treeWalk.addTree(tree);
			treeWalk.setRecursive(false);
			if (!pattern.equals("")) { //$NON-NLS-1$
				PathFilter pathFilter = PathFilter.create(pattern);
				treeWalk.setFilter(pathFilter);
			}
			JSONArray contents = new JSONArray();
			JSONObject result = null;
			ArrayList<JSONObject> parents = new ArrayList<JSONObject>();

			URI baseLocation = ServletResourceHandler.getURI(request);
			Path basePath = new Path(baseLocation.getPath());
			IPath tmp = new Path("/"); //$NON-NLS-1$
			for (int i = 0; i < 5; i++) {
				tmp = tmp.append(basePath.segment(i));
			}
			URI cloneLocation = new URI(baseLocation.getScheme(), baseLocation.getAuthority(), tmp.toPortableString(), null, baseLocation.getFragment());
			JSONObject ref = listEntry(gitSegment, 0, true, 0, cloneLocation, GitUtils.encode(gitSegment));

			parents.add(ref);
			parents.add(listEntry(new Path(cloneLocation.getPath()).lastSegment(), 0, true, 0, cloneLocation, null));
			URI locationWalk = URIUtil.append(cloneLocation, GitUtils.encode(gitSegment));
			while (treeWalk.next()) {
				if (treeWalk.isSubtree()) {
					if (treeWalk.getPathLength() > pattern.length()) {
						String name = treeWalk.getNameString();
						contents.put(listEntry(name, 0, true, 0, locationWalk, name));
					}
					if (treeWalk.getPathLength() <= pattern.length()) {
						locationWalk = URIUtil.append(locationWalk, treeWalk.getNameString());
						parents.add(0, listEntry(treeWalk.getNameString(), 0, true, 0, locationWalk, null));
						treeWalk.enterSubtree();
					}
				} else {
					ObjectId objId = treeWalk.getObjectId(0);
					ObjectLoader loader = repo.open(objId);
					long size = loader.getSize();
					if (treeWalk.getPathLength() == pattern.length()) {
						if ("meta".equals(meta)) { //$NON-NLS-1$
							result = listEntry(treeWalk.getNameString(), 0, false, 0, locationWalk, treeWalk.getNameString());
						} else {
							return getFileContents(request, response, repo, treeWalk, tree);
						}
					} else {
						String name = treeWalk.getNameString();
						contents.put(listEntry(name, 0, false, size, locationWalk, name));
					}
				}
			}
			if (result == null) {
				result = parents.remove(0);
				result.put("Children", contents); //$NON-NLS-1$
			}
			result.put("Parents", new JSONArray(parents)); //$NON-NLS-1$
			response.setContentType("application/json"); //$NON-NLS-1$
			response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$
			response.setHeader("ETag", "\"" + tree.getId().getName() + "\""); //$NON-NLS-1$
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when requesting commit info.", e));
		} finally {
			if (walk != null)
				walk.close();
			if (treeWalk != null)
				treeWalk.close();
		}
	}

	private boolean getFileContents(HttpServletRequest request, HttpServletResponse response, Repository repo, TreeWalk treeWalk, RevTree tree) {
		ObjectStream stream = null;
		try {
			ObjectId objId = treeWalk.getObjectId(0);
			ObjectLoader loader = repo.open(objId);
			response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$
			response.setHeader("ETag", "\"" + tree.getId().getName() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			response.setContentType("application/octet-stream"); //$NON-NLS-1$
			stream = loader.openStream();
			IOUtilities.pipe(stream, response.getOutputStream(), true, false);
		} catch (MissingObjectException e) {
		} catch (IOException e) {
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException e) {
			}
		}
		return true;
	}
}
