/*******************************************************************************
 * Copyright (c) 2014 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import javax.servlet.http.Cookie;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.core.ServerStatus;
import java.io.IOException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter that implements XSRF protection via double submit cookies.
 */
public class XSRFPreventionFilter implements Filter {

	private static final String NONCES_DO_NOT_MATCH = "{0} {1} on behalf of user ''{2}'': CSRF tokens do not match: ''{3}'' does not equal ''{4}''";
	private static final String NO_NONCE_IN_HEADER = "{0} {1} on behalf of user ''{2}'': missing CSRF token in header.";
	private static final String NO_NONCE_IN_COOKIES = "{0} {1} on behalf of user ''{2}'': missing CSRF token in cookies.";

	private static final Logger LOG = LoggerFactory.getLogger(XSRFPreventionFilter.class);

	private static final String XSRF_TOKEN = "x-csrf-token";

	private final Set<String> entryPointList = new HashSet<String>();
	private final Set<String> exceptionList = new HashSet<String>();

	private SecureRandom secureRandom;

	public void init(FilterConfig filterConfig) throws ServletException {
		entryPointList.add("/login");

		exceptionList.add("/login");
		exceptionList.add("/login/canaddusers");
		exceptionList.add("/login/form");
		exceptionList.add("/useremailconfirmation/cansendemails");

		secureRandom = new SecureRandom();
		secureRandom.nextBytes(new byte[1]);
	}

	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {

		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) resp;
		String method = request.getMethod();

		String path = request.getServletPath();
		if (request.getPathInfo() != null) {
			path = path + request.getPathInfo();
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug(MessageFormat.format("Filter called for {0} {1}. ", method, path));
		}

		// check if nonce should be generated
		CookieHandler ch = new CookieHandler(request.getCookies(), XSRF_TOKEN);
		if (entryPointList.contains(path) && !ch.hasNonceCookie()) {
			response.addCookie(new Cookie(XSRF_TOKEN, generateNonce(method, path)));
		}

		boolean doNonceCheck = !"get".equalsIgnoreCase(method) && !exceptionList.contains(path);
		if (doNonceCheck) {
			String requestNonce = request.getHeader(XSRF_TOKEN);
			boolean nonceValid = checkNonce(method, path, ch, requestNonce);

			if (!nonceValid) {
				logReasonForInvalidNonce(request, method, path, ch, requestNonce);
				prepareResponseForInvalidNonce(response);
				return;
			}
		} else if (LOG.isDebugEnabled()) {
			LOG.debug(MessageFormat.format("Skipping nonce check for {0} {1}", method, path));
		}

		chain.doFilter(request, response);
	}

	public void destroy() {
		// do nothing
	}

	private void prepareResponseForInvalidNonce(HttpServletResponse response) throws IOException {
		response.setHeader(XSRF_TOKEN, "required");
		ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Access Denied", null);
		response.setContentType("application/json");
		response.setStatus(status.getHttpCode());
		response.getWriter().print(status.toJSON().toString());
	}

	private void logReasonForInvalidNonce(HttpServletRequest request, String method, String path, CookieHandler ch, String requestNonce) {
		if (ch.hasNonceCookie() && (requestNonce != null)) {
			LOG.error(MessageFormat.format(NONCES_DO_NOT_MATCH, method, path, request.getRemoteUser(), requestNonce, ch.getValue()));
		} else {
			if (!ch.hasNonceCookie()) {
				LOG.error(MessageFormat.format(NO_NONCE_IN_COOKIES, method, path, request.getRemoteUser()));
			}
			if (requestNonce == null) {
				LOG.error(MessageFormat.format(NO_NONCE_IN_HEADER, method, path, request.getRemoteUser()));
			}
		}
	}

	private boolean checkNonce(String method, String path, CookieHandler ch, String requestNonce) {
		boolean nonceValid = false;
		if (ch.hasNonceCookie()) {
			nonceValid = ch.getValue().equals(requestNonce);
		}
		return nonceValid;
	}

	private String generateNonce(String method, String path) {
		byte[] randomBytes = new byte[24];
		secureRandom.nextBytes(randomBytes);
		String nonce = Base64.encodeBase64URLSafeString(randomBytes);
		if (LOG.isDebugEnabled()) {
			LOG.debug(MessageFormat.format("Creating nonce  for {0} {1}: ''{2}''", method, path, nonce));
		}
		return nonce;
	}

	private static class CookieHandler {
		private Cookie cookie;

		public CookieHandler(Cookie[] cookies, String name) {
			for (Cookie c : cookies) {
				if (name.equals(c.getName())) {
					cookie = c;
					break;
				}
			}
		}

		public String getValue() {
			return cookie.getValue();
		}

		public boolean hasNonceCookie() {
			return cookie != null;
		}
	}
}
