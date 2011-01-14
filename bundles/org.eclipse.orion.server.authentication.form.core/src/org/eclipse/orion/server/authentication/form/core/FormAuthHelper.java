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
package org.eclipse.orion.server.authentication.form.core;

import org.eclipse.orion.server.useradmin.*;

import org.eclipse.orion.server.core.LogHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Groups methods to handle session fields for form-based authentication.
 * 
 */
public class FormAuthHelper {

	private static IOrionUserAdminRegistry userAdminRegistry;

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
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORM_CORE, "An error occured when creating JSON object for logged in user", e));
		}
	}

	/**
	 * Authenticates user by credentials send in <code>login</code> and
	 * <code>password</password> request parameters. If user credentials are correct session attribute <code>user</code>
	 * is set. If user cannot be logged in
	 * {@link HttpServletResponse.SC_UNAUTHORIZED} error is send.
	 * 
	 * @param req
	 * @param resp
	 * @return
	 * @throws IOException
	 */
	public static boolean performAuthentication(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		User user;
		try {
			user = getUserForCredentials((String) req.getParameter("login"), //$NON-NLS-1$
					req.getParameter("password"), req.getParameter("store"));
		} catch (UnsupportedUserStoreException e) {
			LogHelper.log(e);
			return false;
		} //$NON-NLS-1$
		if (user != null) {
			req.getSession().setAttribute("user", req.getParameter("login")); //$NON-NLS-1$//$NON-NLS-2$
			return true;
		} else {
			return false;
		}
	}

	private static User getUserForCredentials(String login, String password, String userStoreId) throws UnsupportedUserStoreException {
		UserAdmin userAdmin = (userStoreId == null) ? userAdminRegistry.getUserStore() : userAdminRegistry.getUserStore(userStoreId);
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
	 * 
	 * @param userStoreId if <code>null</code> checks for default user store
	 * @return
	 * @throws UnsupportedUserStoreException
	 */
	public static boolean canAddUsers(String userStoreId) throws UnsupportedUserStoreException {
		return userStoreId == null ? userAdminRegistry.getUserStore().canCreateUsers() : userAdminRegistry.getUserStore(userStoreId).canCreateUsers();
	}

	public static Collection<String> getSupportedUserStores() {
		List<String> list = new ArrayList<String>(userAdminRegistry.getSupportedUserStores());
		list.remove(userAdminRegistry.getUserStore().getStoreName());
		list.add(0, userAdminRegistry.getUserStore().getStoreName());
		return list;
	}

	/**
	 * Uses default user store
	 * @return
	 */
	public static boolean canAddUsers() {
		return userAdminRegistry.getUserStore().canCreateUsers();
	}

	public static OrionUserAdmin getDefaultUserAdmin() {
		return userAdminRegistry.getUserStore();
	}

	public void setUserAdminRegistry(IOrionUserAdminRegistry userAdminStore) {
		FormAuthHelper.userAdminRegistry = userAdminStore;
	}

	public void unsetUserAdminRegistry(IOrionUserAdminRegistry userAdminStore) {
		FormAuthHelper.userAdminRegistry = null;
	}

}
