/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Blame;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONObject;

public class GitBlameHandlerV1 extends AbstractGitHandler {

	GitBlameHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {
		try {

			HttpServletRequest request = requestInfo.request;
			HttpServletResponse response = requestInfo.response;

			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.BLAME);

			String path = requestInfo.relativePath;
			Blame blame = new Blame(cloneLocation, requestInfo.db);

			if (path.length() != 0) {
				// check that path isnt for a folder
				String file = path.substring(path.length() - 1);
				if (!file.endsWith("/") && !file.endsWith("\"")) {
					blame.setFilePath(path);
					blame.setBlameLocation(getURI(request));
				}
			}

			doBlame(blame, requestInfo.db);
			OrionServlet.writeJSONResponse(request, response, blame.toJSON());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {
		try {

			HttpServletRequest request = requestInfo.request;
			HttpServletResponse response = requestInfo.response;

			JSONObject obj = requestInfo.getJSONRequest();
			String commitString = obj.getString(GitConstants.KEY_COMMIT);
			ObjectId id = ObjectId.fromString(commitString);

			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.BLAME);

			String path = requestInfo.relativePath;
			Blame blame = new Blame(cloneLocation, requestInfo.db);

			if (path.length() != 0) {
				// check that path isnt for a folder
				String file = path.substring(path.length() - 1);
				if (!file.endsWith("/") && !file.endsWith("\"")) {
					blame.setFilePath(path);
					blame.setBlameLocation(getURI(request));
				}
			}

			blame.setStartCommit(id);
			doBlame(blame, requestInfo.db);
			OrionServlet.writeJSONResponse(request, response, blame.toJSON());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void doBlame(Blame blame, Repository db) throws GitAPIException, IOException {
		String filePath = blame.getFilePath();

		if (db != null && filePath != null) {

			BlameCommand blameCommand = new BlameCommand(db);
			blameCommand.setFilePath(filePath);
			blameCommand.setFollowFileRenames(true);
			blameCommand.setTextComparator(RawTextComparator.WS_IGNORE_ALL);

			if (blame.getStartCommit() != null) {
				blameCommand.setStartCommit(blame.getStartCommit());
			}
			BlameResult result;

			try {
				result = blameCommand.call();
			} catch (Exception e1) {
				return;
			}
			if (result != null) {
				blame.clearLines();
				RevCommit commitPath;
				String path;
				for (int i = 0; i < result.getResultContents().size(); i++) {
					commitPath = result.getSourceCommit(i);
					path = commitPath.getId().getName();
					blame.addLine(path);
				}
			}
		}
	}

}