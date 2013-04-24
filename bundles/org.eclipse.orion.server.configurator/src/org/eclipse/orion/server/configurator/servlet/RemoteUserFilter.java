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

import javax.servlet.http.HttpServletRequestWrapper;

import javax.servlet.http.HttpServletResponseWrapper;

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

/**
 * The filter checks whether the request is done by an authenticated user.
 * It does not verify the rules in the authorization service.
 */
public class RemoteUserFilter implements Filter {

	private IAuthenticationService authenticationService;
	private Properties authProperties;

	public void init(FilterConfig filterConfig) throws ServletException {
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;

		HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper(httpRequest) {

			@Override
			public String getAuthType() {
				return "CONTAINER";
			}

			@Override
			public String getRemoteUser() {
				return "antonm";
			}

		};
		chain.doFilter(requestWrapper, response);
	}

	public void destroy() {
		// nothing to do
	}
}
