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
import java.net.URISyntaxException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Tag;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * A handler for Git Tag operation.
 */
public class GitTagHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

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
			}
		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle 'tag' request for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		IPath p = new Path(path);
		File gitDir = GitUtils.getGitDir(p);
		Repository db = new FileRepository(gitDir);
		Git git = new Git(db);
		List<RevTag> revTags = git.tagList().call();
		JSONObject result = new JSONObject();
		JSONArray children = new JSONArray();
		for (RevTag revTag : revTags) {
			Tag tag = new Tag(getURI(request), db, revTag);
			children.put(tag.toJSON());
		}
		result.put(ProtocolConstants.KEY_CHILDREN, children);
		OrionServlet.writeJSONResponse(request, response, result);
		return true;
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
			Tag tag = new Tag(getURI(request), db, revTag);
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
}
