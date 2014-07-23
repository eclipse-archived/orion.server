package org.eclipse.orion.server.git.servlets;

import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Commit;
import org.eclipse.orion.server.git.objects.Stash;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONObject;

public class GitStashHandlerV1 extends AbstractGitHandler {

	GitStashHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	/**
	 * Creates a new stash entry from changes in local directory
	 */
	@Override
	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {

		JSONObject requestPayload = requestInfo.getJSONRequest();
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		try {

			String operationType = requestPayload.optString(GitConstants.KEY_STASH_POST_OPERATION_TYPE, null);

			if (GitConstants.KEY_STASH_APPLY_COMMAND.equals(operationType)) {

				Git git = new Git(db);

				String stashRef = requestPayload.optString(GitConstants.KEY_STASH_REF, null);
				boolean applyIndex = requestPayload.optBoolean(GitConstants.KEY_STASH_APPLY_INDEX, false);
				boolean applyUntracked = requestPayload.optBoolean(GitConstants.KEY_STASH_APPLY_UNTRACKED, false);

				StashApplyCommand stashApply = git.stashApply();
				stashApply.setApplyIndex(applyIndex);
				if (stashRef != null)
					stashApply.setStashRef(stashRef);
				stashApply.setApplyUntracked(applyUntracked);
				stashApply.call();

			} else if (GitConstants.KEY_STASH_CREATE_COMMAND.equals(operationType)) {

				Git git = new Git(db);

				boolean includeUntracked = requestPayload.optBoolean(GitConstants.KEY_STASH_INCLUDE_UNTRACKED, true);
				String indexMessage = requestPayload.optString(GitConstants.KEY_STASH_INDEX_MESSAGE, null);
				String workingDirectoryMessage = requestPayload.optString(GitConstants.KEY_STASH_WORKING_DIRECTORY_MESSAGE, null);

				PersonIdent personId = new PersonIdent(db);

				StashCreateCommand stashCreate = git.stashCreate();
				stashCreate.setPerson(personId);
				stashCreate.setIncludeUntracked(includeUntracked);
				if (indexMessage != null)
					stashCreate.setIndexMessage(indexMessage);
				if (workingDirectoryMessage != null)
					stashCreate.setWorkingDirectoryMessage(workingDirectoryMessage);
				stashCreate.call();

			} else {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, GitConstants.STASH_ILLEGAL_OPERATION_MESSAGE, /* not needed */null));
			}
		} catch (InvalidRefNameException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, GitConstants.STASH_ILLEGAL_REF_MESSAGE, e));
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, GitConstants.STASH_ERROR_MESSAGE, e));
		}
		return true;
	}

	/**
	 * Returns list of stash entries
	 */
	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {

		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		try {

			int pageNo = request.getParameter(GitConstants.KEY_STASH_LIST_PAGE) != null ? new Integer(request.getParameter(GitConstants.KEY_STASH_LIST_PAGE)).intValue() : 1;
			int pageSize = request.getParameter(GitConstants.KEY_STASH_LIST_PAGE_SIZE) != null ? new Integer(request.getParameter(GitConstants.KEY_STASH_LIST_PAGE_SIZE)).intValue() : GitConstants.STASH_DEFAULT_PAGE_SIZE;

			if (pageNo <= 0 || pageSize <= 0) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, GitConstants.STASH_LIST_ILLEGAL_PARAMS_MESSAGE, /* not needed */null));
			}

			StashListCommand stashList = new StashListCommand(db);
			Collection<RevCommit> stashedRefsCollection = stashList.call();
			List<RevCommit> stashedRefsList = new ArrayList<RevCommit>(stashedRefsCollection);
			Collections.sort(stashedRefsList, Stash.COMPARATOR);

			List<Commit> stashCommits = new ArrayList<Commit>();

			for (RevCommit revCommit : stashedRefsList) {
				stashCommits.add(new Commit(/* not needed */null, db, revCommit, /* not needed */null));
			}

			JSONArray result = GitUtils.paginate(stashCommits, pageNo, pageSize);

			OrionServlet.writeJSONResponse(request, response, result);
		} catch (NumberFormatException e) {

			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, GitConstants.STASH_LIST_ILLEGAL_PARAMS_MESSAGE, e));

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, GitConstants.STASH_LIST_ERROR_MESSAGE, e));
		}
		return true;
	}

	/**
	 * Deletes stash entry with provided stash ref number. If none is provided then it defaults to first one (0).
	 * If -1 is provided then drops all stash entries.
	 */
	@Override
	protected boolean handleDelete(RequestInfo requestInfo) throws ServletException {

		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		try {

			int dropStashRef = Integer.parseInt(request.getParameter(GitConstants.KEY_STASH_DROP_REF));
			StashDropCommand dropCommand = new StashDropCommand(db);
			if (dropStashRef == -1)
				dropCommand.setAll(true);
			else if (dropStashRef > -1) {
				dropCommand.setStashRef(dropStashRef);
			} else {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, GitConstants.STASH_DROP_ILLEGAL_REF_MESSAGE, /* not needed */null));
			}
			dropCommand.call();

		} catch (NumberFormatException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, GitConstants.STASH_DROP_ILLEGAL_REF_MESSAGE, /* not needed */null));
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, GitConstants.STASH_DROP_ERROR_MESSAGE, e));
		}
		return true;
	}
}
