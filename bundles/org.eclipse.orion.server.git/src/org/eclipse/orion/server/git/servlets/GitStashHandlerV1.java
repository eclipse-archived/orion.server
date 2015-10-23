/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.net.URI;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.StashCreateCommand;
import org.eclipse.jgit.api.StashDropCommand;
import org.eclipse.jgit.api.StashListCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.jobs.StashApplyCommand;
import org.eclipse.orion.server.git.objects.StashPage;
import org.eclipse.orion.server.git.objects.StashRef;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;

public class GitStashHandlerV1 extends AbstractGitHandler {

	private final static int PAGE_SIZE = 50;

	GitStashHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	/**
	 * Helper method extracting the StashRef for the stash commit rev
	 * 
	 * @param git
	 *            Git handler object
	 * @param stashRev
	 *            Git commit name
	 * @return StashRef wrapper object or <code>null</code> if the given commit is not present in the stash
	 * @throws InvalidRefNameException
	 * @throws GitAPIException
	 */
	protected StashRef getStashRef(Git git, String stashRev) throws InvalidRefNameException, GitAPIException {

		if (stashRev == null)
			return null;

		StashListCommand stashList = git.stashList();
		Collection<RevCommit> stashedRefsCollection = stashList.call();

		int k = 0;
		for (RevCommit rev : stashedRefsCollection)
			if (stashRev.equals(rev.getName()))
				return new StashRef(k);
			else
				++k;

		return null;
	}

	/**
	 * Helper method returning whether the stash is empty or not
	 * 
	 * @param git
	 *            Git handler object
	 * @return <code>true</code> iff the git stash is empty
	 * @throws InvalidRefNameException
	 * @throws GitAPIException
	 */
	protected boolean isStashEmpty(Git git) throws InvalidRefNameException, GitAPIException {
		StashListCommand stashList = git.stashList();
		Collection<RevCommit> stashedRefsCollection = stashList.call();
		return stashedRefsCollection.isEmpty();
	}

	@Override
	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {

		JSONObject requestPayload = requestInfo.getJSONRequest();
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		String indexMessage = requestPayload.optString(GitConstants.KEY_STASH_INDEX_MESSAGE);
		String workingDirectoryMessage = requestPayload.optString(GitConstants.KEY_STASH_WORKING_DIRECTORY_MESSAGE);
		boolean includeUntracked = requestPayload.optBoolean(GitConstants.KEY_STASH_INCLUDE_UNTRACKED, false);

		try {

			Git git = Git.wrap(db);
			StashCreateCommand stashCreate = git.stashCreate();
			stashCreate.setPerson(new PersonIdent(db));
			stashCreate.setIncludeUntracked(includeUntracked);

			if (!indexMessage.isEmpty())
				stashCreate.setIndexMessage(indexMessage);

			if (!workingDirectoryMessage.isEmpty())
				stashCreate.setWorkingDirectoryMessage(workingDirectoryMessage);

			stashCreate.call();
			return true;

		} catch (Exception ex) {
			String msg = "An error occured for stash command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}

	@Override
	protected boolean handlePut(RequestInfo requestInfo) throws ServletException {

		JSONObject requestPayload = requestInfo.getJSONRequest();
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		/* gitapi/stash/<stashRev>/file/(...) */
		String stashRev = requestInfo.gitSegment;

		boolean applyIndex = requestPayload.optBoolean(GitConstants.KEY_STASH_APPLY_INDEX, true);
		boolean applyUntracked = requestPayload.optBoolean(GitConstants.KEY_STASH_APPLY_UNTRACKED, true);

		try {

			Git git = Git.wrap(db);

			/* check for empty stash */
			if (isStashEmpty(git)) {
				String msg = "Failed to apply stashed changes due to an empty stash.";
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.WARNING, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}

			StashApplyCommand applyCommand = new StashApplyCommand(db);

			if (stashRev != null) {

				StashRef stashRef = getStashRef(git, stashRev);
				if (stashRef == null) {
					String msg = NLS.bind("Invalid stash reference {0}.", stashRev);
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				}

				applyCommand.setStashRef(stashRef.getStringRef());
				applyCommand.setApplyUntracked(applyUntracked);
				applyCommand.setApplyIndex(applyIndex);
				applyCommand.call();

			} else {

				/* git stash pop */
				applyCommand.setApplyUntracked(applyUntracked);
				applyCommand.setApplyIndex(applyIndex);
				applyCommand.call();

				StashDropCommand dropCommand = git.stashDrop();
				dropCommand.setAll(false);
				dropCommand.call();

			}

			return true;

		} catch (Exception ex) {
			String msg = "An error occured for stash command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {

		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		int page = request.getParameter("page") != null ? new Integer(request.getParameter("page")).intValue() : 1; //$NON-NLS-1$ //$NON-NLS-2$
		int pageSize = request.getParameter("pageSize") != null ? new Integer(request.getParameter("pageSize")).intValue() : PAGE_SIZE; //$NON-NLS-1$ //$NON-NLS-2$
		String messageFilter = request.getParameter("filter"); //$NON-NLS-1$
		try {

			URI baseLocation = getURI(request);
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.COMMIT);

			Git git = Git.wrap(db);
			StashListCommand stashList = git.stashList();
			Collection<RevCommit> stashedRefsCollection = stashList.call();

			StashPage stashPage = new StashPage(cloneLocation, db, stashedRefsCollection, page, pageSize, messageFilter);
			OrionServlet.writeJSONResponse(request, response, stashPage.toJSON());
			return true;

		} catch (Exception ex) {
			String msg = "An error occured for stash command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}

	@Override
	protected boolean handleDelete(RequestInfo requestInfo) throws ServletException {

		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		/* gitapi/stash/<stashRev>/file/(...) */
		String stashRev = requestInfo.gitSegment;

		try {

			Git git = Git.wrap(db);

			/* check for empty stash */
			if (isStashEmpty(git)) {
				String msg = "Failed to drop stashed changes due to an empty stash.";
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.WARNING, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}

			StashDropCommand dropCommand = git.stashDrop();

			if (stashRev != null) {

				StashRef stashRef = getStashRef(git, stashRev);
				if (stashRef == null) {
					String msg = NLS.bind("Invalid stash reference {0}.", stashRev);
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				}

				dropCommand.setStashRef(stashRef.getRef());

			} else
				dropCommand.setAll(true);

			dropCommand.call();
			return true;

		} catch (Exception ex) {
			String msg = "An error occured for stash command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}
}
