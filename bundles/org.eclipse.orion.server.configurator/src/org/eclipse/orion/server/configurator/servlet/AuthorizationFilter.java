/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
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
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.json.JSONException;

public class AuthorizationFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// nothing to do
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		try {
			if (!AuthorizationService.checkRights(httpRequest.getRemoteUser(), httpRequest.getRequestURI().toString(), httpRequest.getMethod())) {
				httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		} catch (JSONException e) {
			httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}
}
