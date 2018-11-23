/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.formoauth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.authentication.Activator;
import org.eclipse.orion.server.authentication.IAuthenticationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class FormAuthenticationService implements IAuthenticationService {

	private boolean registered = false;

	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String user = getAuthenticatedUser(req, resp);
		if (user == null) {
			setNotAuthenticated(req, resp);
		}
		return user;
	}

	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		HttpSession s = req.getSession(true);
		return (String) s.getAttribute("user");//$NON-NLS-1$
	}

	public String getAuthType() {
		// TODO What shall I return?
		return "FORM"; //$NON-NLS-1$
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
			resp.sendRedirect(req.getContextPath() + "/mixloginstatic/LoginWindow.html?redirect=" + req.getRequestURL());
		} else {
			resp.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
			JSONObject result = new JSONObject();
			try {
				result.put("SignInLocation", req.getContextPath() + "/mixloginstatic/LoginWindow.html");
				result.put("label", "Orion workspace server");
				result.put("SignInKey", "FORMOAuthUser");
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, 1, "An error occured during authenitcation", e));
			}
			resp.getWriter().print(result.toString());
		}
	}

	public void setHttpService(HttpService httpService) {
		try {
			httpService.registerServlet("/mixlogin/manageoauth", new ManageOAuthServlet(), null, null);
			httpService.registerServlet("/login", new FormAuthLoginServlet(this), null, null); //$NON-NLS-1$
			httpService.registerServlet("/logout", new FormAuthLogoutServlet(), null, null); //$NON-NLS-1$
		} catch (ServletException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, 1, "An error occured when registering servlets", e));
		} catch (NamespaceException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, 1, "A namespace error occured when registering servlets", e));
		}
	}

	public void unsetHttpService(HttpService httpService) {
		if (httpService != null) {
			httpService.unregister("/mixlogin/manageoauth"); //$NON-NLS-1$
			httpService.unregister("/login"); //$NON-NLS-1$
			httpService.unregister("/logout"); //$NON-NLS-1$
			httpService = null;
		}
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}

	public boolean isRegistered() {
		return registered;
	}
}
