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
 * A user handler suitable for use by a generic HTTP client, such as a web
 * browser.
 */
public class GitHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

	private ServletResourceHandler<String> diffHandlerV1;

	GitHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
		diffHandlerV1 = new GitDiffHandlerV1(statusHandler);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, String gitPathInfo)
			throws ServletException {

		String[] infoParts = gitPathInfo.split("\\/", 3);

		if (infoParts[1].equals(GitConstants.DIFF_COMMAND)) {
			diffHandlerV1.handleRequest(request, response, infoParts[2]);
			return true;
		}

		return false;
	}
}
