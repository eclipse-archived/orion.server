/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin.servlets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.eclipse.orion.server.useradmin.UserAdminActivator;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.json.JSONException;
import org.osgi.service.http.HttpContext;

public class UserAuthFilter implements Filter {

	private IAuthenticationService authenticationService;
	private Properties authProperties;

	private List<String> authorizedAccountCreators;
	private boolean isGuestEnabled;

	public void init(FilterConfig filterConfig) throws ServletException {
		authenticationService = UserAdminActivator.getDefault().getAuthenticationService();
		// treat lack of authentication as an error. Administrator should use
		// "None" to disable authentication entirely
		if (authenticationService == null) {
			String msg = "Authentication service is missing. The server configuration must specify an authentication scheme, or use \"None\" to indicate no authentication"; //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.ERROR, UserAdminActivator.PI_USERADMIN, msg, null));
			throw new ServletException(msg);
		}
		String creators = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null);
		if (creators != null) {
			authorizedAccountCreators = new ArrayList<String>();
			authorizedAccountCreators.addAll(Arrays.asList(creators.split(","))); //$NON-NLS-1$
		}
		isGuestEnabled = Boolean.TRUE.toString().equals(PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION_GUEST, null));
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		if ("POST".equals(httpRequest.getMethod())) { //$NON-NLS-1$
			if (isGuestEnabled && httpRequest.getParameter(UserConstants.KEY_GUEST) != null) {
				// everyone is allowed to create guest user
				chain.doFilter(request, response);
				return;
			} else if (httpRequest.getParameter(UserConstants.KEY_RESET) == null) {
				// either everyone can create users, or only the specific list
				if (authorizedAccountCreators == null || authorizedAccountCreators.contains(httpRequest.getRemoteUser())) {
					chain.doFilter(request, response);
					return;
				}
			}
		}

		String login = httpRequest.getRemoteUser();
		if (login == null) {
			login = authenticationService.getAuthenticatedUser(httpRequest, httpResponse, authProperties);
			if (login == null)
				return;
		}

		request.setAttribute(HttpContext.REMOTE_USER, login);
		request.setAttribute(HttpContext.AUTHENTICATION_TYPE, authenticationService.getAuthType());

		try {
			String requestPath = httpRequest.getServletPath() + (httpRequest.getPathInfo() == null ? "" : httpRequest.getPathInfo());
			if (!AuthorizationService.checkRights(login, requestPath, httpRequest.getMethod())) {
				httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		} catch (JSONException e) {
			httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		chain.doFilter(request, response);
	}

	public void destroy() {
		// nothing to do
	}
}
