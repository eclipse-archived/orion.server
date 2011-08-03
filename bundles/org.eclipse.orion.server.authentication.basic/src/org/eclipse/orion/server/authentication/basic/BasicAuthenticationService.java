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
package org.eclipse.orion.server.authentication.basic;

import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.osgi.service.useradmin.Authorization;

public class BasicAuthenticationService implements IAuthenticationService {

	private static IOrionCredentialsService userAdmin;

	private boolean registered;

	public BasicAuthenticationService() {
		super();
	}

	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String user = getAuthenticatedUser(req, resp, properties);
		if (user == null) {
			setNotAuthenticated(resp);
		}
		return user;
	}

	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		String authHead = req.getHeader("Authorization"); //$NON-NLS-1$

		if (authHead != null && authHead.toUpperCase(Locale.ENGLISH).startsWith(getAuthType())) {
			String base64 = authHead.substring(6);
			String authString = new String(Base64.decode(base64.getBytes()));
			if (authString.indexOf(':') < 0) {
				return null;
			}

			String login = authString.substring(0, authString.indexOf(':'));
			String password = authString.substring(authString.indexOf(':') + 1);
			User user = getUserForCredentials(login, password);
			if (user != null) {
				Authorization authorization = userAdmin.getAuthorization(user);
				// TODO handle authorization
				return user.getUid();
			}
		}
		return null;
	}

	public String getAuthType() {
		return HttpServletRequest.BASIC_AUTH;
	}

	private void setNotAuthenticated(HttpServletResponse resp) throws IOException {
		resp.setHeader("WWW-Authenticate", getAuthType()); //$NON-NLS-1$
		resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}

	private User getUserForCredentials(String login, String password) {
		if (userAdmin == null) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_SERVER_AUTHENTICATION_BASIC, "User admin server is not available"));
			return null;
		}
		User user = userAdmin.getUser("login", login); //$NON-NLS-1$
		if (user != null && user.hasCredential("password", password)) { //$NON-NLS-1$
			return user;
		}
		return null;
	}

	public void configure(Properties properties) {
		// nothing to do
	}

	public void bindUserAdmin(IOrionCredentialsService userAdmin) {
		BasicAuthenticationService.userAdmin = userAdmin;
	}

	public void unbindUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin.equals(BasicAuthenticationService.userAdmin)) {
			BasicAuthenticationService.userAdmin = null;
		}
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}

	public boolean getRegistered() {
		return registered;
	}
}
