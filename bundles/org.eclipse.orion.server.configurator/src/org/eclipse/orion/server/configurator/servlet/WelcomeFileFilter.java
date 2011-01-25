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
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

/**
 * A filter that forwards directory access to the index.html for that directory.
 */
public class WelcomeFileFilter implements Filter {

	private static final String WELCOME_FILE_NAME = "index.html";//$NON-NLS-1$

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		//nothing to do
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String requestURI = httpRequest.getRequestURI();
		if (requestURI.endsWith("/")) { //$NON-NLS-1$
			request.getRequestDispatcher(requestURI + WELCOME_FILE_NAME).forward(request, response);
			return;
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		//nothing to do
	}

}
