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
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.jobs.ListBranchesJob;
import org.eclipse.orion.server.git.objects.Branch;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;

public class GitBranchHandlerV1 extends AbstractGitHandler {

	public static int PAGE_SIZE = 50;

	GitBranchHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
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
			if (gitSegment == null) {
				// branch list: expected path /git/branch/file/{filePath}
				ListBranchesJob job;
				String nameFilter = request.getParameter("filter");
				String commits = request.getParameter(GitConstants.KEY_TAG_COMMITS);
				int commitsNumber = commits == null ? 0 : Integer.parseInt(commits);
				String page = request.getParameter("page"); //$NON-NLS-1$
				if (page != null) {
					int pageNo = Integer.parseInt(page);
					int pageSize = request.getParameter("pageSize") == null ? PAGE_SIZE : Integer.parseInt(request.getParameter("pageSize")); //$NON-NLS-1$ //$NON-NLS-2$
					job = new ListBranchesJob(TaskJobHandler.getUserId(request), filePath, BaseToCloneConverter.getCloneLocation(getURI(request),
							BaseToCloneConverter.BRANCH_LIST), commitsNumber, pageNo, pageSize, request.getRequestURI(), nameFilter);
				} else {
					job = new ListBranchesJob(TaskJobHandler.getUserId(request), filePath, BaseToCloneConverter.getCloneLocation(getURI(request),
							BaseToCloneConverter.BRANCH_LIST), commitsNumber);
				}
				return TaskJobHandler.handleTaskJob(request, response, job, statusHandler, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			}
			// branch details: expected path /git/branch/{name}/file/{filePath}
			List<Ref> branches = Git.wrap(db).branchList().call();
			JSONObject result = null;
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.BRANCH);
			for (Ref ref : branches) {
				if (Repository.shortenRefName(ref.getName()).equals(gitSegment)) {
					result = new Branch(cloneLocation, db, ref).toJSON();
					break;
				}
			}
			if (result == null) {
				String msg = NLS.bind("Branch {0} not found", gitSegment);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;
		} catch (Exception e) {
			final ServerStatus error = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for a branch.", e);
			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}
	}

	@Override
	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {
		String gitSegment = requestInfo.gitSegment;
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		IPath filePath = requestInfo.filePath;
		JSONObject toCreate = requestInfo.getJSONRequest();
		try {
			if (gitSegment == null) {
				// expected path /gitapi/branch/file/{filePath}
				String branchName = toCreate.optString(ProtocolConstants.KEY_NAME, null);
				String startPoint = toCreate.optString(GitConstants.KEY_BRANCH_NAME, null);

				if (branchName == null || branchName.isEmpty()) {
					if (startPoint == null || startPoint.isEmpty())
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
								"Branch name must be provided", null));
					else {
						String shortName = Repository.shortenRefName(startPoint);
						branchName = shortName.substring(shortName.indexOf("/") + 1); //$NON-NLS-1$
					}
				}

				CreateBranchCommand cc = Git.wrap(db).branchCreate();
				cc.setName(branchName);

				if (startPoint != null && !startPoint.isEmpty()) {
					cc.setStartPoint(startPoint);
					cc.setUpstreamMode(SetupUpstreamMode.TRACK);
				}

				Ref ref = cc.call();

				URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.BRANCH_LIST);
				JSONObject result = new Branch(cloneLocation, db, ref).toJSON();
				OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
				response.setHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
				response.setStatus(HttpServletResponse.SC_CREATED);
				return true;
			}
			String msg = NLS.bind("Failed to create a branch for {0}", filePath); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		} catch (RefAlreadyExistsException e){
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CONFLICT,
					"Ref already exists", new Exception(e.getMessage()+", please use another branch name")));
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when creating a branch.", e));
		}
	}

	@Override
	protected boolean handleDelete(RequestInfo requestInfo) throws ServletException {
		String gitSegment = requestInfo.gitSegment;
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Git git = requestInfo.git;
		try {
			if (gitSegment != null) {
				DeleteBranchCommand cc = git.branchDelete();
				cc.setBranchNames(gitSegment);
				// TODO: the force flag should be passed in the API call
				cc.setForce(true);
				cc.call();

				// TODO: do something with the result
				return true;
			}
			return false;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when removing a branch.", e));
		}
	}
}
