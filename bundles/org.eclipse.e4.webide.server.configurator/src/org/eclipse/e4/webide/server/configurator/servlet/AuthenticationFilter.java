/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.webide.server.configurator.servlet;

import java.io.IOException;
import java.util.Properties;

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
import org.eclipse.e4.webide.server.LogHelper;
import org.eclipse.e4.webide.server.authentication.IAuthenticationService;
import org.eclipse.e4.webide.server.configurator.ConfiguratorActivator;
import org.osgi.service.http.HttpContext;

public class AuthenticationFilter implements Filter {

	private IAuthenticationService authenticationService;
	private Properties authProperties;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		authenticationService = ConfiguratorActivator.getDefault().getAuthService();
		// treat lack of authentication as an error. Administrator should use
		// "None" to disable authentication entirely
		if (authenticationService == null) {
			String msg = "Authentication service is missing. The server configuration must specify an authentication scheme, or use \"None\" to indicate no authentication"; //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.ERROR, ConfiguratorActivator.PI_CONFIGURATOR, msg, null));
			throw new ServletException(msg);
		}

		// TODO need to read auth properties from InstanceScope preferences
		// authProperties =
		// ConfiguratorActivator.getDefault().getAuthProperties();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		String login = authenticationService.authenticateUser((HttpServletRequest) request, (HttpServletResponse) response, authProperties);
		if (login == null) {
			return;
		}

		request.setAttribute(HttpContext.REMOTE_USER, login);
		request.setAttribute(HttpContext.AUTHENTICATION_TYPE, authenticationService.getAuthType());
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}
}
