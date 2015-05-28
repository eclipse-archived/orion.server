/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.authentication.IAuthenticationService;
import org.eclipse.orion.server.core.LogHelper;
import org.osgi.service.http.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The filter checks whether the request is done by an authenticated user.
 * It does not verify the rules in the authorization service.
 */
public class LoggedInUserFilter implements Filter {

	private IAuthenticationService authenticationService;
	private boolean redirect = true;

	public void init(FilterConfig filterConfig) throws ServletException {
		if (Boolean.FALSE.toString().equals(filterConfig.getInitParameter("redirect"))) { //$NON-NLS-1$
			redirect = false;
		};

		authenticationService = Activator.getDefault().getAuthService();
		// treat lack of authentication as an error. Administrator should use
		// "None" to disable authentication entirely
		if (authenticationService == null) {
			String msg = "Authentication service is missing. The server configuration must specify an authentication scheme, or use \"None\" to indicate no authentication"; //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, null));
			throw new ServletException(msg);
		}
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		if (httpRequest.getRemoteUser() == null) {
			//// See Bugzilla 468670, this code is temporary
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.login"); //$NON-NLS-1$
			logger.warn("Login hard coded to: ahunter"); //$NON-NLS-1$ 
			httpRequest.getSession().setAttribute("user", "ahunter");
		}

		if (httpRequest.getRemoteUser() != null) {
			chain.doFilter(request, response);
			return;
		}

		String login;
		if (redirect) {
			login = authenticationService.authenticateUser(httpRequest, httpResponse);
			if (login == null) {
				return;
			}
		} else {
			login = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
			if (login == null) {
				chain.doFilter(request, response);
				return;
			}
		}

		request.setAttribute(HttpContext.REMOTE_USER, login);
		request.setAttribute(HttpContext.AUTHENTICATION_TYPE, authenticationService.getAuthType());
		chain.doFilter(request, response);
	}

	public void destroy() {
		// nothing to do
	}
}
