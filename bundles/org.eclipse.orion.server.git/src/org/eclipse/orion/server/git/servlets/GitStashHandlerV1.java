package org.eclipse.orion.server.git.servlets;

import java.util.Collection;
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

			String operationType = requestPayload.getString(GitConstants.KEY_STASH_POST_OPERATION_TYPE);

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
					stashCreate.setIndexMessage(indexMessage); //
				if (workingDirectoryMessage != null)
					stashCreate.setWorkingDirectoryMessage(workingDirectoryMessage); //
				stashCreate.call();

			}
		} catch (InvalidRefNameException e) {
			if (e.getMessage().contains(GitConstants.STASH_TOP_REF)) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, GitConstants.STASH_LIST_EMPTY_MESSAGE, e));
			} else {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured for stash command.", e));
			}
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured for stash command.", e));
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

			StashListCommand stashList = new StashListCommand(db);
			Collection<RevCommit> stashedItems = stashList.call();
			JSONArray result = new JSONArray();

			for (RevCommit item : stashedItems) {
				Commit commit = new Commit(null, db, item, null);
				result.put(commit.toJSON());

			}

			OrionServlet.writeJSONResponse(request, response, result);
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured while getting stash list.", e));
		}
		return true;
	}

	/**
	 * Deletes stash entries with provided ids or if none are given drops whole stash
	 */
	@Override
	protected boolean handleDelete(RequestInfo requestInfo) throws ServletException {

		JSONObject requestPayload = requestInfo.getJSONRequest();
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		int dropStashRef = requestPayload.optInt(GitConstants.KEY_STASH_DROP_REF, 0);
		boolean dropAll = requestPayload.optBoolean(GitConstants.KEY_STASH_DROP_ALL, false);

		try {

			StashDropCommand dropCommand = new StashDropCommand(db);
			dropCommand.setAll(dropAll);
			dropCommand.setStashRef(dropStashRef);
			dropCommand.call();

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured while dropping stash.", e));
		}
		return true;
	}
}
