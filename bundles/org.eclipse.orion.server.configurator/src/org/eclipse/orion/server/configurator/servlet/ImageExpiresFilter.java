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

import java.util.Arrays;

import java.io.IOException;
import java.util.List;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A filter that adds an expires tag on all images.
 */
public class ImageExpiresFilter implements Filter {

	private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(new String[] {"gif", "jpg", "png", "bmp", "tif"});
	private static final int EXPIRES_AFTER = 1000 * 60 * 60 * 24; // 1 day

	public void init(FilterConfig filterConfig) throws ServletException {
		//nothing to do
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		if (isImage(httpRequest.getPathInfo())) {
			HttpServletResponse httpResponse = (HttpServletResponse) response;
			httpResponse.setDateHeader("Expires", System.currentTimeMillis() + EXPIRES_AFTER);
		}
		chain.doFilter(request, response);
	}

	/**
	 * Returns whether the given request URI represents one of our registered servlets.
	 */
	private boolean isImage(String pathInfo) {
		if (pathInfo == null) {
			return false;
		}

		int dot = pathInfo.lastIndexOf('.');
		if (dot != -1) {
			String extension = pathInfo.substring(dot + 1).toLowerCase();
			if (IMAGE_EXTENSIONS.contains(extension)) {
				return true;
			}
		}
		return false;
	}

	public void destroy() {
		//nothing to do
	}

}
