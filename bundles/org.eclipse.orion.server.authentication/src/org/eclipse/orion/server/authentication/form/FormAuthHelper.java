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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.authentication.Activator;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.core.events.IEventService;
import org.eclipse.orion.server.user.profile.*;
import org.eclipse.orion.server.useradmin.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Groups methods to handle session fields for form-based authentication.
 */
public class FormAuthHelper {

	private static IOrionCredentialsService userAdmin;

	private static IOrionUserProfileService userProfileService;

	public enum LoginResult {
		OK, FAIL, BLOCKED
	}

	private static IEventService eventService;

	/**
	 * Authenticates user by credentials send in <code>login</code> and
	 * <code>password</password> request parameters. If user credentials are correct session attribute <code>user</code>
	 * is set. If user cannot be logged in
	 * {@link HttpServletResponse#SC_UNAUTHORIZED} error is send.
	 * 
	 * @param req
	 * @param resp
	 * @throws IOException
	 * @throws UnsupportedUserStoreException 
	 */
	public static LoginResult performAuthentication(HttpServletRequest req, HttpServletResponse resp) throws IOException, UnsupportedUserStoreException {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.login"); //$NON-NLS-1$
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		User user = getUserForCredentials(login, req.getParameter(UserConstants.KEY_PASSWORD));

		if (user != null) {
			if (user.getBlocked()) {
				return LoginResult.BLOCKED;
			}
			String userId = user.getUid();
			if (logger.isInfoEnabled())
				logger.info("Login success: " + login); //$NON-NLS-1$ 
			req.getSession().setAttribute("user", userId); //$NON-NLS-1$

			if (getEventService() != null) {
				JSONObject message = new JSONObject();
				try {
					SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
					Date date = new Date(System.currentTimeMillis());
					message.put("event", "login");
					message.put("published", format.format(date));
					message.put("user", userId);
				} catch (JSONException e1) {
					LogHelper.log(e1);
				}
				getEventService().publish("orion/login", message);
			}

			IOrionUserProfileNode userProfileNode = getUserProfileService().getUserProfileNode(userId, IOrionUserProfileConstants.GENERAL_PROFILE_PART);
			try {
				// try to store the login timestamp in the user profile
				userProfileNode.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, new Long(System.currentTimeMillis()).toString(), false);
				userProfileNode.flush();
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

	private static User getUserForCredentials(String login, String password) throws UnsupportedUserStoreException {
		User user;
		if (userAdmin == null) {
			throw new UnsupportedUserStoreException();
		}
		user = userAdmin.getUser(UserConstants.KEY_LOGIN, login);
		if (user != null && user.hasCredential(UserConstants.KEY_PASSWORD, password)) {
			return user;
		}
		return null;
	}

	/**
	 * Returns <code>true</code>ue if an unauthorised user can create a new account, 
	 * and <code>false</code> otherwise.
	 */
	public static boolean canAddUsers() {
		//if there is no list of users authorised to create accounts, it means everyone can create accounts
		boolean allowAnonymousAccountCreation = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null) == null;
		return allowAnonymousAccountCreation ? (userAdmin == null ? false : userAdmin.canCreateUsers()) : false;
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

	public static IOrionCredentialsService getDefaultUserAdmin() {
		return userAdmin;
	}

	public void setUserAdmin(IOrionCredentialsService userAdmin) {
		FormAuthHelper.userAdmin = userAdmin;
	}

	public void unsetUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin.equals(FormAuthHelper.userAdmin)) {
			FormAuthHelper.userAdmin = null;
		}
	}

	public static JSONObject getUserJson(String uid, String contextPath) throws JSONException {
		JSONObject obj = new JSONObject();
		obj.put(UserConstants.KEY_LOGIN, uid);
		try {
			User user = userAdmin.getUser(UserConstants.KEY_UID, uid);

			if (user == null) {
				return null;
			}
			IOrionUserProfileNode generalUserProfile = FormAuthHelper.getUserProfileService().getUserProfileNode(uid, IOrionUserProfileConstants.GENERAL_PROFILE_PART);
			obj.put(UserConstants.KEY_UID, uid);
			obj.put(UserConstants.KEY_LOGIN, user.getLogin());
			obj.put("Location", contextPath + user.getLocation());
			obj.put("Name", user.getName());
			if (generalUserProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, null) != null) {
				Long lastLogin = Long.parseLong(generalUserProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, ""));
				obj.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, lastLogin);
			}
			if (generalUserProfile.get(IOrionUserProfileConstants.DISK_USAGE_TIMESTAMP, null) != null) {
				Long lastLogin = Long.parseLong(generalUserProfile.get(IOrionUserProfileConstants.DISK_USAGE_TIMESTAMP, ""));
				obj.put(IOrionUserProfileConstants.DISK_USAGE_TIMESTAMP, lastLogin);
			}
			if (generalUserProfile.get(IOrionUserProfileConstants.DISK_USAGE, null) != null) {
				String lastLogin = generalUserProfile.get(IOrionUserProfileConstants.DISK_USAGE, "");
				obj.put(IOrionUserProfileConstants.DISK_USAGE, lastLogin);
			}
		} catch (IllegalArgumentException e) {
			LogHelper.log(e);
		} catch (CoreException e) {
			LogHelper.log(e);
		}

		return obj;

	}

	private static IOrionUserProfileService getUserProfileService() {
		return userProfileService;
	}

	public static void bindUserProfileService(IOrionUserProfileService _userProfileService) {
		userProfileService = _userProfileService;
	}

	public static void unbindUserProfileService(IOrionUserProfileService userProfileService) {
		userProfileService = null;
	}

	private static IEventService getEventService() {
		if (eventService == null) {
			BundleContext context = Activator.getBundleContext();
			ServiceReference<IEventService> eventServiceRef = context.getServiceReference(IEventService.class);
			if (eventServiceRef == null) {
				// Event service not available
				return null;
			}
			eventService = context.getService(eventServiceRef);
			if (eventService == null) {
				// Event service not available
				return null;
			}
		}
		return eventService;
	}
}
