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
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.filter.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.*;
import org.eclipse.orion.server.git.objects.Commit;
import org.eclipse.orion.server.git.objects.Log;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

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
		if (path.segment(0).equals("file")) { //$NON-NLS-1$
			// special case for git log --all
			IPath filePath = path.hasTrailingSeparator() ? path : path.removeLastSegments(1);
			Set<Entry<IPath, File>> set = GitUtils.getGitDirs(filePath, Traverse.GO_UP).entrySet();
			File gitDir = set.iterator().next().getValue();
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?

			db = new FileRepository(gitDir);

			String pattern = GitUtils.getRelativePath(path, set.iterator().next().getKey());
			return handleGetCommitLog(request, response, db, null, pattern);

		} else if (path.segment(1).equals("file")) { //$NON-NLS-1$
			// git log <ref>
			IPath filePath = path.hasTrailingSeparator() ? path.removeFirstSegments(1) : path.removeFirstSegments(1).removeLastSegments(1);
			Set<Entry<IPath, File>> set = GitUtils.getGitDirs(filePath, Traverse.GO_UP).entrySet();
			File gitDir = set.iterator().next().getValue();
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?

			db = new FileRepository(gitDir);

			// /{ref}/file/{projectId}...
			String parts = request.getParameter("parts"); //$NON-NLS-1$
			String pattern = GitUtils.getRelativePath(path, set.iterator().next().getKey());
			if (path.segmentCount() > 3 && "body".equals(parts)) { //$NON-NLS-1$
				return handleGetCommitBody(request, response, db, path.segment(0), pattern);
			}
			if (path.segmentCount() > 2 && (parts == null || "log".equals(parts))) { //$NON-NLS-1$
				return handleGetCommitLog(request, response, db, path.segment(0), pattern);
			}
		}
		return false;
	}

	private boolean handleGetCommitBody(HttpServletRequest request, HttpServletResponse response, Repository db, String ref, String pattern) throws IOException, ServletException, URISyntaxException, CoreException {
		ObjectId refId = db.resolve(ref);
		if (refId == null) {
			String msg = NLS.bind("Failed to get commit body for ref {0}", ref);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}
		RevWalk walk = new RevWalk(db);
		walk.setTreeFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(Collections.singleton(pattern)), TreeFilter.ANY_DIFF));
		RevCommit revCommit = walk.parseCommit(refId);

		Commit commit = new Commit(null /* not needed */, db, revCommit, pattern);
		ObjectStream stream = commit.toObjectStream();
		if (stream == null) {
			String msg = NLS.bind("Commit body for ref {0} not found", ref);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		}
		IOUtilities.pipe(stream, response.getOutputStream(), true, false);

		return true;
	}

	private boolean handleGetCommitLog(HttpServletRequest request, HttpServletResponse response, Repository db, String refIdsRange, String pattern) throws AmbiguousObjectException, IOException, ServletException, JSONException, URISyntaxException, CoreException {
		int page = request.getParameter("page") != null ? new Integer(request.getParameter("page")).intValue() : 0; //$NON-NLS-1$ //$NON-NLS-2$
		int pageSize = request.getParameter("pageSize") != null ? new Integer(request.getParameter("pageSize")).intValue() : PAGE_SIZE; //$NON-NLS-1$ //$NON-NLS-2$

		ObjectId toObjectId = null;
		ObjectId fromObjectId = null;

		Ref toRefId = null;
		Ref fromRefId = null;

		Git git = new Git(db);
		LogCommand lc = git.log();

		if (refIdsRange != null) {
			// git log <since>..<until>
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

			// set the commit range
			lc.add(toObjectId);

			if (fromObjectId != null)
				lc.not(fromObjectId);
		} else {
			// git log --all
			// workaround for git log --all - see bug 353310
			List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
			for (Ref branch : branches) {
				lc.add(branch.getObjectId());
			}
		}

		if (pattern != null && !pattern.isEmpty()) {
			lc.addPath(pattern);
		}

		try {
			Iterable<RevCommit> commits = lc.call();
			URI baseLocation = getURI(request);
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(baseLocation, refIdsRange == null ? BaseToCloneConverter.COMMIT : BaseToCloneConverter.COMMIT_REFRANGE);

			Log log = new Log(cloneLocation, db, commits, pattern, toRefId, fromRefId);
			JSONObject result = log.toJSON(page, pageSize);

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

		String committerName = requestObject.optString(GitConstants.KEY_COMMITTER_NAME, null);
		String committerEmail = requestObject.optString(GitConstants.KEY_COMMITTER_EMAIL, null);
		String authorName = requestObject.optString(GitConstants.KEY_AUTHOR_NAME, null);
		String authorEmail = requestObject.optString(GitConstants.KEY_AUTHOR_EMAIL, null);

		Git git = new Git(db);
		CommitCommand cc = git.commit();

		// workaround of a bug in JGit which causes invalid 
		// support of null values of author/committer name/email, see bug 352984
		PersonIdent defPersonIdent = new PersonIdent(db);
		if (committerName == null)
			committerName = defPersonIdent.getName();
		if (committerEmail == null)
			committerEmail = defPersonIdent.getEmailAddress();
		if (authorName == null)
			authorName = committerName;
		if (authorEmail == null)
			authorEmail = committerEmail;
		cc.setCommitter(committerName, committerEmail);
		cc.setAuthor(authorName, authorEmail);

		// support for committing by path: "git commit -o path"
		String pattern = GitUtils.getRelativePath(path.removeFirstSegments(1), set.iterator().next().getKey());
		if (!pattern.isEmpty()) {
			cc.setOnly(pattern);
		}

		try {
			// "git commit [--amend] -m '{message}' [-a|{path}]"
			RevCommit lastCommit = cc.setAmend(amend).setMessage(message).call();

			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.COMMIT_REFRANGE);
			Commit commit = new Commit(cloneLocation, db, lastCommit, pattern);
			JSONObject result = commit.toJSON();
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
		RevWalk revWalk = new RevWalk(db);
		try {

			Ref headRef = db.getRef(Constants.HEAD);
			if (headRef == null)
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when cherry-picking.", null));
			RevCommit head = revWalk.parseCommit(headRef.getObjectId());

			ObjectId objectId = db.resolve(commitToCherryPick);
			Git git = new Git(db);
			CherryPickResult cherryPickResult = git.cherryPick().include(objectId).call();
			RevCommit newHead = cherryPickResult.getNewHead();

			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_RESULT, cherryPickResult.getStatus().name());
			result.put(GitConstants.KEY_HEAD_UPDATED, !head.equals(newHead));
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when cherry-picking.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when cherry-picking.", e));
		} catch (JGitInternalException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when cherry-picking.", e.getCause()));
		} finally {
			revWalk.release();
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

	private boolean tag(HttpServletRequest request, HttpServletResponse response, Repository db, String commitId, String tagName, boolean isRoot) throws JSONException, URISyntaxException, ServletException {
		Git git = new Git(db);
		RevWalk walk = new RevWalk(db);
		try {
			ObjectId objectId = db.resolve(commitId);
			RevCommit revCommit = walk.lookupCommit(objectId);
			walk.parseBody(revCommit);

			GitTagHandlerV1.tag(git, revCommit, tagName);

			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.COMMIT_REFRANGE);
			Commit commit = new Commit(cloneLocation, db, revCommit, null);
			JSONObject result = commit.toJSON();
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when tagging.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when tagging.", e));
		} catch (JGitInternalException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when tagging.", e));
		} catch (CoreException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when tagging.", e));
		} finally {
			walk.dispose();
		}
	}
}
