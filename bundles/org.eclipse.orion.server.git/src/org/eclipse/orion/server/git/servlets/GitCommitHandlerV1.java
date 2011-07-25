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
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
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
import org.eclipse.orion.server.core.users.UserUtilities;
import org.eclipse.orion.server.git.*;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
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
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to process an operation on commits for {0}", path);
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
		if (p.hasTrailingSeparator())
			np = np.addTrailingSeparator();
		URI nu = new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), np.toString(), u.getQuery(), u.getFragment());
		response.setHeader(ProtocolConstants.HEADER_LOCATION, nu.toString());
		response.setStatus(HttpServletResponse.SC_OK);
		return true;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws CoreException, IOException, ServletException, JSONException, URISyntaxException {
		IPath filePath = path.hasTrailingSeparator() ? path.removeFirstSegments(1) : path.removeFirstSegments(1).removeLastSegments(1);
		Set<Entry<IPath, File>> set = GitUtils.getGitDirs(filePath, Traverse.GO_UP).entrySet();
		File gitDir = set.iterator().next().getValue();
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?

		db = new FileRepository(gitDir);

		// /{ref}/file/{projectId}...}
		String parts = request.getParameter("parts"); //$NON-NLS-1$
		String pattern = GitUtils.getRelativePath(path, set.iterator().next().getKey());
		if (path.segmentCount() > 3 && "body".equals(parts)) { //$NON-NLS-1$
			return handleGetCommitBody(request, response, db, path.segment(0), pattern);
		}
		if (path.segmentCount() > 2 && (parts == null || "log".equals(parts))) { //$NON-NLS-1$
			return handleGetCommitLog(request, response, db, path.segment(0), pattern);
		}

		return false;
	}

	private boolean handleGetCommitBody(HttpServletRequest request, HttpServletResponse response, Repository db, String ref, String pattern) throws AmbiguousObjectException, IOException, ServletException {
		ObjectId refId = db.resolve(ref);
		if (refId == null) {
			String msg = NLS.bind("Failed to generate commit log for ref {0}", ref);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}

		RevWalk walk = new RevWalk(db);
		walk.setTreeFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(Collections.singleton(pattern)), TreeFilter.ANY_DIFF));
		RevCommit commit = walk.parseCommit(refId);
		final TreeWalk w = TreeWalk.forPath(db, pattern, commit.getTree());
		if (w == null) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, null, null));
		}

		ObjectId blobId = w.getObjectId(0);
		ObjectStream stream = db.open(blobId, Constants.OBJ_BLOB).openStream();
		IOUtilities.pipe(stream, response.getOutputStream(), true, false);

		return true;
	}

	private boolean handleGetCommitLog(HttpServletRequest request, HttpServletResponse response, Repository db, String refIdsRange, String path) throws AmbiguousObjectException, IOException, ServletException, JSONException, URISyntaxException, CoreException {
		int page = request.getParameter("page") != null ? new Integer(request.getParameter("page")).intValue() : 0; //$NON-NLS-1$ //$NON-NLS-2$
		int pageSize = request.getParameter("pageSize") != null ? new Integer(request.getParameter("pageSize")).intValue() : PAGE_SIZE; //$NON-NLS-1$ //$NON-NLS-2$

		ObjectId toObjectId = null;
		ObjectId fromObjectId = null;

		Ref toRefId = null;
		Ref fromRefId = null;

		if (refIdsRange.contains("..")) { //$NON-NLS-1$
			String[] commits = refIdsRange.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				String msg = NLS.bind("Failed to generate commit log for ref {0}", refIdsRange);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}

			fromObjectId = db.resolve(commits[0]);
			fromRefId = db.getRef(commits[0]);
			if (fromObjectId == null) {
				String msg = NLS.bind("Failed to generate commit log for ref {0}", commits[0]);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}

			toObjectId = db.resolve(commits[1]);
			toRefId = db.getRef(commits[1]);
			if (toObjectId == null) {
				String msg = NLS.bind("No ref or commit found: {0}", commits[1]);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		} else {
			toObjectId = db.resolve(refIdsRange);
			toRefId = db.getRef(refIdsRange);
			if (toObjectId == null) {
				String msg = NLS.bind("No ref or commit found: {0}", refIdsRange);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		}

		Git git = new Git(db);
		LogCommand log = git.log();
		// set the commit range
		log.add(toObjectId);
		if (fromObjectId != null)
			log.not(fromObjectId);

		// set the path filter
		TreeFilter filter = null;

		boolean isRoot = true;
		if (path != null && !"".equals(path)) { //$NON-NLS-1$
			filter = AndTreeFilter.create(PathFilterGroup.createFromStrings(Collections.singleton(path)), TreeFilter.ANY_DIFF);
			log.addPath(path);
			isRoot = false;
		}

		try {
			Iterable<RevCommit> commits = log.call();
			JSONObject result = toJSON(db, OrionServlet.getURI(request), commits, page, pageSize, filter, isRoot);

			result.put(GitConstants.KEY_REPOSITORY_PATH, isRoot ? "" : path); //$NON-NLS-1$

			if (toRefId != null) {
				result.put(GitConstants.KEY_REMOTE, BaseToRemoteConverter.getRemoteBranchLocation(getURI(request), Repository.shortenRefName(toRefId.getName()), db, BaseToRemoteConverter.REMOVE_FIRST_3));

				String refTargetName = toRefId.getTarget().getName();
				if (refTargetName.startsWith(Constants.R_HEADS)) {
					// this is a branch
					result.put(GitConstants.KEY_LOG_TO_REF, BranchToJSONConverter.toJSON(toRefId.getTarget(), db, getURI(request), 3));
				}
			}
			if (fromRefId != null) {
				String refTargetName = fromRefId.getTarget().getName();
				if (refTargetName.startsWith(Constants.R_HEADS)) {
					// this is a branch
					result.put(GitConstants.KEY_LOG_FROM_REF, BranchToJSONConverter.toJSON(fromRefId.getTarget(), db, getURI(request), 3));
				}
			}
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} catch (NoHeadException e) {
			String msg = NLS.bind("No HEAD reference found when generating log for ref {0}", refIdsRange);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} catch (JGitInternalException e) {
			String msg = NLS.bind("An internal error occured when generating log for ref {0}", refIdsRange);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
	}

	private JSONObject toJSON(Repository db, URI baseLocation, Iterable<RevCommit> commits, int page, int pageSize, TreeFilter filter, boolean isRoot) throws JSONException, URISyntaxException, MissingObjectException, IOException {
		boolean pageable = (page > 0);
		int startIndex = (page - 1) * pageSize;
		int index = 0;

		JSONObject result = new JSONObject();
		JSONArray children = new JSONArray();
		for (RevCommit revCommit : commits) {
			if (pageable && index < startIndex) {
				index++;
				continue;
			}

			if (pageable && index >= startIndex + pageSize)
				break;

			index++;

			children.put(toJSON(db, revCommit, baseLocation, filter, isRoot));
		}
		result.put(ProtocolConstants.KEY_CHILDREN, children);
		return result;
	}

	private JSONObject toJSON(Repository db, RevCommit revCommit, URI baseLocation, TreeFilter filter, boolean isRoot) throws JSONException, URISyntaxException, IOException {
		JSONObject commit = new JSONObject();
		commit.put(ProtocolConstants.KEY_LOCATION, createCommitLocation(baseLocation, revCommit.getName(), null));
		commit.put(ProtocolConstants.KEY_CONTENT_LOCATION, createCommitLocation(baseLocation, revCommit.getName(), "parts=body")); //$NON-NLS-1$
		commit.put(GitConstants.KEY_DIFF, createDiffLocation(baseLocation, revCommit.getName(), null, null, isRoot));
		commit.put(ProtocolConstants.KEY_NAME, revCommit.getName());
		PersonIdent author = revCommit.getAuthorIdent();
		commit.put(GitConstants.KEY_AUTHOR_NAME, author.getName());
		commit.put(GitConstants.KEY_AUTHOR_EMAIL, author.getEmailAddress());
		String authorImage = UserUtilities.getImageLink(author.getEmailAddress());
		if (authorImage != null)
			commit.put(GitConstants.KEY_AUTHOR_IMAGE, authorImage);
		PersonIdent committer = revCommit.getCommitterIdent();
		commit.put(GitConstants.KEY_COMMITTER_NAME, committer.getName());
		commit.put(GitConstants.KEY_COMMITTER_EMAIL, committer.getEmailAddress());
		commit.put(GitConstants.KEY_COMMIT_TIME, ((long) revCommit.getCommitTime()) * 1000 /* time in milliseconds */);
		commit.put(GitConstants.KEY_COMMIT_MESSAGE, revCommit.getFullMessage());
		commit.put(ProtocolConstants.KEY_CHILDREN, toJSON(getTagsForCommit(db, revCommit)));
		commit.put(ProtocolConstants.KEY_TYPE, GitConstants.COMMIT_TYPE);

		if (revCommit.getParentCount() > 0) {
			JSONArray diffs = new JSONArray();

			final TreeWalk tw = new TreeWalk(db);
			final RevWalk rw = new RevWalk(db);
			RevCommit parent = rw.parseCommit(revCommit.getParent(0));
			tw.reset(parent.getTree(), revCommit.getTree());
			tw.setRecursive(true);

			if (filter != null)
				tw.setFilter(filter);
			else
				tw.setFilter(TreeFilter.ANY_DIFF);

			List<DiffEntry> l = DiffEntry.scan(tw);
			for (DiffEntry entr : l) {
				JSONObject diff = new JSONObject();
				diff.put(ProtocolConstants.KEY_TYPE, GitConstants.DIFF_TYPE);

				diff.put(GitConstants.KEY_COMMIT_DIFF_NEWPATH, entr.getNewPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_OLDPATH, entr.getOldPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_CHANGETYPE, entr.getChangeType().toString());

				// add diff location for the commit
				String path = entr.getChangeType() != ChangeType.DELETE ? entr.getNewPath() : entr.getOldPath();
				diff.put(GitConstants.KEY_DIFF, createDiffLocation(baseLocation, revCommit.getName(), revCommit.getParent(0).getName(), path, isRoot));

				diffs.put(diff);
			}
			tw.release();

			commit.put(GitConstants.KEY_COMMIT_DIFFS, diffs);
		}

		return commit;
	}

	private JSONArray toJSON(Map<String, Ref> revTags) throws JSONException {
		JSONArray children = new JSONArray();
		for (Entry<String, Ref> revTag : revTags.entrySet()) {
			JSONObject tag = new JSONObject();
			tag.put(ProtocolConstants.KEY_NAME, revTag.getKey());
			tag.put(ProtocolConstants.KEY_FULL_NAME, revTag.getValue().getName());
			children.put(tag);
		}
		return children;
	}

	private URI createCommitLocation(URI baseLocation, String commitName, String parameters) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), GitServlet.GIT_URI + "/" + GitConstants.COMMIT_RESOURCE + "/" + commitName + "/" + new Path(baseLocation.getPath()).removeFirstSegments(3), parameters, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private URI createDiffLocation(URI baseLocation, String toRefId, String fromRefId, String path, boolean isRoot) throws URISyntaxException {
		String diffPath = GitServlet.GIT_URI + "/" + GitConstants.DIFF_RESOURCE + "/"; //$NON-NLS-1$ //$NON-NLS-2$

		if (fromRefId != null)
			diffPath += fromRefId + ".."; //$NON-NLS-1$

		diffPath += toRefId + "/"; //$NON-NLS-1$

		if (path == null) {
			diffPath += new Path(baseLocation.getPath()).removeFirstSegments(3);
		} else if (isRoot) {
			diffPath += new Path(baseLocation.getPath()).removeFirstSegments(3).append(path);
		} else {
			IPath p = new Path(baseLocation.getPath());
			diffPath += p.removeLastSegments(p.segmentCount() - 5).removeFirstSegments(3).append(path);
		}

		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), diffPath, null, null);
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws ServletException, NoFilepatternException, IOException, JSONException, CoreException, URISyntaxException {
		IPath filePath = path.hasTrailingSeparator() ? path.removeFirstSegments(1) : path.removeFirstSegments(1).removeLastSegments(1);
		Set<Entry<IPath, File>> set = GitUtils.getGitDirs(filePath, Traverse.GO_UP).entrySet();
		File gitDir = set.iterator().next().getValue();
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?
		db = new FileRepository(gitDir);

		JSONObject requestObject = OrionServlet.readJSONRequest(request);
		String commitToMerge = requestObject.optString(GitConstants.KEY_MERGE, null);
		if (commitToMerge != null) {
			return merge(request, response, db, commitToMerge);
		}

		String commitToRebase = requestObject.optString(GitConstants.KEY_REBASE, null);
		String rebaseOperation = requestObject.optString(GitConstants.KEY_OPERATION, null);
		if (commitToRebase != null) {
			return rebase(request, response, db, commitToRebase, rebaseOperation);
		}

		String commitToCherryPick = requestObject.optString(GitConstants.KEY_CHERRY_PICK, null);
		if (commitToCherryPick != null) {
			return cherryPick(request, response, db, commitToCherryPick);
		}

		// continue with creating new commit location

		String newCommitToCreatelocation = requestObject.optString(GitConstants.KEY_COMMIT_NEW, null);
		if (newCommitToCreatelocation != null)
			return createCommitLocation(request, response, db, newCommitToCreatelocation);

		ObjectId refId = db.resolve(path.segment(0));
		if (refId == null || !Constants.HEAD.equals(path.segment(0))) {
			String msg = NLS.bind("Commit failed. Ref must be HEAD and is {0}", path.segment(0));
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}

		String message = requestObject.optString(GitConstants.KEY_COMMIT_MESSAGE, null);
		if (message == null || message.isEmpty()) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Missing commit message.", null));
		}

		boolean amend = Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_COMMIT_AMEND, null));

		Git git = new Git(db);
		CommitCommand commit = git.commit();

		// support for committing by path: "git commit -o path"
		boolean isRoot = true;
		String pattern = GitUtils.getRelativePath(path.removeFirstSegments(1), set.iterator().next().getKey());
		if (!pattern.isEmpty()) {
			commit.setOnly(pattern);
			isRoot = false;
		}

		try {
			// "git commit [--amend] -m '{message}' [-a|{path}]"
			RevCommit lastCommit = commit.setAmend(amend).setMessage(message).call();

			JSONObject result = toJSON(db, lastCommit, getURI(request), null, isRoot);
			OrionServlet.writeJSONResponse(request, response, result);
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

	private boolean rebase(HttpServletRequest request, HttpServletResponse response, Repository db, String commitToRebase, String rebaseOperation) throws ServletException, JSONException, AmbiguousObjectException, IOException {
		JSONObject result = new JSONObject();
		try {
			Git git = new Git(db);
			RebaseCommand rebase = git.rebase();
			Operation operation;
			if (rebaseOperation != null) {
				operation = Operation.valueOf(rebaseOperation);
			} else {
				operation = Operation.BEGIN;
			}
			if (commitToRebase != null && !commitToRebase.isEmpty()) {
				ObjectId objectId = db.resolve(commitToRebase);
				rebase.setUpstream(objectId);
			} else if (operation.equals(Operation.BEGIN)) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Missing commit refId.", null));
			}
			rebase.setOperation(operation);
			RebaseResult rebaseResult = rebase.call();
			result.put(GitConstants.KEY_RESULT, rebaseResult.getStatus().name());
		} catch (UnmergedPathsException e) {
			// this error should be handled by client, so return a proper status
			result.put(GitConstants.KEY_RESULT, AdditionalRebaseStatus.FAILED_UNMERGED_PATHS.name());
		} catch (WrongRepositoryStateException e) {
			// this error should be handled by client, so return a proper status
			result.put(GitConstants.KEY_RESULT, AdditionalRebaseStatus.FAILED_WRONG_REPOSITORY_STATE.name());
		} catch (IllegalArgumentException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid rebase operation.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when rebasing.", e));
		} catch (JGitInternalException e) {
			// get cause and try to handle 
			if (e.getCause() instanceof org.eclipse.jgit.errors.CheckoutConflictException) {
				// this error should be handled by client, so return a proper status
				result.put(GitConstants.KEY_RESULT, AdditionalRebaseStatus.FAILED_PENDING_CHANGES.name());
			} else {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when rebasing.", e));
			}
		}
		OrionServlet.writeJSONResponse(request, response, result);
		return true;
	}

	private boolean cherryPick(HttpServletRequest request, HttpServletResponse response, Repository db, String commitToCherryPick) throws ServletException, JSONException {
		try {
			ObjectId objectId = db.resolve(commitToCherryPick);
			Git git = new Git(db);
			CherryPickResult cherryPickResult = git.cherryPick().include(objectId).call();
			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_RESULT, cherryPickResult.getStatus().name());
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when cherry-picking.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when cherry-picking.", e));
		} catch (JGitInternalException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when cherry-picking.", e.getCause()));
		}
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws ServletException, IOException, JSONException, CoreException, URISyntaxException, JGitInternalException, GitAPIException {
		IPath filePath = path.removeFirstSegments(1);
		Set<Entry<IPath, File>> set = GitUtils.getGitDirs(filePath, Traverse.GO_UP).entrySet();
		File gitDir = set.iterator().next().getValue();

		if (gitDir == null)
			return false; // TODO: or an error response code, 405?
		db = new FileRepository(gitDir);
		boolean isRoot = "".equals(GitUtils.getRelativePath(path, set.iterator().next().getKey())); //$NON-NLS-1$
		JSONObject toPut = OrionServlet.readJSONRequest(request);
		String tagName = toPut.getString(ProtocolConstants.KEY_NAME);
		if (tagName != null) {
			return tag(request, response, db, path.segment(0), tagName, isRoot);
		}
		return false;
	}

	private boolean tag(HttpServletRequest request, HttpServletResponse response, Repository db, String commitId, String tagName, boolean isRoot) throws AmbiguousObjectException, IOException, JGitInternalException, GitAPIException, JSONException, URISyntaxException {
		Git git = new Git(db);
		ObjectId objectId = db.resolve(commitId);

		RevWalk walk = new RevWalk(db);
		RevCommit revCommit = walk.lookupCommit(objectId);
		walk.parseBody(revCommit);

		GitTagHandlerV1.tag(git, revCommit, tagName);

		JSONObject result = toJSON(db, revCommit, OrionServlet.getURI(request), null, isRoot);
		OrionServlet.writeJSONResponse(request, response, result);
		walk.dispose();
		return true;
	}

	// from https://gist.github.com/839693, credits to zx
	private static Map<String, Ref> getTagsForCommit(Repository repo, RevCommit commit) throws MissingObjectException, IOException {
		final Map<String, Ref> revTags = new HashMap<String, Ref>();
		final RevWalk walk = new RevWalk(repo);
		walk.reset();
		for (final Entry<String, Ref> revTag : repo.getTags().entrySet()) {
			final RevObject obj = walk.parseAny(revTag.getValue().getObjectId());
			final RevCommit tagCommit;
			if (obj instanceof RevCommit) {
				tagCommit = (RevCommit) obj;
			} else if (obj instanceof RevTag) {
				tagCommit = walk.parseCommit(((RevTag) obj).getObject());
			} else {
				continue;
			}
			if (commit.equals(tagCommit) || walk.isMergedInto(commit, tagCommit)) {
				revTags.put(revTag.getKey(), revTag.getValue());
			}
		}
		return revTags;
	}
}
