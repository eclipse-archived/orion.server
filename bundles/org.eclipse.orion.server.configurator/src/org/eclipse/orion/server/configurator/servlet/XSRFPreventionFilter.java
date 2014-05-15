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
package org.eclipse.orion.server.configurator.servlet;

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

public class XSRFPreventionFilter implements Filter {

	private static final Logger LOG = LoggerFactory.getLogger(XSRFPreventionFilter.class);

	private static final String XSRF_COOKIE_NAME = "x-csrf-token";
	private static final String XSRF_NONCE_REQUEST_ATTR = "x-csrf-token";

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

		LOG.debug(MessageFormat.format("Filter called for {0} {1}. ", method, path));

		// check if nonce should be generated
		CookieHandler ch = new CookieHandler(request.getCookies(), XSRF_COOKIE_NAME);
		if (entryPointList.contains(path) && !ch.hasNonceCookie()) {
			String nonce = generateNonce();
			LOG.debug(MessageFormat.format("Creating nonce cookie for {0} {1}: ''{2}''", method, path, nonce));
			Cookie cookie = new Cookie(XSRF_COOKIE_NAME, nonce);
			response.addCookie(cookie);
		}

		boolean doNonceCheck = !"get".equalsIgnoreCase(method) && !exceptionList.contains(path);
		if (doNonceCheck) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(MessageFormat.format("Checking nonce for {0} {1}", method, path));
			}
			boolean nonceValid = false;
			String requestNonce = request.getHeader(XSRF_NONCE_REQUEST_ATTR);
			if (ch.hasNonceCookie()) {
				LOG.debug(MessageFormat.format("Comparing nonce from request ''{0}'' with nonce in cookie ''{1}''", requestNonce, ch.getValue()));
				if (ch.getValue().equals(requestNonce)) {
					nonceValid = true;
				}
			}

			if (!nonceValid) {
				if (ch.hasNonceCookie() && (requestNonce != null)) {
					LOG.error(MessageFormat.format("{0} {1} on behalf of user ''{2}'': CSRF tokens do not match: ''{3}'' does not equal ''{4}''", method, path, request.getRemoteUser(), requestNonce, ch.getValue()));
				} else {
					if (!ch.hasNonceCookie()) {
						LOG.error(MessageFormat.format("{0} {1} on behalf of user ''{2}'': missing CSRF token in cookies.", method, path, request.getRemoteUser()));
					}
					if (requestNonce == null)
						LOG.error(MessageFormat.format("{0} {1} on behalf of user ''{2}'': missing CSRF token in header.", method, path, request.getRemoteUser()));
				}

				response.setHeader(XSRF_NONCE_REQUEST_ATTR, "required");
				ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Access Denied", null);
				response.setContentType("application/json");
				response.setStatus(status.getHttpCode());
				response.getWriter().print(status.toJSON().toString());
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

	private String generateNonce() {
		byte[] randomBytes = new byte[24];
		secureRandom.nextBytes(randomBytes);
		return Base64.encodeBase64URLSafeString(randomBytes);
	}

	private class CookieHandler {
		private Cookie cookie;
		private boolean foundCookie = false;

		public CookieHandler(Cookie[] cookies, String name) {
			for (Cookie c : cookies) {
				if (name.equals(c.getName())) {
					cookie = c;
					foundCookie = true;
				}
			}
		}

		public String getValue() {
			return cookie.getValue();
		}

		public boolean hasNonceCookie() {
			return foundCookie;
		}
	}
}
