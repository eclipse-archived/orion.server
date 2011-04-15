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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * 
 */
public class GitCommitHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

	GitCommitHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {

		Repository db = null;
		try {
			Path p = new Path(path);

			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, db, p);
					// case PUT:
					// return handlePut(request, response, p);
				case POST :
					return handlePost(request, response, db, p);
					// case DELETE :
					// return handleDelete(request, response, p);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to process an operation on commits for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws CoreException, IOException, ServletException, JSONException, URISyntaxException {
		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(1).uptoSegment(2));
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?

		db = new FileRepository(gitDir);

		// /{ref}/file/{projectId}...}
		String parts = request.getParameter("parts"); //$NON-NLS-1$
		if (path.segmentCount() > 3 && "body".equals(parts)) { //$NON-NLS-1$
			return handleGetCommitBody(request, response, db, path);
		}
		if (path.segmentCount() > 2 && (parts == null || "log".equals(parts))) { //$NON-NLS-1$
			return handleGetCommitLog(request, response, db, path);
		}

		return false;
	}

	private boolean handleGetCommitBody(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws AmbiguousObjectException, IOException, ServletException {
		ObjectId refId = db.resolve(path.segment(0));
		if (refId == null) {
			String msg = NLS.bind("Failed to generate commit log for ref {0}", path.segment(0)); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}

		RevWalk walk = new RevWalk(db);
		String p = path.removeFirstSegments(3).toString();
		walk.setTreeFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(Collections.singleton(p)), TreeFilter.ANY_DIFF));
		RevCommit commit = walk.parseCommit(refId);
		final TreeWalk w = TreeWalk.forPath(db, p, commit.getTree());
		if (w == null) {
			// TODO:
		}

		ObjectId blobId = w.getObjectId(0);
		ObjectStream stream = db.open(blobId, Constants.OBJ_BLOB).openStream();
		IOUtilities.pipe(stream, response.getOutputStream(), true, false);

		return true;
	}

	private boolean handleGetCommitLog(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws AmbiguousObjectException, IOException, ServletException, JSONException, URISyntaxException {
		String refIdsRange = path.segment(0);

		ObjectId toRefId = null;
		ObjectId fromRefId = null;

		if (refIdsRange.contains("..")) { //$NON-NLS-1$
			String[] commits = refIdsRange.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				String msg = NLS.bind("Failed to generate commit log for ref {0}", refIdsRange); //$NON-NLS-1$
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}

			fromRefId = db.resolve(commits[0]);
			if (fromRefId == null) {
				String msg = NLS.bind("Failed to generate commit log for ref {0}", commits[0]); //$NON-NLS-1$
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}

			toRefId = db.resolve(commits[1]);
			if (toRefId == null) {
				String msg = NLS.bind("Failed to generate commit log for ref {0}", commits[1]); //$NON-NLS-1$
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
		} else {
			toRefId = db.resolve(refIdsRange);
			if (toRefId == null) {
				String msg = NLS.bind("Failed to generate commit log for ref {0}", refIdsRange); //$NON-NLS-1$
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
		}

		RevWalk walk = new RevWalk(db);

		// set the commit range
		walk.markStart(walk.lookupCommit(toRefId));
		if (fromRefId != null)
			walk.markUninteresting(walk.parseCommit(fromRefId));

		// set the path filter
		String p = path.removeFirstSegments(3).toString();
		if (p != null && !"".equals(p))
			walk.setTreeFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(Collections.singleton(p)), TreeFilter.ANY_DIFF));

		OrionServlet.writeJSONResponse(request, response, toJSON(OrionServlet.getURI(request), walk));

		return true;
	}

	private JSONArray toJSON(URI baseLocation, Iterable<RevCommit> commits) throws JSONException, URISyntaxException {
		JSONArray result = new JSONArray();
		for (RevCommit revCommit : commits) {
			JSONObject commit = new JSONObject();
			commit.put(ProtocolConstants.KEY_LOCATION, createCommitLocation(baseLocation, revCommit.getName(), null));
			commit.put(ProtocolConstants.KEY_CONTENT_LOCATION, createCommitLocation(baseLocation, revCommit.getName(), "parts=body"));
			commit.put(GitConstants.KEY_DIFF, createDiffLocation(baseLocation, revCommit.getName()));
			commit.put(ProtocolConstants.KEY_NAME, revCommit.getName());
			commit.put(GitConstants.KEY_AUTHOR_NAME, revCommit.getAuthorIdent().getName());
			commit.put(GitConstants.KEY_COMMIT_TIME, ((long) revCommit.getCommitTime()) * 1000 /* time in milliseconds */);
			commit.put(GitConstants.KEY_COMMIT_MESSAGE, revCommit.getFullMessage());
			result.put(commit);
		}
		return result;
	}

	private URI createCommitLocation(URI baseLocation, String commitName, String parameters) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), GitServlet.GIT_URI + "/" + GitConstants.COMMIT_RESOURCE + "/" + commitName + "/" + new Path(baseLocation.getPath()).removeFirstSegments(3), parameters, null);
	}

	private URI createDiffLocation(URI baseLocation, String commitName) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), GitServlet.GIT_URI + "/" + GitConstants.DIFF_RESOURCE + "/" + commitName + "/" + new Path(baseLocation.getPath()).removeFirstSegments(3), null, null);
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws ServletException, NoFilepatternException, IOException, JSONException, CoreException {
		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(1).uptoSegment(2));
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?
		db = new FileRepository(gitDir);

		JSONObject requestObject = OrionServlet.readJSONRequest(request);
		String commitToMerge = requestObject.optString(GitConstants.KEY_MERGE, null);
		if (commitToMerge != null) {
			return merge(request, response, db, commitToMerge);
		}
		// continue with commit

		if (path.segmentCount() > 3) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_IMPLEMENTED, "Committing by path is not yet supported.", null));
		}

		ObjectId refId = db.resolve(path.segment(0));
		if (refId == null || !Constants.HEAD.equals(path.segment(0))) {
			String msg = NLS.bind("Commit failed. Ref must be HEAD and is {0}", path.segment(0)); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}

		String message = requestObject.optString(GitConstants.KEY_COMMIT_MESSAGE, null);
		if (message == null || message.isEmpty()) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Missing commit message.", null));
		}

		boolean amend = Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_COMMIT_AMEND, null));

		Git git = new Git(db);
		// "git commit [--amend] -m '{message}' [-a|{path}]"
		try {
			git.commit().setAmend(amend).setMessage(message).call();
			return true;
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "An error occured when commiting.", e));
		} catch (JGitInternalException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occured when commiting.", e));
		}
	}

	private boolean merge(HttpServletRequest request, HttpServletResponse response, Repository db, String commitToMerge) throws ServletException {
		try {
			ObjectId objectId = db.resolve(commitToMerge);
			Git git = new Git(db);
			git.merge().include(objectId).call();
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when merging.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when merging.", e));
		}
	}
}
