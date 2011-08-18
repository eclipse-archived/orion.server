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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;
import org.eclipse.orion.server.authentication.form.httpcontext.BundleEntryHttpContext;
import org.eclipse.orion.server.authentication.form.servlets.AuthInitServlet;
import org.eclipse.orion.server.authentication.form.servlets.LoginServlet;
import org.eclipse.orion.server.authentication.form.servlets.LogoutServlet;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class FormAuthenticationService implements IAuthenticationService {

	public static final String CSS_LINK_PROPERTY = "STYLES"; //$NON-NLS-1$
	private Properties defaultAuthenticationProperties;

	private boolean registered = false;

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
		if (username != null)
			return username;
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
			httpService.registerResources("/authenticationPlugin.html", "/web/authenticationPlugin.html", new BundleEntryHttpContext(Activator.getBundleContext().getBundle()));
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
		resp.setHeader("WWW-Authenticate", HttpServletRequest.FORM_AUTH); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

		// redirection from FormAuthenticationService.setNotAuthenticated
		String versionString = req.getHeader("Orion-Version"); //$NON-NLS-1$
		Version version = versionString == null ? null : new Version(versionString);

		// TODO: This is a workaround for calls
		// that does not include the WebEclipse version header
		String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

		if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
			try {
				req.getRequestDispatcher("/loginstatic/LoginWindow.html").forward(req, resp);
			} catch (ServletException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORM_SERVLETS, 1, "An error occured during authenitcation", e));
			}
		} else {
			resp.setContentType("application/json; charset=UTF-8");
			JSONObject result = new JSONObject();
			try {
				result.put("SignInLocation", "/loginstatic/LoginWindow.html");
				result.put("SignInKey", Activator.FORM_AUTH_SIGNIN_KEY);
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORM_SERVLETS, 1, "An error occured during authenitcation", e));
			}
			resp.getWriter().print(result.toString());
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
		httpService.unregister("/loginstatic"); //$NON-NLS-1$
		httpService = null;
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}

	public boolean getRegistered() {
		return registered;
	}

}
