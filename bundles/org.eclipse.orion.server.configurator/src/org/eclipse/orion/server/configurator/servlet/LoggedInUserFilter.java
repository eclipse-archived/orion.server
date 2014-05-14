/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.configurator.servlet;

import org.eclipse.orion.server.authentication.IAuthenticationService;
import java.io.IOException;
import java.util.Properties;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.configurator.ConfiguratorActivator;
import org.eclipse.orion.server.core.*;
import org.osgi.service.http.HttpContext;

/**
 * The filter checks whether the request is done by an authenticated user.
 * It does not verify the rules in the authorization service.
 */
public class LoggedInUserFilter implements Filter {

	private IAuthenticationService authenticationService;
	private Properties authProperties;
	private boolean redirect = true;

	public void init(FilterConfig filterConfig) throws ServletException {
		if (Boolean.FALSE.toString().equals(filterConfig.getInitParameter("redirect"))) {
			redirect = false;
		};

		authenticationService = ConfiguratorActivator.getDefault().getAuthService();
		// treat lack of authentication as an error. Administrator should use
		// "None" to disable authentication entirely
		if (authenticationService == null) {
			String msg = "Authentication service is missing. The server configuration must specify an authentication scheme, or use \"None\" to indicate no authentication"; //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.ERROR, ConfiguratorActivator.PI_CONFIGURATOR, msg, null));
			throw new ServletException(msg);
		}
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		if (httpRequest.getRemoteUser() != null) {
			chain.doFilter(request, response);
			return;
		}

		String login;
		if (redirect) {
			login = authenticationService.authenticateUser(httpRequest, httpResponse, authProperties);
			if (login == null) {
				return;
			}
		} else {
			login = authenticationService.getAuthenticatedUser(httpRequest, httpResponse, authProperties);
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
