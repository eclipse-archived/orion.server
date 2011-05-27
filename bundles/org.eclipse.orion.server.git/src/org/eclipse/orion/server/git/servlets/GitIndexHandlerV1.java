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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
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
			Path p = new Path(path);
			File gitDir = GitUtils.getGitDir(p.uptoSegment(2));
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?
			db = new FileRepository(gitDir);

			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, db, p);
				case PUT :
					return handlePut(request, response, db, p);
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

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws CoreException, IOException, ServletException {
		DirCache cache = db.readDirCache();
		DirCacheEntry entry = cache.getEntry(path.removeFirstSegments(2).toString());
		if (entry == null) {
			String msg = NLS.bind("{0} not found in index", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.OK, HttpServletResponse.SC_NOT_FOUND, msg, null));
		}
		ObjectId blobId = entry.getObjectId();
		ObjectStream stream = db.open(blobId, Constants.OBJ_BLOB).openStream();
		IOUtilities.pipe(stream, response.getOutputStream(), true, false);
		return true;
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws ServletException, NoFilepatternException {
		String pattern;
		if (path.segmentCount() > 2) {
			// PUT file/{project}/{path}
			pattern = path.removeFirstSegments(2).toString();
		} else {
			// PUT file/{project}/
			pattern = ADD_ALL_PATTERN;
		}
		Git git = new Git(db);
		AddCommand add = git.add().addFilepattern(pattern);
		// "git add {pattern}"
		add.call();

		// TODO: we're calling "add" twice, this is inefficient
		// "git add -u {pattern}"
		git.add().setUpdate(true).addFilepattern(pattern).call();

		return true;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws ServletException, NoFilepatternException, IOException, JSONException {
		JSONObject toReset = OrionServlet.readJSONRequest(request);
		String resetType = toReset.optString(GitConstants.KEY_RESET_TYPE, null);
		if (resetType != null) {
			JSONArray paths = toReset.optJSONArray(GitConstants.KEY_PATH);
			if (paths != null) {
				String msg = NLS.bind("Mixing {0} and {1} parameters is not allowed.", new Object[] {GitConstants.KEY_PATH, GitConstants.KEY_RESET_TYPE});
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_IMPLEMENTED, msg, null));
			}
			try {
				ResetType type = ResetType.valueOf(resetType);
				switch (type) {
					case MIXED :
					case HARD :
						Git git = new Git(db);
						// "git reset --{type} HEAD ."
						git.reset().setMode(type).setRef(Constants.HEAD).call();
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
			JSONArray paths = toReset.optJSONArray(GitConstants.KEY_PATH);
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
