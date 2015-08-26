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

import java.io.IOException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.commons.codec.binary.Base64;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.core.*;
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

	private static final String XSRF_TOKEN = "x-csrf-token";//$NON-NLS-1$

	private final Set<String> entryPointList = new HashSet<String>();
	private final Set<String> exceptionList = new HashSet<String>();

	private SecureRandom secureRandom;

	private boolean xsrfPreventionFilterDisabled = false;

	public void init(FilterConfig filterConfig) throws ServletException {
		entryPointList.add("/login");//$NON-NLS-1$

		exceptionList.add("/login");//$NON-NLS-1$
		exceptionList.add("/login/canaddusers");//$NON-NLS-1$
		exceptionList.add("/login/form");//$NON-NLS-1$
		exceptionList.add("/login/redirectinfo");//$NON-NLS-1$
		exceptionList.add("/useremailconfirmation/cansendemails");//$NON-NLS-1$

		secureRandom = new SecureRandom();
		secureRandom.nextBytes(new byte[1]);

		String enableCSRF = PreferenceHelper.getString(ServerConstants.CONFIG_XSRF_PROTECTION_ENABLED);
		xsrfPreventionFilterDisabled = !Boolean.parseBoolean(enableCSRF);
	}

	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {

		if (xsrfPreventionFilterDisabled) {
			chain.doFilter(req, resp);
			return;
		}

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
		if (isEntryPoint(req, path) && !ch.hasNonceCookie()) {
			response.addCookie(new Cookie(XSRF_TOKEN, generateNonce(method, path)));
		}

		boolean doNonceCheck = !"get".equalsIgnoreCase(method) && !isException(req, path);//$NON-NLS-1$
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

	private boolean isEntryPoint(ServletRequest req, String path) {
		if (entryPointList.contains(path))
			return true;
		// Self-hosting check
		return entryPointList.contains((String) req.getAttribute(RequestDispatcher.FORWARD_PATH_INFO));
	}

	private boolean isException(ServletRequest req, String path) {
		if (exceptionList.contains(path))
			return true;
		// Self-hosting check
		return exceptionList.contains((String) req.getAttribute(RequestDispatcher.FORWARD_PATH_INFO));
	}

	public void destroy() {
		// do nothing
	}

	private void prepareResponseForInvalidNonce(HttpServletResponse response) throws IOException {
		response.setHeader(XSRF_TOKEN, "required");//$NON-NLS-1$
		ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Access Denied", null);
		response.setContentType("application/json");//$NON-NLS-1$
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
			if (cookies == null)
				return;
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
