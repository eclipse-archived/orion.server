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
package org.eclipse.orion.server.configurator.servlet;

import java.io.IOException;
import java.util.HashSet;
import java.util.StringTokenizer;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

/**
 * A filter that gzips all contents except excluded extensions and server-side includes.
 */
public class ExcludedExtensionGzipFilter extends org.eclipse.jetty.servlets.GzipFilter {
	static final String INCLUDE_REQUEST_URI_ATTRIBUTE = "javax.servlet.include.request_uri"; //$NON-NLS-1$

	private HashSet<String> excludedExtensions = new HashSet<String>();

	public void init(FilterConfig filterConfig) throws ServletException {
		super.init(filterConfig);
		String excludedExtensionsParam = filterConfig.getInitParameter("excludedExtensions");
		if (excludedExtensionsParam != null) {
			StringTokenizer tokenizer = new StringTokenizer(excludedExtensionsParam, ",", false);
			while (tokenizer.hasMoreTokens()) {
				excludedExtensions.add(tokenizer.nextToken().trim());
			}
		}
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		// do not use the filter if this is a server-side include, JSP, or image file
		if (httpRequest.getAttribute(INCLUDE_REQUEST_URI_ATTRIBUTE) == null && (!isExcludedFileExtension(httpRequest.getPathInfo()))) {
			super.doFilter(request, response, chain);
		} else {
			chain.doFilter(request, response);
		}
	}

	private boolean isExcludedFileExtension(String pathInfo) {
		if (pathInfo == null || excludedExtensions.isEmpty()) {
			return false;
		}

		int dot = pathInfo.lastIndexOf('.');
		if (dot != -1) {
			String extension = pathInfo.substring(dot + 1).toLowerCase();
			if (excludedExtensions.contains(extension)) {
				return true;
			}
		}
		return false;
	}
}
