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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.*;

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
				return false; // TODO: or an error response code, 405?
			Repository db = new FileRepository(gitDir);
			FileTreeIterator iterator = new FileTreeIterator(db);
			IndexDiff diff = new IndexDiff(db, Constants.HEAD, iterator);
			if (path.segmentCount() > 2)
				diff.setFilter(PathFilter.create(path.removeFirstSegments(2)
						.toString()));
			diff.diff();

			URI baseLocation = getURI(request);
			baseLocation = stripOffPath(baseLocation);
			JSONObject result = new JSONObject();
			JSONArray children = toJSONArray(diff.getAdded(), baseLocation,
					GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_ADDED, children);
			// TODO:
			// children = toJSONArray(diff.getAssumeUnchanged());
			// result.put(ProtocolConstants.KEY_CHILDREN, children);
			children = toJSONArray(diff.getChanged(), baseLocation,
					GitConstants.KEY_DIFF_CACHED);
			result.put(GitConstants.KEY_STATUS_CHANGED, children);
			children = toJSONArray(diff.getMissing(), baseLocation,
					GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_MISSING, children);
			children = toJSONArray(diff.getModified(), baseLocation,
					GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_MODIFIED, children);
			children = toJSONArray(diff.getRemoved(), baseLocation,
					GitConstants.KEY_DIFF_CACHED);
			result.put(GitConstants.KEY_STATUS_REMOVED, children);
			children = toJSONArray(diff.getUntracked(), baseLocation,
					GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_UNTRACKED, children);

			OrionServlet.writeJSONResponse(request, response, result);
			return true;

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							"Error generating status response", e));
		}
	}

	private URI stripOffPath(URI u) throws URISyntaxException {
		Path uriPath = new Path(u.getPath());
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(),
				u.getPort(), uriPath.uptoSegment(4).toString(), u.getQuery(),
				u.getFragment());

	}

	private JSONArray toJSONArray(Set<String> set, URI baseLocation,
			String diffType) throws JSONException, URISyntaxException {
		JSONArray result = new JSONArray();
		for (String s : set) {
			JSONObject object = new JSONObject();
			object.put(ProtocolConstants.KEY_NAME, s);
			URI fileLocation = statusToFileLocation(baseLocation);
			object.put(ProtocolConstants.KEY_LOCATION,
					URIUtil.append(fileLocation, s));

			JSONObject gitSection = new JSONObject();
			URI diffLocation = statusToDiffLocation(baseLocation, diffType);
			gitSection.put(GitConstants.KEY_DIFF,
					URIUtil.append(diffLocation, s));
			object.put(GitConstants.KEY_GIT, gitSection);
			result.put(object);
		}
		return result;
	}

	private URI statusToFileLocation(URI u) throws URISyntaxException {
		String uriPath = u.getPath();
		uriPath = uriPath
				.substring((GitServlet.GIT_URI + "/" + GitConstants.STATUS_RESOURCE) //$NON-NLS-1$
						.length());
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(),
				u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}

	private URI statusToDiffLocation(URI u, String diffType)
			throws URISyntaxException {
		String uriPath = u.getPath();
		uriPath = uriPath
				.substring((GitServlet.GIT_URI + "/" + GitConstants.STATUS_RESOURCE) //$NON-NLS-1$
						.length());
		uriPath = GitServlet.GIT_URI
				+ "/" + GitConstants.DIFF_RESOURCE + "/" + diffType + uriPath; //$NON-NLS-1$ //$NON-NLS-2$
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(),
				u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}
}
