/*******************************************************************************
 * Copyright (c) 2010,2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.file;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Adds headers giving the edit-server and edit-token for resources in the file system.
 */
public class EditSupportFilter implements Filter {

	private static final String FILE_SERVLET_ALIAS = "/file"; //$NON-NLS-1$

	public void init(FilterConfig filterConfig) throws ServletException {
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		String requestURI = httpRequest.getRequestURI();

		if (httpRequest.getServletPath().equals(FILE_SERVLET_ALIAS)) {
			String host = getHost(httpRequest);
			httpResponse.addHeader("X-Edit-Server", host + httpRequest.getContextPath() + "/edit/edit.html#"); //$NON-NLS-1$ //$NON-NLS-2$
			httpResponse.addHeader("X-Edit-Token", requestURI); //$NON-NLS-1$
		} else {
			String selfHostPath = System.getProperty("org.eclipse.orion.server.core.selfHostPath"); //$NON-NLS-1$
			if (selfHostPath != null) {
				String host = getHost(httpRequest);
				httpResponse.addHeader("X-Edit-Server", host + httpRequest.getContextPath() + "/edit/edit.html#"); //$NON-NLS-1$ //$NON-NLS-2$
				httpResponse.addHeader("X-Edit-Token", httpRequest.getContextPath() + FILE_SERVLET_ALIAS + selfHostPath + requestURI); //$NON-NLS-1$
			}
		}

		chain.doFilter(request, response);
	}

	private static String getHost(ServletRequest request) {
		return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort(); //$NON-NLS-1$ //$NON-NLS-2$;
	}

	public void destroy() {
	}
}