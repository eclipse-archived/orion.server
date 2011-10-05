/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.configurator.servlet;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

/**
 * A filter that forwards directory access to the index.html for that directory.
 */
public class WelcomeFileFilter implements Filter {

	private static final String WELCOME_FILE_NAME = "index.html";//$NON-NLS-1$
	private static final List<String> SERVLET_PATHS = Arrays.asList("/auth2", "/login", "/hosted", "/file", "/workspace/", "/filesystems", "/prefs", "/filesearch", "/git", "/users", "/site", "/xfer", "/help"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$

	public void init(FilterConfig filterConfig) throws ServletException {
		//nothing to do
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String requestPath = httpRequest.getServletPath() + (httpRequest.getPathInfo() == null ? "" : httpRequest.getPathInfo());
		// Only alter directories that aren't part of our servlets
		if (requestPath.endsWith("/") && !isServletPath(requestPath)) { //$NON-NLS-1$
			request.getRequestDispatcher(requestPath + WELCOME_FILE_NAME).forward(request, response);
			return;
		}
		chain.doFilter(request, response);
	}

	/**
	 * Returns whether the given request URI represents one of our registered servlets.
	 */
	private boolean isServletPath(String requestURI) {
		for (String prefix : SERVLET_PATHS)
			if (requestURI.startsWith(prefix))
				return true;
		return false;
	}

	public void destroy() {
		//nothing to do
	}

}
