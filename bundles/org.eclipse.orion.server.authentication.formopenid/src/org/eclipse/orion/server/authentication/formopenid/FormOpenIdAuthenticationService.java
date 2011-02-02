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
package org.eclipse.orion.server.authentication.formopenid;

import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;

import org.eclipse.orion.server.openid.core.OpenIdHelper;

import org.eclipse.orion.server.authentication.formopenid.httpcontext.BundleEntryHttpContext;
import org.eclipse.orion.server.authentication.formopenid.servlets.*;

import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;

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

public class FormOpenIdAuthenticationService implements IAuthenticationService {

	private HttpService httpService;
	private Properties defaultAuthenticationProperties;
	public static final String OPENIDS_PROPERTY = "openids"; //$NON-NLS-1$

	public Properties getDefaultAuthenticationProperties() {
		return defaultAuthenticationProperties;
	}

	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String user = getAuthenticatedUser(req, resp, properties);
		if (user == null) {
			setNotAuthenticated(req, resp, properties);
		}
		return user;
	}

	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String formUser = FormAuthHelper.getAuthenticatedUser(req);
		if (formUser != null) {
			return formUser;
		}
		return OpenIdHelper.getAuthenticatedUser(req);
	}

	public String getAuthType() {
		// TODO What shall I return?
		return "FORM"; //$NON-NLS-1$
	}

	public void configure(Properties properties) {
		this.defaultAuthenticationProperties = properties;
		try {
			httpService.registerServlet("/auth2", new AuthInitServlet( //$NON-NLS-1$
					properties), null, new BundleEntryHttpContext(Activator.getBundleContext().getBundle()));
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_FORMOPENID_SERVLETS, "Reconfiguring FormOpenIdAuthenticationService"));

			try {
				httpService.unregister("/auth2");
				httpService.registerServlet("/auth2", new AuthInitServlet(properties), null, new BundleEntryHttpContext(Activator.getBundleContext().getBundle()));
			} catch (ServletException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "An error occured when registering servlets", e1));
			} catch (NamespaceException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "A namespace error occured when registering servlets", e1));
			} catch (IllegalArgumentException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "FormOpenIdAuthenticationService could not be configured", e1));
			}

		}

	}

	private void setNotAuthenticated(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {

		resp.setHeader("WWW-Authenticate", HttpServletRequest.FORM_AUTH); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		RequestDispatcher rd = req.getRequestDispatcher("/mixlogin/login?redirect=" //$NON-NLS-1$
				+ req.getRequestURI());
		if (properties != null) {
			req.setAttribute(OPENIDS_PROPERTY, properties.get(OPENIDS_PROPERTY));
		}
		try {
			rd.forward(req, resp);
		} catch (ServletException e) {
			throw new IOException(e.getMessage());
		} finally {
			resp.flushBuffer();
		}
	}

	public void setHttpService(HttpService hs) {
		httpService = hs;

		HttpContext httpContext = new BundleEntryHttpContext(Activator.getBundleContext().getBundle());

		try {
			httpService.registerServlet("/mixlogin", //$NON-NLS-1$
					new LoginFormServlet(this), null, httpContext);
			httpService.registerResources("/mixloginstatic", "/static", //$NON-NLS-1$ //$NON-NLS-2$
					httpContext);
			httpService.registerResources("/openids", "/openids", httpContext);
			httpService.registerServlet("/login", new FormOpenIdLoginServlet(this), null, httpContext); //$NON-NLS-1$
			httpService.registerServlet("/logout", new FormOpenIdLogoutServlet(), null, httpContext); //$NON-NLS-1$
		} catch (ServletException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "An error occured when registering servlets", e));
		} catch (NamespaceException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "A namespace error occured when registering servlets", e));
		}

	}

	public void unsetHttpService(HttpService hs) {
		if (httpService != null) {
			httpService.unregister("/mixlogin"); //$NON-NLS-1$
			httpService.unregister("/mixloginstatic"); //$NON-NLS-1$
			httpService.unregister("/login"); //$NON-NLS-1$
			httpService.unregister("/logout"); //$NON-NLS-1$
			httpService.unregister("/openids"); //$NON-NLS-1$
			httpService = null;
		}
	}
}
