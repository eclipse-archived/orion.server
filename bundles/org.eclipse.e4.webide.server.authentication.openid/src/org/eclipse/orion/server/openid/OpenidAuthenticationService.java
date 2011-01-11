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
package org.eclipse.orion.server.openid;

import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;

import org.eclipse.orion.server.openid.core.OpenIdHelper;

import org.eclipse.orion.server.openid.servlet.*;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class OpenidAuthenticationService implements IAuthenticationService {

	private HttpService httpService;

	public OpenidAuthenticationService() {
		super();
	}

	@Override
	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		return OpenIdHelper.getAuthenticatedUser(req);
	}

	public String getAuthType() {
		return OpenIdHelper.getAuthType();
	}

	private void setNotAuthenticated(HttpServletRequest req, HttpServletResponse resp) throws IOException {

		resp.setHeader("WWW-Authenticate", HttpServletRequest.FORM_AUTH); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		RequestDispatcher rd = req.getRequestDispatcher("/openidform/login?redirect=" //$NON-NLS-1$
				+ req.getRequestURI());
		try {
			rd.forward(req, resp);
		} catch (ServletException e) {
			throw new IOException(e);
		} finally {
			resp.flushBuffer();
		}
	}

	@Override
	public void configure(Properties properties) {
		try {
			httpService.registerServlet("/auth2", new AuthInitServlet( //$NON-NLS-1$
					properties), null, new BundleEntryHttpContext(Activator.getDefault().getContext().getBundle()));
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_OPENID_SERVLETS, "Reconfiguring OpenidAuthenticationService"));

			try {
				httpService.unregister("/auth2");
				httpService.registerServlet("/auth2", new AuthInitServlet( //$NON-NLS-1$
						properties), null, new BundleEntryHttpContext(Activator.getDefault().getContext().getBundle()));
			} catch (ServletException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "An error occured when registering servlets", e1));
			} catch (NamespaceException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "A namespace error occured when registering servlets", e1));
			} catch (IllegalArgumentException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "OpenidAuthenticationService could not be configured", e1));
			}

		}
	}

	@Override
	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String user = getAuthenticatedUser(req, resp, properties);
		if (user == null) {
			setNotAuthenticated(req, resp);
		}
		return user;
	}

	public void setHttpService(HttpService httpService) {
		HttpContext httpContext = new BundleEntryHttpContext(Activator.getDefault().getContext().getBundle());
		this.httpService = httpService;
		try {
			httpService.registerServlet("/openid", new OpenidServlet(), null, httpContext); //$NON-NLS-1$
			httpService.registerServlet("/logout", new OpenIdLogoutServlet(), null, httpContext); //$NON-NLS-1$
			httpService.registerServlet("/openidform", new OpenIdFormServlet(), null, httpContext); //$NON-NLS-1$
			httpService.registerResources("/openidstatic", "/static", //$NON-NLS-1$ //$NON-NLS-2$
					httpContext);
		} catch (ServletException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "An error occured when registering servlets", e));
		} catch (NamespaceException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "A namespace error occured when registering servlets", e));
		}
	}

	public void unsetHttpService(HttpService httpService) {
		httpService.unregister("/openid"); //$NON-NLS-1$
		httpService.unregister("openidform"); //$NON-NLS-1$
		httpService.unregister("/openidstatic"); //$NON-NLS-1$
		this.httpService = null;
	}

}
