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
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String gitPathInfo) throws ServletException {
		try {
			Path path = new Path(gitPathInfo);
			if (!path.hasTrailingSeparator())
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Cannot get status on a file.", null));
			File gitDir = GitUtils.getGitDir(path.uptoSegment(2), request.getRemoteUser());
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?
			Repository db = new FileRepository(gitDir);
			FileTreeIterator iterator = new FileTreeIterator(db);
			IndexDiff diff = new IndexDiff(db, Constants.HEAD, iterator);
			// see bug 339351
			// if (path.segmentCount() > 2)
			// diff.setFilter(PathFilter.create(path.removeFirstSegments(2).toString()));
			diff.diff();

			URI baseLocation = getURI(request);
			baseLocation = stripOffPath(baseLocation);
			IPath basePath = path.removeFirstSegments(2);
			JSONObject result = new JSONObject();
			JSONArray children = toJSONArray(diff.getAdded(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_ADDED, children);
			// TODO: bug 338913
			// children = toJSONArray(diff.getAssumeUnchanged(), baseLocation, ?);
			// result.put(GitConstants.KEY_STATUS_ASSUME_UNCHANGED, children);
			children = toJSONArray(diff.getChanged(), basePath, baseLocation, GitConstants.KEY_DIFF_CACHED);
			result.put(GitConstants.KEY_STATUS_CHANGED, children);
			children = toJSONArray(diff.getMissing(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_MISSING, children);
			children = toJSONArray(diff.getModified(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_MODIFIED, children);
			children = toJSONArray(diff.getRemoved(), basePath, baseLocation, GitConstants.KEY_DIFF_CACHED);
			result.put(GitConstants.KEY_STATUS_REMOVED, children);
			children = toJSONArray(diff.getUntracked(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_UNTRACKED, children);

			OrionServlet.writeJSONResponse(request, response, result);
			return true;

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating status response", e));
		}
	}

	private URI stripOffPath(URI u) throws URISyntaxException {
		Path uriPath = new Path(u.getPath());
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath.uptoSegment(4).toString(), u.getQuery(), u.getFragment());

	}

	private JSONArray toJSONArray(Set<String> set, IPath basePath, URI baseLocation, String diffType) throws JSONException, URISyntaxException {
		JSONArray result = new JSONArray();
		for (String s : set) {
			JSONObject object = new JSONObject();

			object.put(ProtocolConstants.KEY_NAME, s);
			IPath relative = new Path(s).makeRelativeTo(basePath);
			object.put(GitConstants.KEY_PATH, relative);
			URI fileLocation = statusToFileLocation(baseLocation);
			object.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(fileLocation, s));

			JSONObject gitSection = new JSONObject();
			URI diffLocation = statusToDiffLocation(baseLocation, diffType);
			gitSection.put(GitConstants.KEY_DIFF, URIUtil.append(diffLocation, s));
			object.put(GitConstants.KEY_GIT, gitSection);

			URI commitLocation = statusToCommitLocation(baseLocation, Constants.HEAD);
			gitSection.put(GitConstants.KEY_COMMIT, URIUtil.append(commitLocation, s));
			object.put(GitConstants.KEY_GIT, gitSection);

			URI indexLocation = statusToIndexLocation(baseLocation);
			gitSection.put(GitConstants.KEY_INDEX, URIUtil.append(indexLocation, s));
			object.put(GitConstants.KEY_GIT, gitSection);

			result.put(object);
		}
		return result;
	}

	private URI statusToFileLocation(URI u) throws URISyntaxException {
		String uriPath = u.getPath();
		uriPath = uriPath.substring((GitServlet.GIT_URI + "/" + GitConstants.STATUS_RESOURCE) //$NON-NLS-1$
				.length());
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}

	private URI statusToDiffLocation(URI u, String diffType) throws URISyntaxException {
		String uriPath = u.getPath();
		uriPath = uriPath.substring((GitServlet.GIT_URI + "/" + GitConstants.STATUS_RESOURCE) //$NON-NLS-1$
				.length());
		uriPath = GitServlet.GIT_URI + "/" + GitConstants.DIFF_RESOURCE + "/" + diffType + uriPath; //$NON-NLS-1$ //$NON-NLS-2$
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}

	private URI statusToCommitLocation(URI u, String ref) throws URISyntaxException {
		String uriPath = u.getPath();
		uriPath = uriPath.substring((GitServlet.GIT_URI + "/" + GitConstants.STATUS_RESOURCE) //$NON-NLS-1$
				.length());
		uriPath = GitServlet.GIT_URI + "/" + GitConstants.COMMIT_RESOURCE + "/" + ref + uriPath; //$NON-NLS-1$ //$NON-NLS-2$
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}

	private URI statusToIndexLocation(URI u) throws URISyntaxException {
		String uriPath = u.getPath();
		uriPath = uriPath.substring((GitServlet.GIT_URI + "/" + GitConstants.STATUS_RESOURCE) //$NON-NLS-1$
				.length());
		uriPath = GitServlet.GIT_URI + "/" + GitConstants.INDEX_RESOURCE + uriPath; //$NON-NLS-1$
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}
}
