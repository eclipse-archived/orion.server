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
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
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
			if (!path.hasTrailingSeparator()) {
				String msg = NLS.bind("Cannot get status on a file: {0}", gitPathInfo);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			Set<Entry<IPath, File>> set = GitUtils.getGitDirs(path, Traverse.GO_UP).entrySet();
			File gitDir = set.iterator().next().getValue();
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?
			Repository db = new FileRepository(gitDir);
			Git git = new Git(db);
			Status status = git.status().call();

			URI baseLocation = getURI(request);
			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_CLONE, BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.STATUS));

			String relativePath = GitUtils.getRelativePath(path, set.iterator().next().getKey());
			IPath basePath = new Path(relativePath);

			JSONArray children = toJSONArray(status.getAdded(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_ADDED, children);
			// TODO: bug 338913
			// children = toJSONArray(diff.getAssumeUnchanged(), baseLocation, ?);
			// result.put(GitConstants.KEY_STATUS_ASSUME_UNCHANGED, children);
			children = toJSONArray(status.getChanged(), basePath, baseLocation, GitConstants.KEY_DIFF_CACHED);
			result.put(GitConstants.KEY_STATUS_CHANGED, children);
			children = toJSONArray(status.getMissing(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_MISSING, children);
			children = toJSONArray(status.getModified(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_MODIFIED, children);
			children = toJSONArray(status.getRemoved(), basePath, baseLocation, GitConstants.KEY_DIFF_CACHED);
			result.put(GitConstants.KEY_STATUS_REMOVED, children);
			children = toJSONArray(status.getUntracked(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_UNTRACKED, children);
			children = toJSONArray(status.getConflicting(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
			result.put(GitConstants.KEY_STATUS_CONFLICTING, children);

			result.put(GitConstants.KEY_INDEX, statusToIndexLocation(baseLocation));
			result.put(GitConstants.KEY_COMMIT, statusToCommitLocation(baseLocation, Constants.HEAD));

			OrionServlet.writeJSONResponse(request, response, result);
			return true;

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating status response", e));
		}
	}

	private JSONArray toJSONArray(Set<String> set, IPath basePath, URI baseLocation, String diffType) throws JSONException, URISyntaxException {
		JSONArray result = new JSONArray();
		for (String s : set) {
			JSONObject object = new JSONObject();

			object.put(ProtocolConstants.KEY_NAME, s);
			IPath relative = new Path(s).makeRelativeTo(basePath);
			object.put(ProtocolConstants.KEY_PATH, relative);
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
