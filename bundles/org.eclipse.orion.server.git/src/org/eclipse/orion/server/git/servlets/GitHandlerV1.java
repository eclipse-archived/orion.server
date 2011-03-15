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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.git.GitConstants;

/**
 * A git handler for Orion Git API v 1.0.
 */
public class GitHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<String> cloneHandlerV1;
	private ServletResourceHandler<String> commitHandlerV1;
	private ServletResourceHandler<String> diffHandlerV1;
	private ServletResourceHandler<String> indexHandlerV1;
	private ServletResourceHandler<String> statusHandlerV1;
	private ServletResourceHandler<String> configHandlerV1;

	GitHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		cloneHandlerV1 = new GitCloneHandlerV1(statusHandler);
		commitHandlerV1 = new GitCommitHandlerV1(statusHandler);
		diffHandlerV1 = new GitDiffHandlerV1(statusHandler);
		indexHandlerV1 = new GitIndexHandlerV1(statusHandler);
		statusHandlerV1 = new GitStatusHandlerV1(statusHandler);
		configHandlerV1 = new GitConfigHandlerV1(statusHandler);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String gitPathInfo) throws ServletException {

		String[] infoParts = gitPathInfo.split("\\/", 3); //$NON-NLS-1$

		if (infoParts[1].equals(GitConstants.CLONE_RESOURCE)) {
			return cloneHandlerV1.handleRequest(request, response, infoParts[2]);
		} else if (infoParts[1].equals(GitConstants.COMMIT_RESOURCE)) {
			return commitHandlerV1.handleRequest(request, response, infoParts[2]);
		} else if (infoParts[1].equals(GitConstants.DIFF_RESOURCE)) {
			return diffHandlerV1.handleRequest(request, response, infoParts[2]);
		} else if (infoParts[1].equals(GitConstants.INDEX_RESOURCE)) {
			return indexHandlerV1.handleRequest(request, response, infoParts[2]);
		} else if (infoParts[1].equals(GitConstants.STATUS_RESOURCE)) {
			return statusHandlerV1.handleRequest(request, response, infoParts[2]);
		} else if (infoParts[1].equals(GitConstants.CONFIG_RESOURCE)) {
			return configHandlerV1.handleRequest(request, response, infoParts[2]);
		}
		return false;
	}
}
