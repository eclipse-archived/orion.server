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
import java.util.*;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
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

	private final static int PAGE_SIZE = 50;

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
				case PUT :
					return handlePut(request, response, db, p);
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

	private boolean createCommitLocation(HttpServletRequest request, HttpServletResponse response, Repository db, String newCommitToCreatelocation) throws IOException, JSONException, URISyntaxException {
		URI u = getURI(request);
		IPath p = new Path(u.getPath());
		IPath np = new Path("/"); //$NON-NLS-1$
		for (int i = 0; i < p.segmentCount(); i++) {
			String s = p.segment(i);
			if (i == 2) {
				s += ".." + newCommitToCreatelocation; //$NON-NLS-1$
			}
			np = np.append(s);
		}
		URI nu = new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), np.toString(), u.getQuery(), u.getFragment());
		response.setHeader(ProtocolConstants.HEADER_LOCATION, nu.toString());
		response.setStatus(HttpServletResponse.SC_OK);
		return true;
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

		int page = request.getParameter("page") != null ? new Integer(request.getParameter("page")).intValue() : 0;

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
		if (p != null && !"".equals(p)) //$NON-NLS-1$
			walk.setTreeFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(Collections.singleton(p)), TreeFilter.ANY_DIFF));

		JSONObject result = toJSON(db, OrionServlet.getURI(request), walk, page);
		result.put(GitConstants.KEY_REMOTE, getRemoteBranchLocation(getURI(request), db));
		OrionServlet.writeJSONResponse(request, response, result);
		walk.dispose();
		return true;
	}

	private URI getRemoteBranchLocation(URI base, Repository db) throws IOException, URISyntaxException {
		for (Entry<String, Ref> refEntry : db.getRefDatabase().getRefs(Constants.R_REMOTES).entrySet()) {
			if (!refEntry.getValue().isSymbolic()) {
				Ref ref = refEntry.getValue();
				String name = ref.getName();
				name = Repository.shortenRefName(name).substring(Constants.DEFAULT_REMOTE_NAME.length() + 1);
				if (db.getBranch().equals(name)) {
					return baseToRemoteLocation(base, Constants.DEFAULT_REMOTE_NAME, name);
				}
			}
		}
		return null;
	}

	private URI baseToRemoteLocation(URI u, String remote, String branch) throws URISyntaxException {
		// URIUtil.append(baseLocation, configName)
		IPath p = new Path(u.getPath());
		p = p.uptoSegment(1).append(GitConstants.REMOTE_RESOURCE).append(remote).append(branch).addTrailingSeparator().append(p.removeFirstSegments(3));
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), p.toString(), u.getQuery(), u.getFragment());
	}

	private JSONObject toJSON(Repository db, URI baseLocation, Iterable<RevCommit> commits, int page) throws JSONException, URISyntaxException, MissingObjectException, IOException {
		boolean pageable = (page > 0);
		int startIndex = (page - 1) * PAGE_SIZE;
		int index = 0;

		JSONObject result = new JSONObject();
		JSONArray children = new JSONArray();
		for (RevCommit revCommit : commits) {
			if (pageable && index < startIndex) {
				index++;
				continue;
			}

			if (pageable && index >= startIndex + PAGE_SIZE)
				break;

			index++;

			children.put(toJSON(db, revCommit, baseLocation));
		}
		result.put(ProtocolConstants.KEY_CHILDREN, children);
		return result;
	}

	private JSONObject toJSON(Repository db, RevCommit revCommit, URI baseLocation) throws JSONException, URISyntaxException, IOException {
		JSONObject commit = new JSONObject();
		commit.put(ProtocolConstants.KEY_LOCATION, createCommitLocation(baseLocation, revCommit.getName(), null));
		commit.put(ProtocolConstants.KEY_CONTENT_LOCATION, createCommitLocation(baseLocation, revCommit.getName(), "parts=body"));
		commit.put(GitConstants.KEY_DIFF, createDiffLocation(baseLocation, revCommit.getName()));
		commit.put(ProtocolConstants.KEY_NAME, revCommit.getName());
		commit.put(GitConstants.KEY_AUTHOR_NAME, revCommit.getAuthorIdent().getName());
		commit.put(GitConstants.KEY_AUTHOR_EMAIL, revCommit.getAuthorIdent().getEmailAddress());
		commit.put(GitConstants.KEY_COMMITTER_NAME, revCommit.getCommitterIdent().getName());
		commit.put(GitConstants.KEY_COMMITTER_EMAIL, revCommit.getCommitterIdent().getEmailAddress());
		commit.put(GitConstants.KEY_COMMIT_TIME, ((long) revCommit.getCommitTime()) * 1000 /* time in milliseconds */);
		commit.put(GitConstants.KEY_COMMIT_MESSAGE, revCommit.getFullMessage());
		commit.put(ProtocolConstants.KEY_CHILDREN, toJSON(getTagsForCommit(db, revCommit)));

		if (revCommit.getParentCount() > 0) {
			JSONArray diffs = new JSONArray();

			final TreeWalk tw = new TreeWalk(db);
			tw.reset(revCommit.getParent(0).getTree(), revCommit.getTree());
			tw.setRecursive(true);
			tw.setFilter(TreeFilter.ANY_DIFF);

			List<DiffEntry> l = DiffEntry.scan(tw);
			for (DiffEntry entr : l) {
				JSONObject diff = new JSONObject();
				diff.put(GitConstants.KEY_COMMIT_DIFF_NEWPATH, entr.getNewPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_OLDPATH, entr.getOldPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_CHANGETYPE, entr.getChangeType().toString());
				diffs.put(diff);
			}
			tw.release();

			commit.put(GitConstants.KEY_COMMIT_DIFFS, diffs);
		}

		return commit;
	}

	private JSONArray toJSON(Set<Ref> tags) {
		JSONArray children = new JSONArray();
		for (Ref ref : tags) {
			children.put(ref.getName());
		}
		return children;
	}

	private URI createCommitLocation(URI baseLocation, String commitName, String parameters) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), GitServlet.GIT_URI + "/" + GitConstants.COMMIT_RESOURCE + "/" + commitName + "/" + new Path(baseLocation.getPath()).removeFirstSegments(3), parameters, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private URI createDiffLocation(URI baseLocation, String commitName) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), GitServlet.GIT_URI + "/" + GitConstants.DIFF_RESOURCE + "/" + commitName + "/" + new Path(baseLocation.getPath()).removeFirstSegments(3), null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws ServletException, NoFilepatternException, IOException, JSONException, CoreException, URISyntaxException {
		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(1).uptoSegment(2));
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?
		db = new FileRepository(gitDir);

		JSONObject requestObject = OrionServlet.readJSONRequest(request);
		String commitToMerge = requestObject.optString(GitConstants.KEY_MERGE, null);
		if (commitToMerge != null) {
			return merge(request, response, db, commitToMerge);
		}

		// continue with creating new commit location

		String newCommitToCreatelocation = requestObject.optString(GitConstants.KEY_COMMIT_NEW, null);
		if (newCommitToCreatelocation != null)
			return createCommitLocation(request, response, db, newCommitToCreatelocation);

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

	private boolean merge(HttpServletRequest request, HttpServletResponse response, Repository db, String commitToMerge) throws ServletException, JSONException {
		try {
			ObjectId objectId = db.resolve(commitToMerge);
			Git git = new Git(db);
			MergeResult mergeResult = git.merge().include(objectId).call();
			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_RESULT, mergeResult.getMergeStatus().name());
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when merging.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when merging.", e));
		} catch (JGitInternalException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when merging.", e.getCause()));
		}
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws ServletException, IOException, JSONException, CoreException, URISyntaxException, JGitInternalException, GitAPIException {
		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(1));
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?
		db = new FileRepository(gitDir);

		JSONObject toPut = OrionServlet.readJSONRequest(request);
		String tagName = toPut.getString(ProtocolConstants.KEY_NAME);
		if (tagName != null) {
			return tag(request, response, db, path.segment(0), tagName);
		}
		return false;
	}

	private boolean tag(HttpServletRequest request, HttpServletResponse response, Repository db, String commitId, String tagName) throws AmbiguousObjectException, IOException, JGitInternalException, GitAPIException, JSONException, URISyntaxException {
		Git git = new Git(db);
		ObjectId objectId = db.resolve(commitId);

		RevWalk walk = new RevWalk(db);
		RevCommit revCommit = walk.lookupCommit(objectId);
		walk.parseBody(revCommit);

		GitTagHandlerV1.tag(git, revCommit, tagName);

		JSONObject result = toJSON(db, revCommit, OrionServlet.getURI(request));
		OrionServlet.writeJSONResponse(request, response, result);
		walk.dispose();
		return true;
	}

	// from https://gist.github.com/839693, credits to zx
	private static Set<Ref> getTagsForCommit(Repository repo, RevCommit commit) throws MissingObjectException, IOException {
		final Set<Ref> tags = new HashSet<Ref>();
		final RevWalk walk = new RevWalk(repo);
		walk.reset();
		for (final Ref ref : repo.getTags().values()) {
			final RevObject obj = walk.parseAny(ref.getObjectId());
			final RevCommit tagCommit;
			if (obj instanceof RevCommit) {
				tagCommit = (RevCommit) obj;
			} else if (obj instanceof RevTag) {
				tagCommit = walk.parseCommit(((RevTag) obj).getObject());
			} else {
				continue;
			}
			if (commit.equals(tagCommit) || walk.isMergedInto(commit, tagCommit)) {
				tags.add(ref);
			}
		}
		return tags;
	}
}
