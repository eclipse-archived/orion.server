/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;

/**
 * The filter checks whether the request is done by an authenticated user.
 * It does not verify the rules in the authorization service.
 */
public class MonitoringUserFilter implements Filter {

	private List<String> authorizedUsers;

	public void init(FilterConfig filterConfig) throws ServletException {
		String users = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_LOG_SERVICE, null);
		if (users != null) {
			authorizedUsers = new ArrayList<String>();
			authorizedUsers.addAll(Arrays.asList(users.split(","))); //$NON-NLS-1$
		}
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;

		if (authorizedUsers == null) {
			// no users are authorized to access the resource or service
			return;
		}
		
		String login = httpRequest.getRemoteUser();
		if (login == null || !authorizedUsers.contains(login)) {
			// the user is not authorized to access the resource or service
			return;
		}
	
		chain.doFilter(request, response);
	}

	public void destroy() {
		// nothing to do
	}
}
