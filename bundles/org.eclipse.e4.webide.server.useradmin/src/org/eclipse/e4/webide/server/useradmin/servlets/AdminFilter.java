/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.webide.server.useradmin.servlets;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.e4.webide.server.useradmin.EclipseWebUserAdminRegistry;
import org.eclipse.e4.webide.server.useradmin.UserAdminActivator;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.UserAdmin;

public class AdminFilter implements Filter {

	private static final String ADMIN_ROLE = "admin";

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		if ("POST".equals(httpRequest.getMethod())) { // everyone can create a user
			chain.doFilter(request, response);
			return;
		}

		if ("GET".equals(httpRequest.getMethod()) && httpRequest.getPathInfo().startsWith("/create")) {
			// display add user form to everyone
			chain.doFilter(request, response);
			return;
		}

		// TODO: We need a better way to get the authentication service that is configured
		String user = UserAdminActivator.getDefault().getAuthenticationService().authenticateUser(httpRequest, httpResponse, null);
		UserAdmin userAdmin;
		userAdmin = EclipseWebUserAdminRegistry.getDefault().getUserStore();
		Authorization authorization = userAdmin.getAuthorization(userAdmin.getUser("login", user));

		if (authorization.hasRole(ADMIN_ROLE)) {
			chain.doFilter(request, response);
			return;
		}

		httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub

	}

}
