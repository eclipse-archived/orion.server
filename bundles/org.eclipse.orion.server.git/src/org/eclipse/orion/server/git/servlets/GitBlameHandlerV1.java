/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.objects.Blame;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;

public class GitBlameHandlerV1 extends AbstractGitHandler {

	GitBlameHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;

		try {

			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.BLAME);

			Path Filepath = new Path(requestInfo.filePath.toString());
			if (Filepath.hasTrailingSeparator()) {
				String msg = NLS.bind("Cannot get blame Information on a folder: {0}", requestInfo.filePath.toString());
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}

			Blame blame = new Blame(cloneLocation, requestInfo.db);

			String gitSegment = requestInfo.gitSegment;
			if (!gitSegment.equalsIgnoreCase("HEAD") && !gitSegment.equalsIgnoreCase("master")) {
				ObjectId id = ObjectId.fromString(requestInfo.gitSegment);
				blame.setStartCommit(id);
			}

			String path = requestInfo.relativePath;
			blame.setFilePath(path);
			blame.setBlameLocation(getURI(request));
			doBlame(blame, requestInfo.db);
			OrionServlet.writeJSONResponse(request, response, blame.toJSON(), JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Error generating blame response", e));
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
				RevCommit commit;
				RevCommit prevCommit = null;
				String path;
				String prevPath = null;
				for (int i = 0; i < result.getResultContents().size(); i++) {
					try {
						commit = result.getSourceCommit(i);
						prevCommit = commit;
					} catch (NullPointerException e) {
						commit = prevCommit;
					}

					if (!blame.commitExists(commit)) {
						if (commit != null) {
							blame.addCommit(commit);
						}
					}

					try {
						path = commit.getId().getName();
						prevPath = path;
					} catch (NullPointerException e) {
						path = prevPath;
					}
					blame.addLine(path);
				}
			}
		}
	}

}