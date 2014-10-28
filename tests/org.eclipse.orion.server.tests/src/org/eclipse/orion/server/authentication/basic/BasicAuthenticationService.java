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
package org.eclipse.orion.server.authentication.basic;

import java.io.IOException;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.authentication.IAuthenticationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class BasicAuthenticationService implements IAuthenticationService {

	public static String PI_BASIC_AUTH = "org.eclipse.orion.server.authentication.formoauth";
	private boolean registered;

	public BasicAuthenticationService() {
		super();
	}

	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String user = getAuthenticatedUser(req, resp);
		if (user == null) {
			setNotAuthenticated(resp);
		}
		return user;
	}

	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String authHead = req.getHeader("Authorization"); //$NON-NLS-1$

		if (authHead != null && authHead.toUpperCase(Locale.ENGLISH).startsWith(getAuthType())) {
			String base64 = authHead.substring(6);
			String authString = new String(Base64.decode(base64.getBytes()));
			if (authString.indexOf(':') < 0) {
				return null;
			}

			String login = authString.substring(0, authString.indexOf(':'));
			String password = authString.substring(authString.indexOf(':') + 1);
			UserInfo userInfo = getUserForCredentials(login, password);
			if (userInfo != null) {
				//				Authorization authorization = userAdmin.getAuthorization(user);
				// TODO handle authorization
				return userInfo.getUniqueId();
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
		//add a sleep to avoid brute force login attack
		long start = System.currentTimeMillis();
		long SLEEP_TIME = 1000;
		do {
			try {
				Thread.sleep(SLEEP_TIME);
				break;
			} catch (InterruptedException e) {
				//ignore and keep waiting
			}
		} while ((System.currentTimeMillis() - start) < SLEEP_TIME);
	}

	private UserInfo getUserForCredentials(String login, String password) {
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, login, false, false);
			if (userInfo != null && userInfo.getProperty(UserConstants2.PASSWORD) != null) {
				String userPassword = userInfo.getProperty(UserConstants2.PASSWORD);
				if (password.equals(userPassword)) {
					return userInfo;
				}
			}
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.ERROR, PI_BASIC_AUTH, 1, "An error occured when validating user credentials", e));
		}
		return null;
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}

	public boolean isRegistered() {
		return registered;
	}

	public void setHttpService(HttpService httpService) {
		try {
			httpService.registerServlet("/basiclogin", //$NON-NLS-1$
					new BasicAuthenticationServlet(this), null, null);
		} catch (ServletException e) {
			LogHelper.log(new Status(IStatus.ERROR, PI_BASIC_AUTH, 1, "An error occured when registering servlets", e));
		} catch (NamespaceException e) {
			LogHelper.log(new Status(IStatus.ERROR, PI_BASIC_AUTH, 1, "A namespace error occured when registering servlets", e));
		}

	}

	public void unsetHttpService(HttpService httpService) {
		httpService.unregister("/basiclogin"); //$NON-NLS-1$
	}
}
