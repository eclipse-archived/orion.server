/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.*;

public class GitTreeHandlerV1 extends AbstractGitHandler {

	GitTreeHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	private JSONObject listEntry(String name, long timeStamp, boolean isDir, long length, URI location, boolean appendName) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ProtocolConstants.KEY_NAME, name);
			jsonObject.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, timeStamp);
			jsonObject.put(ProtocolConstants.KEY_DIRECTORY, isDir);
			jsonObject.put(ProtocolConstants.KEY_LENGTH, length);
			if (location != null) {
				if (isDir && !location.getPath().endsWith("/")) {
					location = URIUtil.append(location, "");
				}
				if (appendName) {
					location = URIUtil.append(location, name);
					if (isDir) {
						location = URIUtil.append(location, "");
					}
				}
				jsonObject.put(ProtocolConstants.KEY_LOCATION, location);
				if (isDir) {
					try {
						jsonObject.put(ProtocolConstants.KEY_CHILDREN_LOCATION, new URI(location.getScheme(), location.getAuthority(), location.getPath(), "depth=1", location.getFragment())); //$NON-NLS-1$
					} catch (URISyntaxException e) {
						throw new RuntimeException(e);
					}
				}
			}
			JSONObject attributes = new JSONObject();
			attributes.put("ReadOnly", true);
			jsonObject.put(ProtocolConstants.KEY_ATTRIBUTES, attributes);

		} catch (JSONException e) {
			//cannot happen because the key is non-null and the values are strings
			throw new RuntimeException(e);
		}
		return jsonObject;
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
		try {
			if (gitSegment == null) {
				throw new Exception("Missing ref in git segment");
			} else {
				ObjectId head = repo.resolve(gitSegment);
				if (head == null) {
					throw new Exception("Missing ref in git segment");
				}
				walk = new RevWalk(repo);
				// add try catch to catch failures
				Git git = new Git(repo);
				git.getRepository().getDirectory();

				RevCommit commit = walk.parseCommit(head);
				RevTree tree = commit.getTree();
				treeWalk = new TreeWalk(repo);
				treeWalk.addTree(tree);
				treeWalk.setRecursive(false);
				if (!pattern.equals("")) {
					PathFilter pathFilter = PathFilter.create(pattern);
					treeWalk.setFilter(pathFilter);
				}
				JSONArray contents = new JSONArray();
				JSONObject result = null;
				ArrayList<JSONObject> parents = new ArrayList<JSONObject>();
				parents.add(listEntry(filePath.lastSegment(), 0, true, 0, ServletResourceHandler.getURI(request), false));
				while (treeWalk.next()) {
					if (treeWalk.isSubtree()) {
						if (treeWalk.getPathLength() > pattern.length()) {
							contents.put(listEntry(treeWalk.getNameString(), 0, true, 0, ServletResourceHandler.getURI(request), true));
						}
						if (treeWalk.getPathLength() <= pattern.length()) {
							parents.add(0, listEntry(treeWalk.getNameString(), 0, true, 0, ServletResourceHandler.getURI(request), false));
							treeWalk.enterSubtree();
						}
					} else {
						ObjectId objId = treeWalk.getObjectId(0);
						ObjectLoader loader = repo.open(objId);
						long size = loader.getSize();
						if (treeWalk.getPathLength() == pattern.length()) {
							if ("meta".equals(meta)) {
								result = listEntry(treeWalk.getNameString(), 0, false, 0, ServletResourceHandler.getURI(request), false);
							} else {
								return getFileContents(request, response, gitSegment, pattern, repo);
							}
						} else {
							contents.put(listEntry(treeWalk.getNameString(), 0, false, size, ServletResourceHandler.getURI(request), true));
						}
					}
				}
				if (result == null) {
					result = parents.remove(0);
					result.put("Children", contents);
					result.put("Parents", new JSONArray(parents));
				}
				response.setContentType("application/json");
				response.setHeader("Cache-Control", "no-cache");
				response.setHeader("ETag", "\"" + tree.getId().getName() + "\"");
				OrionServlet.writeJSONResponse(request, response, result);
			}
			return true;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when requesting commit info.", e));
		} finally {
			if (walk != null)
				walk.release();
			if (treeWalk != null)
				treeWalk.release();
		}
	}

	private boolean getFileContents(HttpServletRequest request, HttpServletResponse response, String gitSegment, String pattern, Repository repo) {
		ObjectStream stream = null;
		TreeWalk treeWalk = null;
		RevWalk walk = null;
		try {
			walk = new RevWalk(repo);
			// add try catch to catch failures
			Git git = new Git(repo);
			Ref head = repo.getRef(gitSegment);
			git.getRepository().getDirectory();
			RevCommit commit = walk.parseCommit(head.getObjectId());
			RevTree tree = commit.getTree();

			treeWalk = new TreeWalk(repo);
			treeWalk.addTree(tree);
			treeWalk.setRecursive(true);
			treeWalk.setFilter(PathFilter.create(pattern));
			if (!treeWalk.next()) {
				throw new IllegalStateException("No file found");
			}

			ObjectId objId = treeWalk.getObjectId(0);
			ObjectLoader loader = repo.open(objId);
			response.setHeader("Cache-Control", "no-cache");
			response.setHeader("ETag", "\"" + tree.getId().getName() + "\"");
			response.setContentType("application/octet-stream");
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
			walk.release();
			treeWalk.release();
		}
		return true;
	}
}
