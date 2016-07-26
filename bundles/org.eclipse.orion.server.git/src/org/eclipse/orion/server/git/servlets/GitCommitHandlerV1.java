/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.UserEmailUtil;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.eclipse.orion.server.git.AdditionalRebaseStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.jobs.LogJob;
import org.eclipse.orion.server.git.objects.Commit;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class GitCommitHandlerV1 extends AbstractGitHandler {

	private final static String EMAIL_REVIEW_REQUEST_FILE = "/emails/EmailReviewRequestNotification.txt"; //$NON-NLS-1$
	private final static String EMAIL_COMMITER_NAME = "<COMMITER_NAME>";
	private final static String EMAIL_COMMIT_MESSAGE = "<COMMIT_MESSAGE>";
	private static final String EMAIL_URL_LINK = "<URL>"; //$NON-NLS-1$

	private final static int PAGE_SIZE = 50;

	private Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.git");
	private EmailContent reviewRequestEmail;

	public class EmailContent {
		private String title;
		private String content;

		public String getTitle() {
			return title;
		}

		public String getContent() {
			return content;
		}

		public EmailContent(String fileName) throws URISyntaxException, IOException {
			URL entry = this.getClass().getResource(fileName);
			if (entry == null)
				throw new IOException("File not found: " + fileName);
			BufferedReader reader = new BufferedReader(new InputStreamReader(entry.openStream()));
			String line = null;
			try {
				title = reader.readLine();
				StringBuilder stringBuilder = new StringBuilder();
				String ls = System.getProperty("line.separator");
				while ((line = reader.readLine()) != null) {
					stringBuilder.append(line);
					stringBuilder.append(ls);
				}
				content = stringBuilder.toString();
			} finally {
				reader.close();
			}
		}
	};

	GitCommitHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	private boolean identifyNewCommitResource(HttpServletRequest request, HttpServletResponse response, Repository db, String newCommit)
			throws ServletException {
		try {
			URI u = getURI(request);
			IPath p = new Path(u.getPath());
			IPath np = new Path("/"); //$NON-NLS-1$
			for (int i = 0; i < p.segmentCount(); i++) {
				String s = p.segment(i);
				if (i == 2) {
					s += ".." + GitUtils.encode(newCommit); //$NON-NLS-1$
				}
				np = np.append(s);
			}
			if (p.hasTrailingSeparator())
				np = np.addTrailingSeparator();
			URI nu = new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), np.toString(), request.getQueryString(), u.getFragment());
			JSONObject result = new JSONObject();
			result.put(ProtocolConstants.KEY_LOCATION, nu);
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, resovleOrionURI(request, nu).toString());
			return true;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occurred when identifying a new Commit resource.", e));
		}
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {
		String gitSegment = requestInfo.gitSegment;
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		String pattern = requestInfo.relativePath;
		IPath filePath = requestInfo.filePath;
		try {
			if (gitSegment == null) {
				// special case for git log --all
				return handleGetCommitLog(request, response, filePath, db, null, pattern);
			} else {
				// git log <ref>
				String parts = request.getParameter("parts"); //$NON-NLS-1$

				if ("body".equals(parts)) { //$NON-NLS-1$
					return handleGetCommitBody(request, response, db, gitSegment, pattern);
				} else if (parts == null || "log".equals(parts)) { //$NON-NLS-1$
					return handleGetCommitLog(request, response, filePath, db, gitSegment, pattern);
				}
			}
			return false;
		} catch (Exception e) {
			String msg = "An error occurred while requesting commit information.";
			logger.error(msg + " " + requestInfo.request.getRequestURI(), e);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
	}

	private boolean handleGetCommitBody(HttpServletRequest request, HttpServletResponse response, Repository db, String ref, String pattern)
			throws IOException, ServletException, CoreException {
		ObjectId refId = db.resolve(ref);
		if (refId == null) {
			String msg = NLS.bind("Failed to get commit body for ref {0}", ref);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}
		RevWalk walk = new RevWalk(db);
		walk.setTreeFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(Collections.singleton(pattern)), TreeFilter.ANY_DIFF));
		RevCommit revCommit = walk.parseCommit(refId);
		walk.dispose();

		Commit commit = new Commit(null /* not needed */, db, revCommit, pattern);
		ObjectStream stream = commit.toObjectStream();
		if (stream == null) {
			String msg = NLS.bind("Commit body for ref {0} not found", ref);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		}
		IOUtilities.pipe(stream, response.getOutputStream(), true, false);

		return true;
	}

	private boolean handleGetCommitLog(HttpServletRequest request, HttpServletResponse response, IPath filePath, Repository db, String refIdsRange,
			String pattern) throws AmbiguousObjectException, IOException, ServletException, JSONException, URISyntaxException, CoreException {
		int page = request.getParameter("page") != null ? new Integer(request.getParameter("page")).intValue() : 0; //$NON-NLS-1$ //$NON-NLS-2$
		int pageSize = request.getParameter("pageSize") != null ? new Integer(request.getParameter("pageSize")).intValue() : PAGE_SIZE; //$NON-NLS-1$ //$NON-NLS-2$
		String messageFilter = request.getParameter("filter"); //$NON-NLS-1$
		String authorFilter = request.getParameter("author"); //$NON-NLS-1$
		String committerFilter = request.getParameter("committer"); //$NON-NLS-1$
		String sha1Filter = request.getParameter("sha1"); //$NON-NLS-1$
		String fromDate = request.getParameter("fromDate"); //$NON-NLS-1$
		String toDate = request.getParameter("toDate"); //$NON-NLS-1$
		String mergeBaseFilter = request.getParameter("mergeBase"); //$NON-NLS-1$
		ObjectId toObjectId = null;
		ObjectId fromObjectId = null;

		Ref toRefId = null;
		Ref fromRefId = null;

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
			toObjectId = getCommitObjectId(db, toObjectId);
		}

		URI baseLocation = getURI(request);
		URI cloneLocation = BaseToCloneConverter.getCloneLocation(baseLocation,
				refIdsRange == null ? BaseToCloneConverter.COMMIT : BaseToCloneConverter.COMMIT_REFRANGE);

		LogJob job = new LogJob(TaskJobHandler.getUserId(request), filePath, cloneLocation, page, pageSize, toObjectId, fromObjectId, toRefId, fromRefId,
				refIdsRange, pattern, messageFilter, authorFilter, committerFilter, sha1Filter, "true".equals(mergeBaseFilter), fromDate, toDate);
		return TaskJobHandler.handleTaskJob(request, response, job, statusHandler, JsonURIUnqualificationStrategy.ALL_NO_GIT);
	}

	private ObjectId getCommitObjectId(Repository db, ObjectId oid) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		RevWalk walk = new RevWalk(db);
		try {
			return walk.parseCommit(oid);
		} finally {
			walk.close();
		}
	}

	@Override
	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {
		String gitSegment = requestInfo.gitSegment;
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		String pattern = requestInfo.relativePath;
		JSONObject requestObject = requestInfo.getJSONRequest();
		try {
			String commitToMerge = requestObject.optString(GitConstants.KEY_MERGE, null);
			if (commitToMerge != null) {
				boolean squash = requestObject.optBoolean(GitConstants.KEY_SQUASH, false);
				return merge(request, response, db, commitToMerge, squash);
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

			String commitToRevert = requestObject.optString(GitConstants.KEY_REVERT, null);
			if (commitToRevert != null) {
				return revert(request, response, db, commitToRevert);
			}

			String newCommit = requestObject.optString(GitConstants.KEY_COMMIT_NEW, null);
			if (newCommit != null)
				return identifyNewCommitResource(request, response, db, newCommit);

			String reviewReqLogin = requestObject.optString(GitConstants.KEY_REVIEW_REQ_NOTIFY_LOGIN);
			if (reviewReqLogin != null && reviewReqLogin.length() != 0) {
				String reviewReqUrl = requestObject.optString(GitConstants.KEY_REVIEW_REQ_URL);
				String ReviewReqCommit = requestObject.optString(GitConstants.KEY_REVIEW_REQ_COMMIT);
				String ReviewReqAuthorName = requestObject.optString(GitConstants.KEY_REVIEW_REQ_AUTHOR_NAME);
				String ReviewMessage = requestObject.optString(GitConstants.KEY_REVIEW_REQ_MESSAGE);
				return sendNotification(request, response, db, reviewReqLogin, ReviewReqCommit, reviewReqUrl, ReviewReqAuthorName, ReviewMessage);
			}

			if (!Constants.HEAD.equals(gitSegment)) {
				String msg = NLS.bind("Commit failed. Ref must be HEAD and is {0}", gitSegment);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}

			String message = requestObject.optString(GitConstants.KEY_COMMIT_MESSAGE, null);
			if (message == null || message.isEmpty()) {
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Missing commit message.", null));
			}

			Git git = Git.wrap(db);
			CommitCommand cc = git.commit();
			Config config = git.getRepository().getConfig();

			boolean amend = Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_COMMIT_AMEND, null));
			boolean insertChangeId = config.getBoolean(ConfigConstants.CONFIG_GERRIT_SECTION, ConfigConstants.CONFIG_KEY_CREATECHANGEID, false)
					|| Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_CHANGE_ID, null));

			String committerName = requestObject.optString(GitConstants.KEY_COMMITTER_NAME, null);
			String committerEmail = requestObject.optString(GitConstants.KEY_COMMITTER_EMAIL, null);
			String authorName = requestObject.optString(GitConstants.KEY_AUTHOR_NAME, null);
			String authorEmail = requestObject.optString(GitConstants.KEY_AUTHOR_EMAIL, null);

			// workaround of a bug in JGit which causes invalid
			// support of null values of author/committer name/email, see bug
			// 352984
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
			if (insertChangeId)
				cc.setInsertChangeId(true);

			// support for committing by path: "git commit -o path"
			if (!pattern.isEmpty()) {
				cc.setOnly(pattern);
			}

			try {
				// "git commit [--amend] -m '{message}' [-a|{path}]"
				RevCommit lastCommit = cc.setAmend(amend).setMessage(message).call();

				URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.COMMIT_REFRANGE);
				Commit commit = new Commit(cloneLocation, db, lastCommit, pattern);
				JSONObject result = commit.toJSON();
				OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			} catch (GitAPIException e) {
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "An error occurred when committing.", e));
			} catch (UnmergedPathException e) {
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred when committing.", e));
			}
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when requesting commit information.", e));
		}
	}

	private boolean merge(HttpServletRequest request, HttpServletResponse response, Repository db, String commitToMerge, boolean squash)
			throws ServletException, JSONException {
		try {
			ObjectId objectId = db.resolve(commitToMerge);
			Git git = Git.wrap(db);
			MergeResult mergeResult = git.merge().setSquash(squash).include(objectId).call();
			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_RESULT, mergeResult.getMergeStatus().name());
			if (mergeResult.getFailingPaths() != null && !mergeResult.getFailingPaths().isEmpty()) {
				result.put(GitConstants.KEY_FAILING_PATHS, mergeResult.getFailingPaths());
			}
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;
		} catch (CheckoutConflictException e) {
			return workaroundBug356918(request, response, e);
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when merging.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when merging.", e));
		}
	}

	private boolean workaroundBug356918(HttpServletRequest request, HttpServletResponse response, Exception e) throws ServletException, JSONException {
		if (e instanceof CheckoutConflictException) {
			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_RESULT, MergeStatus.FAILED.name());
			Map<String, MergeFailureReason> failingPaths = new HashMap<String, MergeFailureReason>();
			String[] files = e.getMessage().split("\n"); //$NON-NLS-1$
			for (int i = 1; i < files.length; i++) {
				// TODO: this is not always true, but it's a temporary
				// workaround
				failingPaths.put(files[i], MergeFailureReason.DIRTY_WORKTREE);
			}
			result.put(GitConstants.KEY_FAILING_PATHS, failingPaths);
			try {
				OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			} catch (IOException e1) {
				e = e1;
			}
		}
		return statusHandler.handleRequest(request, response,
				new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when merging.", e.getCause()));
	}

	private boolean rebase(HttpServletRequest request, HttpServletResponse response, Repository db, String commitToRebase, String rebaseOperation)
			throws ServletException, JSONException, AmbiguousObjectException, IOException {
		JSONObject result = new JSONObject();
		try {
			Git git = Git.wrap(db);
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
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Missing commit refId.", null));
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
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid rebase operation.", e));
		} catch (GitAPIException e) {
			// get cause and try to handle
			if (e.getCause() instanceof org.eclipse.jgit.errors.CheckoutConflictException) {
				// this error should be handled by client, so return a proper
				// status
				result.put(GitConstants.KEY_RESULT, AdditionalRebaseStatus.FAILED_PENDING_CHANGES.name());
			} else {
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when rebasing.", e));
			}
		}
		OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
		return true;
	}

	private boolean cherryPick(HttpServletRequest request, HttpServletResponse response, Repository db, String commitToCherryPick)
			throws ServletException, JSONException {
		RevWalk revWalk = new RevWalk(db);
		try {

			Ref headRef = db.getRef(Constants.HEAD);
			if (headRef == null)
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when cherry-picking.", null));
			RevCommit head = revWalk.parseCommit(headRef.getObjectId());

			ObjectId objectId = db.resolve(commitToCherryPick);
			Git git = Git.wrap(db);
			CherryPickResult cherryPickResult = git.cherryPick().include(objectId).call();
			RevCommit newHead = cherryPickResult.getNewHead();

			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_RESULT, cherryPickResult.getStatus().name());
			result.put(GitConstants.KEY_HEAD_UPDATED, !head.equals(newHead));
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when cherry-picking.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when cherry-picking.", e));
		} finally {
			revWalk.close();
		}
	}

	private boolean revert(HttpServletRequest request, HttpServletResponse response, Repository db, String commitToRevert)
			throws ServletException, JSONException {
		RevWalk revWalk = new RevWalk(db);
		try {

			Ref headRef = db.getRef(Constants.HEAD);
			if (headRef == null)
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when reverting.", null));

			ObjectId objectId = db.resolve(commitToRevert);
			Git git = Git.wrap(db);

			RevertCommand revertCommand = git.revert().include(objectId);
			RevCommit revertedCommit = revertCommand.call();

			if (revertedCommit == null) {
				JSONObject result = new JSONObject();
				result.put(GitConstants.KEY_RESULT, "FAILURE"); //$NON-NLS-1$
				OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			}

			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_RESULT, "OK"); //$NON-NLS-1$
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when reverting.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when reverting.", e));
		} finally {
			revWalk.close();
		}
	}

	private boolean sendNotification(HttpServletRequest request, HttpServletResponse response, Repository db, String login, String commit, String url,
			String authorName, String message) throws ServletException, URISyntaxException, IOException, JSONException, CoreException, Exception {
		UserEmailUtil util = UserEmailUtil.getUtil();
		if (!util.isEmailConfigured()) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SMTP server not configured", null));
		}
		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, login, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}
		try {
			if (reviewRequestEmail == null) {
				reviewRequestEmail = new EmailContent(EMAIL_REVIEW_REQUEST_FILE);
			}

			String emailAdress = userInfo.getProperty(UserConstants.EMAIL);

			util.sendEmail(reviewRequestEmail.getTitle(), reviewRequestEmail.getContent().replaceAll(EMAIL_COMMITER_NAME, authorName)
					.replaceAll(EMAIL_URL_LINK, url).replaceAll(EMAIL_COMMIT_MESSAGE, message), emailAdress);

			JSONObject result = new JSONObject();
			result.put(GitConstants.KEY_RESULT, "Email sent");
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "User doesn't exist", null));
		}
	};

	@Override
	protected boolean handlePut(RequestInfo requestInfo) throws ServletException {
		String gitSegment = requestInfo.gitSegment;
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		String filePath = requestInfo.relativePath;
		JSONObject toPut = requestInfo.getJSONRequest();
		try {
			boolean isRoot = "".equals(filePath); //$NON-NLS-1$
			String tagName = toPut.getString(ProtocolConstants.KEY_NAME);
			boolean isTagAnnotated = toPut.has(GitConstants.KEY_ANNOTATED_TAG) ? toPut.getBoolean(GitConstants.KEY_ANNOTATED_TAG) : true;//true by default
			String annotatedTagMessage = toPut.optString(GitConstants.KEY_ANNOTATED_TAG_MESSAGE);
			if (tagName != null) {
				return tag(request, response, db, gitSegment, tagName, isRoot, isTagAnnotated, annotatedTagMessage);
			}
			return false;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when tagging.", e));
		}
	}

	private boolean tag(HttpServletRequest request, HttpServletResponse response, Repository db, String commitId,
		String tagName, boolean isRoot, boolean isTagAnnotated, String annotatedTagMessage)
			throws JSONException, URISyntaxException, ServletException {
		Git git = Git.wrap(db);
		RevWalk walk = new RevWalk(db);
		try {
			ObjectId objectId = db.resolve(commitId);
			RevCommit revCommit = walk.lookupCommit(objectId);
			walk.parseBody(revCommit);

			GitTagHandlerV1.tag(git, revCommit, tagName, isTagAnnotated, annotatedTagMessage);

			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.COMMIT_REFRANGE);
			Commit commit = new Commit(cloneLocation, db, revCommit, null);
			JSONObject result = commit.toJSON();
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when tagging.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when tagging.", e));
		} catch (CoreException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred when tagging.", e));
		} finally {
			walk.dispose();
		}
	}
}
