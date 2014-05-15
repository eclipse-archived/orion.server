package org.eclipse.orion.server.configurator.servlet;

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

	private static final String XSRF_NONCE_SESSION_ATTR = "x-csrf-token";
	private static final String XSRF_NONCE_REQUEST_ATTR = "x-csrf-token";
	private static final String XSRF_NONCE_FETCH = "fetch";

	private final Set<String> exceptionList = new HashSet<String>();

	private SecureRandom secureRandom;

	public void init(FilterConfig filterConfig) throws ServletException {
		exceptionList.add("/login");
		exceptionList.add("/logout");
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

		// checking if nonce should be generated
		if (XSRF_NONCE_FETCH.equalsIgnoreCase(request.getHeader(XSRF_NONCE_REQUEST_ATTR))) {
			String nonce;
			if (request.getSession().getAttribute(XSRF_NONCE_SESSION_ATTR) != null) {
				nonce = (String) request.getSession().getAttribute(XSRF_NONCE_SESSION_ATTR);
				if (LOG.isDebugEnabled()) {
					LOG.debug(MessageFormat.format("Returning existing nonce ''{0}''", nonce));
				}
			} else {
				nonce = generateNonce();
				request.getSession().setAttribute(XSRF_NONCE_SESSION_ATTR, nonce);
				if (LOG.isDebugEnabled()) {
					LOG.debug(MessageFormat.format("Returning new nonce ''{0}''", nonce));
				}
			}
			response.addHeader(XSRF_NONCE_REQUEST_ATTR, nonce);
		}

		// nonce check
		String path = request.getServletPath();
		if (request.getPathInfo() != null) {
			path = path + request.getPathInfo();
		}

		boolean doNonceCheck = !"get".equalsIgnoreCase(method) && !exceptionList.contains(path);
		if (doNonceCheck) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(MessageFormat.format("Checking nonce for {0} {1}", method, path));
			}
			String requestNonce = request.getHeader(XSRF_NONCE_REQUEST_ATTR);
			String sessionNonce = (String) request.getSession().getAttribute(XSRF_NONCE_SESSION_ATTR);
			boolean nonceValid = false;
			if (sessionNonce != null) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(MessageFormat.format("Comparing nonce from request ''{0}'' with nonce in session ''{1}''", requestNonce, sessionNonce));
				}
				if (sessionNonce.equals(requestNonce)) {
					nonceValid = true;
				}
			}

			if (!nonceValid) {
				if (sessionNonce != null) {
					LOG.error(MessageFormat.format("{0} {1} on behalf of user ''{2}'': invalid CSRF token ''{3}'')", method, path, request.getRemoteUser(), sessionNonce));
				} else {
					LOG.error(MessageFormat.format("{0} {1} on behalf of user ''{2}'': missing CSRF token)", method, path, request.getRemoteUser()));
				}
				response.setHeader(XSRF_NONCE_REQUEST_ATTR, "required");
				ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Access Denied", null);
				response.setContentType("application/json");
				response.setStatus(status.getHttpCode());
				response.getWriter().print(status.toJSON().toString());
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
		return Base64.encodeBase64(randomBytes).toString();
	}
}
