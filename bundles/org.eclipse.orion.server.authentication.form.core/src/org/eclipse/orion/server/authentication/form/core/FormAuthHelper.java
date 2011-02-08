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
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.orion.server.useradmin.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Groups methods to handle session fields for form-based authentication.
 */
public class FormAuthHelper {

	private static Map<String, IOrionCredentialsService> userStores = new HashMap<String, IOrionCredentialsService>();
	private static IOrionCredentialsService defaultUserAdmin;
	private static boolean everyoneCanCreateUsers;

	static {
		//FIXME preferences are temporary storage, change to proper configuration storage when it's established
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode("org.eclipse.orion.server.configurator"); //$NON-NLS-1$
		everyoneCanCreateUsers = prefs.getBoolean("everyoneCanCreateUsers", true); //$NON-NLS-1$
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
		 * Writes a response in JSON that contains user login.
		 * 
		 * @param user
		 * @param resp
		 * @throws IOException
		 */
	public static void writeLoginResponse(String user, HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		try {
			JSONObject array = new JSONObject();
			array.put("login", user); //$NON-NLS-1$
			resp.getWriter().print(array.toString());
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
			if (logger.isInfoEnabled())
				logger.info("Login success: " + login); //$NON-NLS-1$ 
			req.getSession().setAttribute("user", login); //$NON-NLS-1$
			return true;
		}
		//don't bother tracing malformed login attempts
		if (login != null)
			logger.info("Login failed: " + login); //$NON-NLS-1$
		return false;
	}

	private static User getUserForCredentials(String login, String password, String userStoreId) throws UnsupportedUserStoreException {
		UserAdmin userAdmin = (userStoreId == null) ? defaultUserAdmin : userStores.get(userStoreId);
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

	/**
	 * Returns whether new users can be created in the given store.
	 * @param userStoreId if <code>null</code> checks for default user store
	 * @throws UnsupportedUserStoreException
	 */
	public static boolean canAddUsers(String userStoreId) throws UnsupportedUserStoreException {
		if (!everyoneCanCreateUsers) {
			return false;
		}
		return userStoreId == null ? defaultUserAdmin.canCreateUsers() : userStores.get(userStoreId).canCreateUsers();
	}

	public static Collection<String> getSupportedUserStores() {
		List<String> list = new ArrayList<String>(userStores.keySet());
		list.remove(defaultUserAdmin.getStoreName());
		list.add(0, defaultUserAdmin.getStoreName());
		return list;
	}

	/**
	 * Uses default user store
	 */
	public static boolean canAddUsers() {
		return everyoneCanCreateUsers ? defaultUserAdmin.canCreateUsers() : false;
	}

	public static IOrionCredentialsService getDefaultUserAdmin() {
		return defaultUserAdmin;
	}

	public void setUserAdmin(UserAdmin userAdmin) {
		if (userAdmin instanceof IOrionCredentialsService) {
			IOrionCredentialsService eclipseWebUserAdmin = (IOrionCredentialsService) userAdmin;
			userStores.put(eclipseWebUserAdmin.getStoreName(), eclipseWebUserAdmin);
			if (defaultUserAdmin == null || UserAdminActivator.eclipseWebUsrAdminName.equals(eclipseWebUserAdmin.getStoreName())) {
				defaultUserAdmin = eclipseWebUserAdmin;
			}
		}
	}

	public void unsetUserAdmin(UserAdmin userAdmin) {
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
}
