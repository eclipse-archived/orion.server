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
package org.eclipse.orion.server.authentication.form.core;

import java.io.IOException;
import java.util.*;
import javax.servlet.http.*;
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

	private static Map<String, IOrionCredentialsService> userStores = new HashMap<String, IOrionCredentialsService>();
	private static IOrionCredentialsService defaultUserAdmin;
	
	private static IOrionUserProfileService userProfileService;
	
	private static boolean allowAnonymousAccountCreation;

	static {
		//if there is no list of users authorised to create accounts, it means everyone can create accounts
		allowAnonymousAccountCreation = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null) == null; //$NON-NLS-1$
	}

	/**
	 * Returns the name of the user stored in session.
	 * 
	 * @param req
	 * @return authenticated user name or <code>null</code> if user is not
	 *         authenticated.
	 */
	public static String getAuthenticatedUser(HttpServletRequest req) {
		HttpSession s = req.getSession(true);
		if (s.getAttribute("user") != null) { //$NON-NLS-1$
			return (String) s.getAttribute("user"); //$NON-NLS-1$
		}

		return null;
	}

	/**
	 * @param user
	 * @param resp
	 * @throws IOException
	 * @throws CoreException
	 */
	public static void writeLoginResponse(String user, HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		try {
			resp.getWriter().print(FormAuthHelper.getUserJson(user));
		} catch (JSONException e) {
			//can't fail
		}
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
	public static boolean performAuthentication(HttpServletRequest req, HttpServletResponse resp) throws IOException, UnsupportedUserStoreException {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.login"); //$NON-NLS-1$
		String login = req.getParameter("login");//$NON-NLS-1$
		User user = getUserForCredentials(login, req.getParameter("password"), req.getParameter("store")); //$NON-NLS-1$ //$NON-NLS-2$
		
		if (user != null) {
			String actualLogin = user.getUid();
			if (logger.isInfoEnabled())
				logger.info("Login success: " + actualLogin); //$NON-NLS-1$ 
			req.getSession().setAttribute("user", actualLogin); //$NON-NLS-1$
			
			IOrionUserProfileNode userProfileNode =getUserProfileService().getUserProfileNode(actualLogin, IOrionUserProfileConstants.GENERAL_PROFILE_PART);
			try {
				// try to store the login timestamp in the user profile
				userProfileNode.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, new Long(System.currentTimeMillis()).toString(), false);
				userProfileNode.flush();
			} catch (CoreException e) {
				// just log that the login timestamp was not stored
				LogHelper.log(e);
			}
			return true;
		}
		//don't bother tracing malformed login attempts
		if (login != null)
			logger.info("Login failed: " + login); //$NON-NLS-1$
		return false;
	}

	private static User getUserForCredentials(String login, String password, String userStoreId) throws UnsupportedUserStoreException {
		IOrionCredentialsService userAdmin = (userStoreId == null) ? defaultUserAdmin : userStores.get(userStoreId);
		if (userAdmin == null) {
			throw new UnsupportedUserStoreException(userStoreId);
		}
		User user = userAdmin.getUser("login", login); //$NON-NLS-1$
		if (user != null && user.hasCredential("password", password)) { //$NON-NLS-1$
			return user;
		}
		return null;
	}

	public static void performLogout(HttpServletRequest req) {
		HttpSession s = req.getSession(true);
		if (s.getAttribute("user") != null) { //$NON-NLS-1$
			s.removeAttribute("user"); //$NON-NLS-1$
		}
	}

	public static boolean isSupportedUserStore(String userStoreId) {
		return getSupportedUserStores().contains(userStoreId);
	}

	public static Collection<String> getSupportedUserStores() {
		List<String> list = new ArrayList<String>(userStores.keySet());
		list.remove(defaultUserAdmin.getStoreName());
		list.add(0, defaultUserAdmin.getStoreName());
		return list;
	}

	/**
	 * Returns <code>true</code>ue if an unauthorised user can create a new account, 
	 * and <code>false</code> otherwise.
	 */
	public static boolean canAddUsers() {
		return allowAnonymousAccountCreation ? defaultUserAdmin.canCreateUsers() : false;
	}

	public static IOrionCredentialsService getDefaultUserAdmin() {
		return defaultUserAdmin;
	}

	public void setUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin instanceof IOrionCredentialsService) {
			IOrionCredentialsService eclipseWebUserAdmin = (IOrionCredentialsService) userAdmin;
			userStores.put(eclipseWebUserAdmin.getStoreName(), eclipseWebUserAdmin);
			if (defaultUserAdmin == null || UserAdminActivator.eclipseWebUsrAdminName.equals(eclipseWebUserAdmin.getStoreName())) {
				defaultUserAdmin = eclipseWebUserAdmin;
			}
		}
	}

	public void unsetUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin instanceof IOrionCredentialsService) {
			IOrionCredentialsService eclipseWebUserAdmin = (IOrionCredentialsService) userAdmin;
			userStores.remove(eclipseWebUserAdmin.getStoreName());
			if (userAdmin.equals(defaultUserAdmin)) {
				Iterator<IOrionCredentialsService> iterator = userStores.values().iterator();
				if (iterator.hasNext())
					defaultUserAdmin = iterator.next();
			}
		}
	}
	
	public static JSONObject getUserJson(String uid) throws JSONException{
		JSONObject obj = new JSONObject();
			obj.put("login", uid); //$NON-NLS-1$

			try {
				// try to add the login timestamp to the user info
				IOrionUserProfileNode generalUserProfile = FormAuthHelper.getUserProfileService().getUserProfileNode(uid, IOrionUserProfileConstants.GENERAL_PROFILE_PART);
				User user = defaultUserAdmin.getUser(UserConstants.KEY_UID, uid);
				obj.put(UserConstants.KEY_UID, uid);
				obj.put(UserConstants.KEY_LOGIN, user.getLogin());
				obj.put("Location", user.getLocation());
				obj.put("Name", user.getName());
				if(generalUserProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, null)!=null){
					Long lastLogin = Long.parseLong(generalUserProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, ""));
					
					obj.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, lastLogin);
				}
			} catch (IllegalArgumentException e) {
				LogHelper.log(e);
			} catch (CoreException e) {
				LogHelper.log(e);
			}
		
		return obj;
		
	}
	
	public static IOrionUserProfileService getUserProfileService() {
		return userProfileService;
	}
	
	public static void bindUserProfileService(IOrionUserProfileService _userProfileService){
		userProfileService = _userProfileService;
	}
	
	public static void unbindUserProfileService(IOrionUserProfileService userProfileService){
		userProfileService = null;
	}
}
