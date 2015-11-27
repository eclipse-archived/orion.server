/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import java.io.IOException;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * A filter that forwards directory access to the index.html for that directory.
 */
public class WelcomeFileFilter implements Filter {

	private static final String WELCOME_FILE_NAME = "index.html";//$NON-NLS-1$
	private final List<String> includes = new ArrayList<String>();
	private final List<String> excludes = new ArrayList<String>();

	public void init(FilterConfig filterConfig) throws ServletException {
		String includesParameter = filterConfig.getInitParameter("includes"); //$NON-NLS-1$
		if (includesParameter != null) {
			StringTokenizer tokenizer = new StringTokenizer(includesParameter, ",", false); //$NON-NLS-1$
			while (tokenizer.hasMoreTokens()) {
				String token = tokenizer.nextToken().trim();
				includes.add(token);
			}
		}

		String excludesParameter = filterConfig.getInitParameter("excludes"); //$NON-NLS-1$
		if (excludesParameter != null) {
			StringTokenizer tokenizer = new StringTokenizer(excludesParameter, ",", false); //$NON-NLS-1$
			while (tokenizer.hasMoreTokens()) {
				String token = tokenizer.nextToken().trim();
				excludes.add(token);
			}
		}
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		final HttpServletRequest httpRequest = (HttpServletRequest) request;
		final String requestPath = httpRequest.getServletPath() + (httpRequest.getPathInfo() == null ? "" : httpRequest.getPathInfo()); //$NON-NLS-1$
		// Only alter directories that aren't part of our servlets
		if (requestPath.endsWith("/") && isIncluded(requestPath) && !isExcluded(requestPath)) { //$NON-NLS-1$
			response = new HttpServletResponseWrapper((HttpServletResponse) response) {

				private boolean handleWelcomeFile(int sc) {
					if (sc == SC_NOT_FOUND || sc == SC_FORBIDDEN) {
						try {
							httpRequest.getRequestDispatcher(requestPath + WELCOME_FILE_NAME).forward(httpRequest, getResponse());
							return true;
						} catch (Exception e) {
							// fall through
						}
					}
					return false;
				}

				public void sendError(int sc) throws IOException {
					if (!handleWelcomeFile(sc)) {
						super.sendError(sc);
					}
				}

				public void sendError(int sc, String msg) throws IOException {
					if (!handleWelcomeFile(sc)) {
						super.sendError(sc, msg);
					}
				}

				public void setContentLength(int len) {
					if (len == 0) {
						handleWelcomeFile(SC_NOT_FOUND);
					} else
						super.setContentLength(len);
				}

				public void setStatus(int sc) {
					if (!handleWelcomeFile(sc)) {
						super.setStatus(sc);
					}
				}

				/**@deprecated*/
				public void setStatus(int sc, String sm) {
					if (!handleWelcomeFile(sc)) {
						super.setStatus(sc, sm);
					}
				}
			};
		}
		chain.doFilter(request, response);
	}

	private boolean isIncluded(String requestPath) {
		return includes.isEmpty() || includes.contains(requestPath);
	}

	private boolean isExcluded(String requestPath) {
		return excludes.contains(requestPath);
	}

	public void destroy() {
		//nothing to do
	}

}
