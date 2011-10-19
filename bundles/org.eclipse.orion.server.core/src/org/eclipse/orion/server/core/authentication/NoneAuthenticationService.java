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
package org.eclipse.orion.server.core.authentication;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.osgi.framework.Version;

/**
 * This authentication service is registered when <code>configuration.xml</code>
 * points to "None" authentication. This authentication service passes though
 * all the requests returns empty strings for
 * {@link #getAuthenticatedUser(HttpServletRequest, HttpServletResponse, Properties)}
 * and {@link #getAuthType()}.
 * 
 */
public class NoneAuthenticationService implements IAuthenticationService {

	public static final String AUTH_TYPE = "None";
	private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";//$NON-NLS-1$
	public boolean registered = false;

	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		return getAuthenticatedUser(req, resp, properties);
	}

	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		return "Anonymous";
	}

	public String getAuthType() {
		return AUTH_TYPE;
	}

	public void configure(Properties properties) {
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}

	public boolean getRegistered() {
		return registered;
	}

	public void setUnauthrizedUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String versionString = req.getHeader("Orion-Version"); //$NON-NLS-1$
		Version version = versionString == null ? null : new Version(versionString);

		// TODO: This is a workaround for calls
		// that does not include the WebEclipse version header
		String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

		String msg = "You are not authorized to access " + req.getRequestURL();
		if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, msg);
		} else {
			resp.setContentType(CONTENT_TYPE_JSON);
			ServerStatus serverStatus = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, msg, null);
			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			resp.getWriter().print(serverStatus.toJSON().toString());
		}

	}

}
