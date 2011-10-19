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

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;
import org.eclipse.orion.server.authentication.formopenid.httpcontext.BundleEntryHttpContext;
import org.eclipse.orion.server.authentication.formopenid.servlets.FormOpenIdLoginServlet;
import org.eclipse.orion.server.authentication.formopenid.servlets.FormOpenIdLogoutServlet;
import org.eclipse.orion.server.authentication.formopenid.servlets.ManageOpenidsServlet;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.openid.core.OpenIdHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class FormOpenIdAuthenticationService implements IAuthenticationService {

	private HttpService httpService;
	private Properties defaultAuthenticationProperties;
	public static final String OPENIDS_PROPERTY = "openids"; //$NON-NLS-1$
	
	private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";//$NON-NLS-1$

	private boolean registered = false;

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
			httpService.registerResources("/authenticationPlugin.html", "/web/authenticationPlugin.html", new BundleEntryHttpContext(Activator.getBundleContext().getBundle()));
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_FORMOPENID_SERVLETS, "Reconfiguring FormOpenIdAuthenticationService"));
		}
	}

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
			resp.sendRedirect(req.getContextPath() + "/mixloginstatic/LoginWindow.html?redirect=" + req.getRequestURI());
		} else {
			resp.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
			JSONObject result = new JSONObject();
			try {
				result.put("SignInLocation", "/mixloginstatic/LoginWindow.html");
				result.put("SignInKey", "FORMOpenIdUser");
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "An error occured during authenitcation", e));
			}
			resp.getWriter().print(result.toString());
		}
	}

	public void setHttpService(HttpService hs) {
		httpService = hs;

		HttpContext httpContext = new BundleEntryHttpContext(Activator.getBundleContext().getBundle());

		try {
			httpService.registerResources("/mixloginstatic", "/web", //$NON-NLS-1$ //$NON-NLS-2$
					httpContext);
			httpService.registerServlet("/mixlogin/manageopenids", new ManageOpenidsServlet(this), null, httpContext);
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
			httpService.unregister("/mixloginstatic"); //$NON-NLS-1$
			httpService.unregister("/mixlogin/manageopenids"); //$NON-NLS-1$
			httpService.unregister("/login"); //$NON-NLS-1$
			httpService.unregister("/logout"); //$NON-NLS-1$
			httpService = null;
		}
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
		Activator.getDefault().getResourceDecorator().setDecorate(registered);
	}

	public boolean getRegistered() {
		return registered;
	}

	public void setUnauthrizedUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String versionString = req.getHeader("Orion-Version"); //$NON-NLS-1$
		Version version = versionString == null ? null : new Version(versionString);

		String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

		String msg = "You are not authorized to access " + req.getRequestURL();
		if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
			String url = "/mixloginstatic/LoginWindow.html";
			msg+="<br>You can authencatie as different user";
			if (req.getParameter("redirect") != null) {
				url += "?redirect=" + req.getParameter("redirect");
			}

			url += url.contains("?") ? "&" : "?";
			url += "error=" + new String(Base64.encode(msg.getBytes()));

			resp.sendRedirect(url);

		} else {
			resp.setContentType(CONTENT_TYPE_JSON);
			ServerStatus serverStatus = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, msg, null);
			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			resp.getWriter().print(serverStatus.toJSON().toString());
		}
		
	}
}
