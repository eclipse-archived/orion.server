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

import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

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
	private Repository db;
	private ObjectId blobId;

	GitIndexHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {

		try {
			Path p = new Path(path);
			File gitDir = GitUtils.getGitDir(p.uptoSegment(2), request.getRemoteUser());
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?
			db = new FileRepository(gitDir);

			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, p);
				case PUT :
					return handlePut(request, response, p);
				case POST :
					return handlePost(request, response, p);
					// case DELETE :
					// return handleDelete(request, response, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to process an operation on index for {0}", path); //$NON-NLS-1$
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, Path path) throws CoreException, IOException {
		DirCache cache = db.readDirCache();
		DirCacheEntry entry = cache.getEntry(path.removeFirstSegments(2).toString());
		blobId = entry.getObjectId();
		IOUtilities.pipe(open(), response.getOutputStream(), true, false);
		return true;
	}

	private InputStream open() throws IOException, CoreException, IncorrectObjectTypeException {
		return db.open(blobId, Constants.OBJ_BLOB).openStream();
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, Path path) throws ServletException, NoFilepatternException {
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

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, Path path) throws ServletException, NoFilepatternException, IOException, JSONException {
		JSONObject toReset = OrionServlet.readJSONRequest(request);
		String resetType = toReset.optString(GitConstants.KEY_RESET_TYPE, null);
		if (resetType == null) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Reset type must be specified.", null));
		}

		try {
			ResetType rt = ResetType.valueOf(resetType);
			switch (rt) {
				case MIXED :
					Git git = new Git(db);
					// "git reset --{type} HEAD ."
					git.reset().setMode(rt).setRef(Constants.HEAD).call();
					return true;
				case HARD :
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
		return false;
	}
}
