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
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.jobs.ListTagsJob;
import org.eclipse.orion.server.git.objects.Tag;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A handler for Git Tag operation.
 */
public class GitTagHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;
	private static int PAGE_SIZE = 50;

	GitTagHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, path);
				case POST :
					return handlePost(request, response, path);
				case DELETE :
					return handleDelete(request, response, path);
			}
		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle 'tag' request for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		IPath p = new Path(path);
		if (p.segment(1).equals("file")) { //$NON-NLS-1$
			String tagName = p.segment(0);
			p = p.removeFirstSegments(1);
			File gitDir = GitUtils.getGitDir(p);
			Repository db = new FileRepository(gitDir);
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.TAG);

			Map<String, Ref> refList = db.getRefDatabase().getRefs(Constants.R_TAGS);
			Ref ref = refList.get(tagName);
			if (ref != null) {
				RevWalk revWalk = new RevWalk(db);
				try {
					RevTag revTag = revWalk.parseTag(ref.getObjectId());
					Tag tag = new Tag(cloneLocation, db, revTag);
					OrionServlet.writeJSONResponse(request, response, tag.toJSON());
					return true;
				} finally {
					revWalk.release();
				}
			} else {
				String msg = NLS.bind("Tag not found: {0}", tagName);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		} else {
			ListTagsJob job;
			String commits = request.getParameter(GitConstants.KEY_TAG_COMMITS);
			int commitsNumber = commits == null ? 0 : Integer.parseInt(commits);
			String page = request.getParameter("page");
			if (page != null) {
				int pageNo = Integer.parseInt(page);
				int pageSize = request.getParameter("pageSize") == null ? PAGE_SIZE : Integer.parseInt(request.getParameter("pageSize"));
				job = new ListTagsJob(TaskJobHandler.getUserId(request), p, BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.TAG_LIST), commitsNumber, pageNo, pageSize, request.getRequestURI());
			} else {
				job = new ListTagsJob(TaskJobHandler.getUserId(request), p, BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.TAG_LIST), commitsNumber);
			}
			return TaskJobHandler.handleTaskJob(request, response, job, statusHandler);
		}
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, JSONException, ServletException, URISyntaxException {
		IPath p = new Path(path);
		File gitDir = GitUtils.getGitDir(p);
		Repository db = new FileRepository(gitDir);
		Git git = new Git(db);
		RevWalk walk = new RevWalk(db);
		try {
			JSONObject toPut = OrionServlet.readJSONRequest(request);
			String tagName = toPut.getString(ProtocolConstants.KEY_NAME);
			String commitId = toPut.getString(GitConstants.KEY_TAG_COMMIT);
			ObjectId objectId = db.resolve(commitId);
			RevCommit revCommit = walk.lookupCommit(objectId);

			RevTag revTag = tag(git, revCommit, tagName);
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.TAG_LIST);
			Tag tag = new Tag(cloneLocation, db, revTag);
			OrionServlet.writeJSONResponse(request, response, tag.toJSON());
			return true;
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when tagging.", e));
		} catch (GitAPIException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when tagging.", e));
		} catch (JGitInternalException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when tagging.", e));
		} finally {
			walk.dispose();
		}
	}

	static RevTag tag(Git git, RevCommit revCommit, String tagName) throws JGitInternalException, GitAPIException {
		return git.tag().setObjectId(revCommit).setName(tagName).call();
	}

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, JSONException, ServletException, URISyntaxException {
		IPath p = new Path(path);
		File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
		Repository db = new FileRepository(gitDir);
		Git git = new Git(db);
		try {
			git.tagDelete().setTags(p.segment(0)).call();
			return true;
		} catch (JGitInternalException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when removing a tag.", e));
		}
	}
}
