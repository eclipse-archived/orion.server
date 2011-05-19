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

import java.io.IOException;
import java.util.Properties;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.configurator.ConfiguratorActivator;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.osgi.service.http.HttpContext;

public class AuthenticationFilter implements Filter {

	private IAuthenticationService authenticationService;
	private Properties authProperties;

	public void init(FilterConfig filterConfig) throws ServletException {
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

		String projectWorldReadable = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_ANONYMOUS_READ);
		if (httpRequest.getMethod().equals("GET") && httpRequest.getRequestURI().toString().startsWith("/file/") && "true".equalsIgnoreCase(projectWorldReadable)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			chain.doFilter(request, response);
			return;
		}

		String login = authenticationService.authenticateUser(httpRequest, httpResponse, authProperties);
		if (login == null) {
			return;
		}

		request.setAttribute(HttpContext.REMOTE_USER, login);
		request.setAttribute(HttpContext.AUTHENTICATION_TYPE, authenticationService.getAuthType());
		chain.doFilter(request, response);
	}

	public void destroy() {
		// nothing to do
	}
}
