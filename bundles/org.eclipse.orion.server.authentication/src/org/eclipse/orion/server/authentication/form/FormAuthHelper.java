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
package org.eclipse.orion.server.authentication.form;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.authentication.Activator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Groups methods to handle session fields for form-based authentication.
 */
public class FormAuthHelper {

	public enum LoginResult {
		OK, FAIL, BLOCKED
	}

	/**
	 * Authenticates user by credentials send in <code>login</code> and
	 * <code>password</password> request parameters. If user credentials are correct session attribute <code>user</code>
	 * is set. If user cannot be logged in
	 * {@link HttpServletResponse#SC_UNAUTHORIZED} error is send.
	 * 
	 * @param req
	 * @param resp
	 * @throws IOException
	 */
	public static LoginResult performAuthentication(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.login"); //$NON-NLS-1$
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		UserInfo userInfo = getUserForCredentials(login, req.getParameter(UserConstants2.PASSWORD));

		if (userInfo != null) {
			if (userInfo.getProperties().containsKey(UserConstants2.BLOCKED)) {
				return LoginResult.BLOCKED;
			}
			String userId = userInfo.getUniqueId();
			if (logger.isInfoEnabled())
				logger.info("Login success: " + login); //$NON-NLS-1$ 
			req.getSession().setAttribute("user", userId); //$NON-NLS-1$

			try {
				// try to store the login timestamp in the user profile
				userInfo.setProperty(UserConstants2.LAST_LOGIN_TIMESTAMP, new Long(System.currentTimeMillis()).toString());
				OrionConfiguration.getMetaStore().updateUser(userInfo);
			} catch (CoreException e) {
				// just log that the login timestamp was not stored
				LogHelper.log(e);
			}
			return LoginResult.OK;
		}
		//don't bother tracing malformed login attempts
		if (login != null)
			logger.info("Login failed: " + login); //$NON-NLS-1$
		return LoginResult.FAIL;
	}

	private static UserInfo getUserForCredentials(String login, String password) {
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, login, false, false);
			if (userInfo != null && userInfo.getProperty(UserConstants2.PASSWORD) != null) {
				String userPassword = userInfo.getProperty(UserConstants2.PASSWORD);
				if (password.equals(userPassword)) {
					return userInfo;
				} else {
					// password verification failed
					return null;
				}
			}
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, 1, "An error occured when validating user credentials", e));
		}
		
		return null;
	}

	/**
	 * Returns <code>true</code> if an unauthorised user can create a new account, 
	 * and <code>false</code> otherwise.
	 */
	public static boolean canAddUsers() {
		//if there is no list of users authorised to create accounts, it means everyone can create accounts
		boolean allowAnonymousAccountCreation = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null) == null;
		return allowAnonymousAccountCreation;
	}

	public static boolean forceEmail() {
		return PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION_FORCE_EMAIL, "false").equalsIgnoreCase("true"); //$NON-NLS-1$ //$NON-NLS-2$;
	}

	/**
	 * Returns a URI to use for account registrations or null if none.
	 * @return String a URI to open when adding user accounts.
	 */
	public static String registrationURI() {
		//if there is an alternate URI to handle registrations retrieve it.
		return PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_REGISTRATION_URI, null);
	}

	public static JSONObject getUserJson(String uid, String contextPath) throws JSONException {
		JSONObject obj = new JSONObject();
		obj.put(UserConstants.KEY_LOGIN, uid);
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, uid, false, false);
			if (userInfo == null) {
				return null;
			}
			obj.put(UserConstants.KEY_UID, uid);
			obj.put(UserConstants.KEY_LOGIN, userInfo.getUserName());
			obj.put(UserConstants.KEY_LOCATION, contextPath + '/' + UserConstants.KEY_USERS + '/' + uid);
			obj.put(UserConstants2.FULL_NAME, userInfo.getFullName());
			if (userInfo.getProperties().containsKey(UserConstants2.LAST_LOGIN_TIMESTAMP)) {
				Long lastLoginTimestamp = Long.parseLong(userInfo.getProperty(UserConstants2.LAST_LOGIN_TIMESTAMP));
				obj.put(UserConstants2.LAST_LOGIN_TIMESTAMP, lastLoginTimestamp);
			}
			if (userInfo.getProperties().containsKey(UserConstants2.DISK_USAGE_TIMESTAMP)) {
				Long diskUsageTimestamp = Long.parseLong(userInfo.getProperty(UserConstants2.DISK_USAGE_TIMESTAMP));
				obj.put(UserConstants2.DISK_USAGE_TIMESTAMP, diskUsageTimestamp);
			}
			if (userInfo.getProperties().containsKey(UserConstants2.DISK_USAGE)) {
				String diskUsage = userInfo.getProperty(UserConstants2.DISK_USAGE);
				obj.put(UserConstants2.DISK_USAGE, diskUsage);
			}
		} catch (IllegalArgumentException e) {
			LogHelper.log(e);
		} catch (CoreException e) {
			LogHelper.log(e);
		}

		return obj;

	}
}
