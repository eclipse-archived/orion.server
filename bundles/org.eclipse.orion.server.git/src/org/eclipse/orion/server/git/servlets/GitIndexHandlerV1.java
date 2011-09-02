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
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Index;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * A handler for Git operations on index:
 * <ul>
 * <li>get file content stored in index
 * <li>add file(s) to index
 * </ul>
 */
public class GitIndexHandlerV1 extends ServletResourceHandler<String> {

	private static final String ADD_ALL_PATTERN = "."; //$NON-NLS-1$
	private ServletResourceHandler<IStatus> statusHandler;

	GitIndexHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		Repository db = null;
		try {
			IPath p = new Path(path);
			IPath filePath = p.hasTrailingSeparator() ? p : p.removeLastSegments(1);
			Set<Entry<IPath, File>> set = GitUtils.getGitDirs(filePath, Traverse.GO_UP).entrySet();
			File gitDir = set.iterator().next().getValue();
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?
			db = new FileRepository(gitDir);
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, db, GitUtils.getRelativePath(p, set.iterator().next().getKey()));
				case PUT :
					return handlePut(request, response, db, GitUtils.getRelativePath(p, set.iterator().next().getKey()));
				case POST :
					return handlePost(request, response, db, p);
			}
		} catch (Exception e) {
			String msg = NLS.bind("Failed to process an operation on index for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, Repository db, String pattern) throws CoreException, IOException, ServletException {
		Index index = new Index(null /* not needed */, db, pattern);
		ObjectStream stream = index.toObjectStream();
		if (stream == null) {
			String msg = NLS.bind("{0} not found in index", pattern); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.OK, HttpServletResponse.SC_NOT_FOUND, msg, null));
		}
		IOUtilities.pipe(stream, response.getOutputStream(), true, false);
		return true;
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, Repository db, String pattern) throws ServletException, NoFilepatternException, IOException, JSONException {
		JSONObject toAdd = OrionServlet.readJSONRequest(request);
		JSONArray paths = toAdd.optJSONArray(ProtocolConstants.KEY_PATH);
		if (paths == null) {
			paths = new JSONArray().put(pattern.isEmpty() ? ADD_ALL_PATTERN : pattern);
		}

		Git git = new Git(db);
		AddCommand add = git.add();
		for (int i = 0; i < paths.length(); i++) {
			add.addFilepattern(paths.getString(i));
		}
		// "git add {pattern}"
		add.call();

		// TODO: we're calling "add" twice, this is inefficient, see bug 349299
		// "git add -u {pattern}"
		add = git.add().setUpdate(true);
		for (int i = 0; i < paths.length(); i++) {
			add.addFilepattern(paths.getString(i));
		}
		add.call();
		return true;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, Repository db, IPath path) throws ServletException, NoFilepatternException, IOException, JSONException {
		JSONObject toReset = OrionServlet.readJSONRequest(request);
		String resetType = toReset.optString(GitConstants.KEY_RESET_TYPE, null);
		if (resetType != null) {
			JSONArray paths = toReset.optJSONArray(ProtocolConstants.KEY_PATH);
			if (paths != null) {
				String msg = NLS.bind("Mixing {0} and {1} parameters is not allowed.", new Object[] {ProtocolConstants.KEY_PATH, GitConstants.KEY_RESET_TYPE});
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			String ref = toReset.optString(GitConstants.KEY_TAG_COMMIT, Constants.HEAD);
			try {
				ResetType type = ResetType.valueOf(resetType);
				switch (type) {
					case MIXED :
					case HARD :
						Git git = new Git(db);
						// "git reset --{type} HEAD ."
						git.reset().setMode(type).setRef(ref).call();
						return true;
					case KEEP :
					case MERGE :
					case SOFT :
						String msg = NLS.bind("The reset type is not yet supported: {0}.", resetType);
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_IMPLEMENTED, msg, null));
				}
			} catch (IllegalArgumentException e) {
				String msg = NLS.bind("Unknown or malformed reset type: {0}.", resetType);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
		} else {
			String commit = toReset.optString(GitConstants.KEY_TAG_COMMIT, null);
			if (commit != null) {
				String msg = NLS.bind("Mixing {0} and {1} parameters is not allowed.", new Object[] {ProtocolConstants.KEY_PATH, GitConstants.KEY_TAG_COMMIT});
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			JSONArray paths = toReset.optJSONArray(ProtocolConstants.KEY_PATH);
			Git git = new Git(db);
			ResetCommand reset = git.reset().setRef(Constants.HEAD);
			if (paths != null) {
				for (int i = 0; i < paths.length(); i++) {
					reset.addPath(paths.getString(i));
				}
			} else {
				String p = path.removeFirstSegments(2).toString();
				if (p.isEmpty()) {
					String msg = "Path cannot be empty.";
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				}
				reset.addPath(p);
			}
			reset.call();
			return true;
		}
		return false;
	}
}
