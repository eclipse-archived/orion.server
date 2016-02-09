/*******************************************************************************
 * Copyright (c) 2011, 2016 IBM Corporation and others.
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
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.core.ProtocolConstants;

/**
 * A filter that adds cache-control information.
 */
public class CacheFilter implements Filter {
	private static final Pattern maxAgePattern = Pattern.compile("^max-age *= *(\\d+)$");

	private String directives;
	private int maxAge = -1;
	private Pattern includeMatches;
	private Pattern excludeMatches;

	public void init(FilterConfig filterConfig) throws ServletException {
		directives = filterConfig.getInitParameter("directives");
		if (directives == null) {
			throw new IllegalArgumentException("Cache-Control directives parmater cannot be empty");
		}

		StringTokenizer tokenizer = new StringTokenizer(directives, ",", false);
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken().trim();
			Matcher matcher = maxAgePattern.matcher(token);
			if (matcher.matches()) {
				maxAge = Integer.parseInt(matcher.group(1));
				if (maxAge < 0) {
					maxAge = 0;
				}
				break;
			}
		}

		String includeMatchesParam = filterConfig.getInitParameter("includeMatches");
		if (includeMatchesParam != null) {
			includeMatches = Pattern.compile(includeMatchesParam);
		}

		String excludeMatchesParam = filterConfig.getInitParameter("excludeMatches");
		if (excludeMatchesParam != null) {
			excludeMatches = Pattern.compile(excludeMatchesParam);
		}
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String requestPath = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
		if ((includeMatches == null || includeMatches.matcher(requestPath).matches()) && (excludeMatches == null || !excludeMatches.matcher(requestPath).matches())) {
			if (maxAge != -1) {
				httpResponse.setDateHeader("Expires", System.currentTimeMillis() + maxAge * 1000);
			}
			httpResponse.setHeader("Cache-Control", directives);
		}
		if (requestPath.equals("/defaults.pref")) {
			// TODO: add missing header, see Bugzilla 
			httpResponse.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
		}
		chain.doFilter(request, response);
	}

	public void destroy() {
	}

}
