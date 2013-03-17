/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others 
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
import java.security.Guard;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.user.profile.*;
import org.eclipse.orion.server.useradmin.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Groups methods to handle session fields for form-based authentication.
 */
public class FormAuthHelper {

	private static IOrionCredentialsService userAdmin;
	private static IOrionGuestCredentialsService guestUserAdmin;

	private static IOrionUserProfileService userProfileService;

	private static boolean allowAnonymousAccountCreation;
	private static boolean forceEmailWhileCreatingAccount;

	public enum LoginResult {
		OK, FAIL, BLOCKED
	}

	private static String registrationURI;

	static {
		//if there is no list of users authorised to create accounts, it means everyone can create accounts
		allowAnonymousAccountCreation = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null) == null; //$NON-NLS-1$
		forceEmailWhileCreatingAccount = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION_FORCE_EMAIL, "false").equalsIgnoreCase("true"); //$NON-NLS-1$

		//if there is an alternate URI to handle registrations retrieve it.
		registrationURI = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_REGISTRATION_URI, null);
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
	 * @throws UnsupportedUserStoreException 
	 */
	public static LoginResult performAuthentication(HttpServletRequest req, HttpServletResponse resp) throws IOException, UnsupportedUserStoreException {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.login"); //$NON-NLS-1$
		String login = req.getParameter("login");//$NON-NLS-1$
		User user = getUserForCredentials(login, req.getParameter("password")); //$NON-NLS-1$

		if (user != null) {
			if (user.getBlocked()) {
				return LoginResult.BLOCKED;
			}
			String userId = user.getUid();
			if (logger.isInfoEnabled())
				logger.info("Login success: " + login); //$NON-NLS-1$ 
			req.getSession().setAttribute("user", userId); //$NON-NLS-1$

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
		// First try authenticating them against the guest user service
		if (getGuestUserAdmin() != null) {
			user = getGuestUserAdmin().getUser("login", login); //$NON-NLS-1$
			if (user != null)
				return user;
		}
		// Now try default credentials service
		if (userAdmin == null) {
			throw new UnsupportedUserStoreException();
		}
		user = userAdmin.getUser("login", login); //$NON-NLS-1$
		if (user != null && user.hasCredential("password", password)) { //$NON-NLS-1$
			return user;
		}
		return null;
	}

	private static boolean isGuestUser(User user) {
		return Boolean.TRUE.toString().equals(user.getProperty(UserConstants.KEY_GUEST));
	}

	/**
	 * Returns <code>true</code>ue if an unauthorised user can create a new account, 
	 * and <code>false</code> otherwise.
	 */
	public static boolean canAddUsers() {
		return allowAnonymousAccountCreation ? (userAdmin == null ? false : userAdmin.canCreateUsers()) : false;
	}

	public static boolean forceEmail() {
		return forceEmailWhileCreatingAccount;
	}

	/**
	 * Returns a URI to use for account registrations or null if none.
	 * @return String a URI to open when adding user accounts.
	 */
	public static String registrationURI() {
		return registrationURI;
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

	public static IOrionCredentialsService getGuestUserAdmin() {
		return guestUserAdmin;
	}

	public void setGuestUserAdmin(IOrionGuestCredentialsService service) {
		guestUserAdmin = service;
	}

	public void unsetGuestUserAdmin(IOrionGuestCredentialsService service) {
		if (service.equals(guestUserAdmin)) {
			guestUserAdmin = null;
		}
	}

	public static JSONObject getUserJson(String uid, String contextPath) throws JSONException {
		JSONObject obj = new JSONObject();
		obj.put("login", uid); //$NON-NLS-1$
		try {
			User user = null;
			// Try guest login first
			if (getGuestUserAdmin() != null) {
				user = getGuestUserAdmin().getUser(UserConstants.KEY_UID, uid);
			}
			if (user == null)
				user = userAdmin.getUser(UserConstants.KEY_UID, uid);

			if (user == null) {
				return null;
			}
			// try to add the login timestamp to the user info
			IOrionUserProfileNode generalUserProfile = FormAuthHelper.getUserProfileService().getUserProfileNode(uid, IOrionUserProfileConstants.GENERAL_PROFILE_PART);
			obj.put(UserConstants.KEY_UID, uid);
			obj.put(UserConstants.KEY_LOGIN, user.getLogin());
			obj.put("Location", contextPath + user.getLocation());
			obj.put("Name", user.getName());
			if (generalUserProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, null) != null) {
				Long lastLogin = Long.parseLong(generalUserProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, ""));

				obj.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, lastLogin);
			}
			if (isGuestUser(user)) {
				obj.put(UserConstants.KEY_GUEST, true);
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
}
