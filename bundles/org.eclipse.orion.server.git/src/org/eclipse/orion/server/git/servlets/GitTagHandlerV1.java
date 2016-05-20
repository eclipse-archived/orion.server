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

import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.jobs.ListTagsJob;
import org.eclipse.orion.server.git.objects.Tag;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;

/**
 * A handler for Git Tag operation.
 */
public class GitTagHandlerV1 extends AbstractGitHandler {

	private static int PAGE_SIZE = 50;

	GitTagHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {
		String gitSegment = requestInfo.gitSegment;
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		IPath filePath = requestInfo.filePath;
		try {
			if (gitSegment != null) {
				String tagName = gitSegment;
				URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.TAG);

				Ref ref = db.getRefDatabase().getRef(Constants.R_TAGS + tagName);
				if (ref != null) {
					Tag tag = new Tag(cloneLocation, db, ref);
					OrionServlet.writeJSONResponse(request, response, tag.toJSON(), JsonURIUnqualificationStrategy.ALL_NO_GIT);
					return true;
				} else {
					String msg = NLS.bind("Tag not found: {0}", tagName);
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
				}
			} else {
				ListTagsJob job;
				String commits = request.getParameter(GitConstants.KEY_TAG_COMMITS);
				int commitsNumber = commits == null ? 0 : Integer.parseInt(commits);
				String nameFilter = request.getParameter("filter"); //$NON-NLS-1$
				String page = request.getParameter("page"); //$NON-NLS-1$
				if (page != null) {
					int pageNo = Integer.parseInt(page);
					int pageSize = request.getParameter("pageSize") == null ? PAGE_SIZE : Integer.parseInt(request.getParameter("pageSize")); //$NON-NLS-1$ //$NON-NLS-2$
					job = new ListTagsJob(TaskJobHandler.getUserId(request), filePath, BaseToCloneConverter.getCloneLocation(getURI(request),
							BaseToCloneConverter.TAG_LIST), commitsNumber, pageNo, pageSize, request.getRequestURI(), nameFilter);
				} else {
					job = new ListTagsJob(TaskJobHandler.getUserId(request), filePath, BaseToCloneConverter.getCloneLocation(getURI(request),
							BaseToCloneConverter.TAG_LIST), commitsNumber, nameFilter);
				}
				return TaskJobHandler.handleTaskJob(request, response, job, statusHandler, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			}
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for a tag.", e));
		}
	}

	@Override
	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		Git git = requestInfo.git;
		JSONObject toPut = requestInfo.getJSONRequest();
		RevWalk walk = new RevWalk(db);
		try {
			String tagName = toPut.getString(ProtocolConstants.KEY_NAME);
			String commitId = toPut.getString(GitConstants.KEY_TAG_COMMIT);
			boolean isTagAnnotated = toPut.has(GitConstants.KEY_ANNOTATED_TAG) ? toPut.getBoolean(GitConstants.KEY_ANNOTATED_TAG) : true;//true by default
			String annotatedTagMessage = toPut.optString(GitConstants.KEY_ANNOTATED_TAG_MESSAGE);
			ObjectId objectId = db.resolve(commitId);
			RevCommit revCommit = walk.lookupCommit(objectId);

			Ref ref = tag(git, revCommit, tagName, isTagAnnotated, annotatedTagMessage);
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.TAG_LIST);
			Tag tag = new Tag(cloneLocation, db, ref);
			OrionServlet.writeJSONResponse(request, response, tag.toJSON(), JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occurred when tagging.", e));
		} finally {
			walk.dispose();
		}
	}

	static Ref tag(Git git, RevCommit revCommit, String tagName, boolean isTagAnnotated, String annotatedTagMessage) throws GitAPIException {
		TagCommand tag = git.tag();
		return isTagAnnotated ? tag.setObjectId(revCommit).setName(tagName).setAnnotated(isTagAnnotated).setMessage(annotatedTagMessage).call()
				: tag.setObjectId(revCommit).setName(tagName).setAnnotated(isTagAnnotated).call();
	}

	@Override
	protected boolean handleDelete(RequestInfo requestInfo) throws ServletException {
		String gitSegment = requestInfo.gitSegment;
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Git git = requestInfo.git;
		if (gitSegment != null) {
			try {
				git.tagDelete().setTags(gitSegment).call();
				return true;
			} catch (GitAPIException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"An error occured when removing a tag.", e));
			}
		} else {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
					"Tag deletion aborted: no tag name provided.", null));
		}
	}
}
