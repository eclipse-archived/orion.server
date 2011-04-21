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
import java.util.Collection;
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
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
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

		Collection<Ref> revTags = db.getTags().values();
		RevWalk walk = new RevWalk(db);

		JSONObject result = new JSONObject();
		JSONArray children = new JSONArray();
		for (Ref ref : revTags) {
			RevTag revTag = walk.parseTag(db.resolve(ref.getName()));
			JSONObject tag = new JSONObject();
			result.put(ProtocolConstants.KEY_NAME, revTag.getName());
			children.put(tag);
		}
		result.put(ProtocolConstants.KEY_CHILDREN, children);
		OrionServlet.writeJSONResponse(request, response, result);
		walk.dispose();
		return true;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, CoreException, JGitInternalException, GitAPIException {
		IPath p = new Path(path);
		File gitDir = GitUtils.getGitDir(p);
		Repository db = new FileRepository(gitDir);
		Git git = new Git(db);
		JSONObject toPut = OrionServlet.readJSONRequest(request);
		String tagName = toPut.getString(ProtocolConstants.KEY_NAME);
		String commitId = toPut.getString(GitConstants.KEY_TAG_COMMIT);
		ObjectId objectId = db.resolve(commitId);

		RevWalk walk = new RevWalk(db);
		RevCommit revCommit = walk.lookupCommit(objectId);

		RevTag revTag = tag(git, revCommit, tagName);
		JSONObject result = new JSONObject();
		result.put(ProtocolConstants.KEY_NAME, revTag.getTagName());
		result.put(ProtocolConstants.KEY_CONTENT_LOCATION, OrionServlet.getURI(request));
		OrionServlet.writeJSONResponse(request, response, result);
		walk.dispose();
		return true;
	}

	static RevTag tag(Git git, RevCommit revCommit, String tagName) throws JGitInternalException, GitAPIException {
		return git.tag().setObjectId(revCommit).setName(tagName).call();
	}
}
