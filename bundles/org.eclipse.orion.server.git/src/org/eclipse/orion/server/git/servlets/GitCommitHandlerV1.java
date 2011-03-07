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
import java.util.Collections;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 
 */
public class GitCommitHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;
	private Repository db;
	private ObjectId blobId;

	GitCommitHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, String path) throws ServletException {

		try {
			Path p = new Path(path);

			switch (getMethod(request)) {
			case GET:
				return handleGet(request, response, p);
				// case PUT:
				// return handlePut(request, response, p);
			case POST:
				return handlePost(request, response, p);
				// case DELETE :
				// return handleDelete(request, response, p);
			}

		} catch (Exception e) {
			String msg = NLS.bind(
					"Failed to process an operation on commits for {0}", path); //$NON-NLS-1$
			statusHandler.handleRequest(request, response, new ServerStatus(
					IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, Path path) throws CoreException,
			IOException, ServletException {

		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(1)
				.uptoSegment(2), request.getRemoteUser());
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?
		db = new FileRepository(gitDir);

		// /{ref}/file/{projectId}/{path/...}
		if (path.segmentCount() > 3) {
			// TODO: try {} catch () { BAD_REQUEST, NOT_FOUND }
			ObjectId refId = db.resolve(path.segment(0));
			RevWalk walk = new RevWalk(db);
			String p = path.removeFirstSegments(3).toString();
			walk.setTreeFilter(AndTreeFilter.create(
					PathFilterGroup.createFromStrings(Collections.singleton(p)),
					TreeFilter.ANY_DIFF));
			RevCommit commit = walk.parseCommit(refId);
			final TreeWalk w = TreeWalk.forPath(db, p, commit.getTree());
			if (w == null) {
				// TODO:
			}
			blobId = w.getObjectId(0);
			IOUtilities.pipe(open(), response.getOutputStream(), true, false);
			return true;
		}
		return statusHandler.handleRequest(request, response, new ServerStatus(
				IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
				"The commit log is not yet supported.", null));
	}

	private InputStream open() throws IOException, CoreException,
			IncorrectObjectTypeException {
		return db.open(blobId, Constants.OBJ_BLOB).openStream();
	}

	private boolean handlePost(HttpServletRequest request,
			HttpServletResponse response, Path path) throws ServletException,
			NoFilepatternException, IOException, JSONException, CoreException {

		File gitDir = GitUtils.getGitDir(path.uptoSegment(2),
				request.getRemoteUser());
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?
		db = new FileRepository(gitDir);

		if (path.segmentCount() > 2) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_NOT_IMPLEMENTED,
							"Committing by path is not yet supported.", null));
		}

		JSONObject toReset = OrionServlet.readJSONRequest(request);
		String message = toReset.optString(GitConstants.KEY_COMMIT_MESSAGE,
				null);
		if (message == null) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"Missing commit message.", null));
		}

		boolean amend = Boolean.parseBoolean(toReset.optString(
				GitConstants.KEY_COMMIT_AMEND, null));

		Git git = new Git(db);
		// "git commit [--amend] -m '{message}' [-a|{path}]"
		try {
			git.commit().setAmend(amend).setMessage(message).call();
			return true;
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"An error occured when commiting.", e));
		} catch (JGitInternalException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							"An internal error occured when commiting.", e));
		}
	}
}
