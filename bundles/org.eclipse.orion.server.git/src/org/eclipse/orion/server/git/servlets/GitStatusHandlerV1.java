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
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A handler for Git Status operation.
 */
public class GitStatusHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

	GitStatusHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, String gitPathInfo)
			throws ServletException {
		try {
			Path path = new Path(gitPathInfo);
			File gitDir = GitUtils.getGitDir(path.uptoSegment(2),
					request.getRemoteUser());
			if (gitDir == null)
				return false; // TODO: or a error response code, 405?
			Repository db = new FileRepository(gitDir);
			FileTreeIterator iterator = new FileTreeIterator(db);
			IndexDiff diff = new IndexDiff(db, Constants.HEAD, iterator);
			diff.diff();

			JSONObject result = new JSONObject();

			JSONArray children = toJSONArray(diff.getAdded());
			result.put(GitConstants.KEY_STATUS_ADDED, children);
//			children = toJSONArray(diff.getAssumeUnchanged());
//			result.put(ProtocolConstants.KEY_CHILDREN, children);
//			children = toJSONArray(diff.getChanged());
//			result.put(ProtocolConstants.KEY_CHILDREN, children);
			children = toJSONArray(diff.getMissing());
			result.put(GitConstants.KEY_STATUS_MISSING, children);
			children = toJSONArray(diff.getModified());
			result.put(GitConstants.KEY_STATUS_MODIFIED, children);
			children = toJSONArray(diff.getAdded());
//			children = toJSONArray(diff.getRemoved());
//			result.put(GitConstants.KEY_STATUS_REMOVED, children);
			children = toJSONArray(diff.getUntracked());
			result.put(GitConstants.KEY_STATUS_UNTRACKED, children);

			OrionServlet.writeJSONResponse(request, response, result);
			return true;

		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							"Error generating status response", e));
		} catch (Exception e) {
			throw new ServletException("Error creating Git status", e);
		}
	}

	private JSONArray toJSONArray(Set<String> set) throws JSONException {
		JSONArray result = new JSONArray();
		for (String s : set) {
			JSONObject object = new JSONObject();
			object.put(ProtocolConstants.KEY_NAME, s);
			result.put(object);
		}
		return result;
	}
}
