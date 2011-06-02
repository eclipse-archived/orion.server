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
package org.eclipse.orion.server.authentication.form;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;
import org.eclipse.orion.server.authentication.form.httpcontext.BundleEntryHttpContext;
import org.eclipse.orion.server.authentication.form.servlets.AuthInitServlet;
import org.eclipse.orion.server.authentication.form.servlets.LoginFormServlet;
import org.eclipse.orion.server.authentication.form.servlets.LoginServlet;
import org.eclipse.orion.server.authentication.form.servlets.LogoutServlet;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class FormAuthenticationService implements IAuthenticationService {

	public static final String CSS_LINK_PROPERTY = "STYLES"; //$NON-NLS-1$
	private Properties defaultAuthenticationProperties;

	public Properties getDefaultAuthenticationProperties() {
		return defaultAuthenticationProperties;
	}

	public FormAuthenticationService() {
		super();
	}

	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String user = getAuthenticatedUser(req, resp, properties);
		if (user == null) {
			setNotAuthenticated(req, resp, properties);
		}
		return user;
	}

	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String username = FormAuthHelper.getAuthenticatedUser(req);
		if (username != null) {
			return username;
		}
		/*
				// let through any calls to Login Servlet
				if (req.getServletPath().startsWith("/login")) { //$NON-NLS-1$
					return ""; //$NON-NLS-1$
				}
				if (req.getServletPath().startsWith("/loginform")) { //$NON-NLS-1$
					return ""; //$NON-NLS-1$
				}
		*/
		return null;
	}

	public String getAuthType() {
		return HttpServletRequest.FORM_AUTH;
	}

	public void configure(Properties properties) {
		this.defaultAuthenticationProperties = properties;
		try {

			httpService.registerServlet("/auth2", new AuthInitServlet( //$NON-NLS-1$
					properties), null, new BundleEntryHttpContext(Activator.getBundleContext().getBundle()));
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_FORM_SERVLETS, "Reconfiguring FormAutneticationService"));

			try {
				httpService.unregister("/auth2");
				httpService.registerServlet("/auth2", new AuthInitServlet(properties), null, new BundleEntryHttpContext(Activator.getBundleContext().getBundle()));
			} catch (ServletException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORM_SERVLETS, 1, "An error occured when registering servlets", e1));
			} catch (NamespaceException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORM_SERVLETS, 1, "A namespace error occured when registering servlets", e1));
			} catch (IllegalArgumentException e1) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORM_SERVLETS, 1, "FormAuthenticationService could not be configured", e1));
			}

		}
	}

	private HttpService httpService;

	private void setNotAuthenticated(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		if (properties == null) {
			properties = new Properties();
		}
		resp.setHeader("WWW-Authenticate", getAuthType()); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		String putStyle = properties.getProperty(CSS_LINK_PROPERTY) == null ? "" //$NON-NLS-1$
				: "&styles=" + properties.getProperty(CSS_LINK_PROPERTY); //$NON-NLS-1$
		RequestDispatcher rd = req.getRequestDispatcher("/loginform/login?redirect=" //$NON-NLS-1$
				+ req.getRequestURI() + putStyle);
		try {
			rd.forward(req, resp);
		} catch (ServletException e) {
			throw new IOException(e.getMessage());
		} finally {
			resp.flushBuffer();
		}
	}

	public/* synchronized? */void setHttpService(HttpService hs) {
		httpService = hs;

		HttpContext httpContext = new BundleEntryHttpContext(Activator.getBundleContext().getBundle());

		try {
			httpService.registerServlet("/login", new LoginServlet(), null, //$NON-NLS-1$
					httpContext);
			httpService.registerServlet("/logout", new LogoutServlet(), null, //$NON-NLS-1$
					httpContext);
			httpService.registerServlet("/loginform", new LoginFormServlet(), //$NON-NLS-1$
					null, httpContext);
			httpService.registerResources("/loginstatic", "/web", //$NON-NLS-1$ //$NON-NLS-2$
					httpContext);
		} catch (ServletException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORM_SERVLETS, 1, "An error occured when registering servlets", e));
		} catch (NamespaceException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORM_SERVLETS, 1, "A namespace error occured when registering servlets", e));
		}
	}

	public/* synchronized? */void unsetHttpService(HttpService hs) {
		httpService.unregister("/login"); //$NON-NLS-1$
		httpService.unregister("/logout"); //$NON-NLS-1$
		httpService.unregister("/loginform"); //$NON-NLS-1$
		httpService.unregister("/loginstatic"); //$NON-NLS-1$
		httpService = null;
	}

}
