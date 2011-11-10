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
package org.eclipse.orion.server.openid;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.eclipse.orion.server.openid.core.OpenIdHelper;
import org.eclipse.orion.server.openid.httpcontext.BundleEntryHttpContext;
import org.eclipse.orion.server.openid.servlet.OpenIdLogoutServlet;
import org.eclipse.orion.server.openid.servlet.OpenidServlet;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class OpenidAuthenticationService implements IAuthenticationService {

	private HttpService httpService;
	public static final String OPENIDS_PROPERTY = "openids"; //$NON-NLS-1$
	
	private boolean registered;

	public OpenidAuthenticationService() {
		super();
	}

	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		return OpenIdHelper.getAuthenticatedUser(req);
	}

	public String getAuthType() {
		return OpenIdHelper.getAuthType();
	}

	private void setNotAuthenticated(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
				req.getRequestDispatcher("/openidstatic/LoginWindow.html").forward(req, resp);
			} catch (ServletException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "An error occured during authenitcation", e));
			}
		} else {
			resp.setContentType("application/json; charset=UTF-8");
			JSONObject result = new JSONObject();
			try {
				result.put("SignInLocation", "/openidstatic/LoginWindow.html");
				result.put("label", "Orion workspace server");
				result.put("SignInKey", Activator.OPENID_AUTH_SIGNIN_KEY);
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "An error occured during authenitcation", e));
			}
			resp.getWriter().print(result.toString());
		}
	}

	public void configure(Properties properties) {
		try {
			httpService.registerResources("/authenticationPlugin.html", "/web/authenticationPlugin.html", new BundleEntryHttpContext(Activator.getBundleContext().getBundle()));
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_OPENID_SERVLETS, "Reconfiguring FormOpenIdAuthenticationService"));
		}
	}

	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String user = getAuthenticatedUser(req, resp, properties);
		if (user == null) {
			setNotAuthenticated(req, resp);
		}
		return user;
	}

	public void setHttpService(HttpService httpService) {
		HttpContext httpContext = new BundleEntryHttpContext(Activator.getDefault().getBundleContext().getBundle());
		this.httpService = httpService;
		try {
			httpService.registerServlet("/openid", new OpenidServlet(), null, httpContext); //$NON-NLS-1$
			httpService.registerServlet("/logout", new OpenIdLogoutServlet(), null, httpContext); //$NON-NLS-1$
			httpService.registerResources("/openidstatic", "/web", //$NON-NLS-1$ //$NON-NLS-2$
					httpContext);
		} catch (ServletException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "An error occured when registering servlets", e));
		} catch (NamespaceException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_SERVLETS, 1, "A namespace error occured when registering servlets", e));
		}
	}

	public void unsetHttpService(HttpService httpService) {
		httpService.unregister("/openid"); //$NON-NLS-1$
		httpService.unregister("/openidstatic"); //$NON-NLS-1$
		this.httpService = null;
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}

	public boolean getRegistered() {
		return registered;
	}
}
