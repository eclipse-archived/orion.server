/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.useradmin;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WorkspaceResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.eclipse.orion.server.useradmin.UserEmailUtil;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A user handler for Orion User API v 1.0.
 */
public class UserHandlerV1 extends ServletResourceHandler<String> {

	/**
	 * The minimum length of a username.
	 */
	private static final int USERNAME_MIN_LENGTH = 3;
	/**
	 * The maximum length of a username.
	 */
	private static final int USERNAME_MAX_LENGTH = 20;
	/**
	 * The minimum length of a password.
	 */
	private static final int PASSWORD_MIN_LENGTH = 8;

	private ServletResourceHandler<IStatus> statusHandler;

	UserHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String userPathInfo) throws ServletException {
		// handle calls to /users
		if (userPathInfo == null) {
			try {
				switch (getMethod(request)) {
					case GET :
						return handleUsersGet(request, response);
					case POST :
						return request.getParameter(UserConstants.KEY_RESET) == null ? handleUserCreate(request, response) : handleUserReset(request, response);
					default :
						return false;
				}
			} catch (JSONException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
			} catch (Exception e) {
				throw new ServletException("Error handling users", e);
			}
		}

		// handle calls to /users/[userId]
		String[] userPathInfoParts = userPathInfo.split("\\/", 2);
		String userId = userPathInfoParts[1];
		try {
			switch (getMethod(request)) {
				case GET :
					return handleUserGet(request, response, userId);
				case PUT :
					return handleUserPut(request, response, userId);
				case DELETE :
					return handleUserDelete(request, response, userId);
				default :
					return false;
			}
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		} catch (Exception e) {
			throw new ServletException(NLS.bind("Error handling user: {0}", userId), e);
		}
	}

	private List<String> getAllUsers() throws CoreException {
		// For Bug 372270 - changing the admin getUsers() to return a sorted list.
		List<String> users = OrionConfiguration.getMetaStore().readAllUsers();
		Collections.sort(users, new Comparator<String>() {
			public int compare(String userId1, String userId2) {
				return userId1.compareTo(userId2);
			}
		});
		return users;
	}

	private boolean handleUsersGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, JSONException, CoreException {
		List<String> users = getAllUsers();
		String startParam = req.getParameter(UserConstants.KEY_START);
		String rowsParam = req.getParameter(UserConstants.KEY_ROWS);
		boolean noStartParam = true;
		int start = 0, rows = 0, count = 0;
		if (startParam != null && !(startParam.length() == 0)) {
			start = Integer.parseInt(startParam);
			if (start < 0)
				start = 0;
			noStartParam = false;
		} else {
			start = 0;
		}
		if (rowsParam != null && !(rowsParam.length() == 0)) {
			rows = Integer.parseInt(rowsParam);
			if (rows < 0)
				rows = 200; // default is to return 200 at a time
		} else {
			// if there's no start and no rows then return the entire list to be backwards compatible
			if (noStartParam)
				rows = users.size(); // Return the full set of users
			else
				rows = 200; // default is to return 200 at a time
		}
		ArrayList<JSONObject> userJSONs = new ArrayList<JSONObject>();
		URI location = ServletResourceHandler.getURI(req);
		for (String userId : users) {
			if (count >= start + rows)
				break;
			if (count++ < start)
				continue;
			URI userLocation = URIUtil.append(location, userId);
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUser(userId);
			userJSONs.add(formJson(userInfo, userLocation, req.getContextPath()));
		}
		JSONObject json = new JSONObject();
		json.put(UserConstants.KEY_USERS, userJSONs);
		json.put(UserConstants.KEY_USERS_START, start);
		json.put(UserConstants.KEY_USERS_ROWS, rows);
		json.put(UserConstants.KEY_USERS_LENGTH, users.size());
		OrionServlet.writeJSONResponse(req, resp, json);
		return true;
	}

	private boolean handleUserGet(HttpServletRequest req, HttpServletResponse resp, String userId) throws IOException, JSONException, ServletException, CoreException {
		UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, userId, false, false);

		if (userInfo == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "User not found " + userId, null));

		URI location = ServletResourceHandler.getURI(req);
		OrionServlet.writeJSONResponse(req, resp, formJson(userInfo, location, req.getContextPath()));
		return true;
	}

	private boolean handleUserReset(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String password = req.getParameter(UserConstants2.PASSWORD);

		if (login == null || login.length() == 0)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User login not specified.", null));

		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, login, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}

		if (userInfo == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "User " + login + " could not be found.", null));

		String passwordMsg = validatePassword(password);
		if (passwordMsg != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, passwordMsg, null));
		}

		try {
			userInfo.setProperty(UserConstants2.PASSWORD, password);
			OrionConfiguration.getMetaStore().updateUser(userInfo);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}
		return true;
	}

	private boolean handleUserCreate(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, JSONException, CoreException {
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String name = req.getParameter(UserConstants2.FULL_NAME);
		String email = req.getParameter(UserConstants2.EMAIL);
		String password = req.getParameter(UserConstants2.PASSWORD);
		String identifier = req.getParameter("identifier");

		boolean isEmailRequired = Boolean.TRUE.toString().equalsIgnoreCase(PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION_FORCE_EMAIL));

		if (name == null) {
			name = login;
		}

		String msg = validateLogin(login);
		if (msg != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}

		String passwordMsg = validatePassword(password);
		if (passwordMsg != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, passwordMsg, null));
		}

		if (isEmailRequired && (email == null || email.length() == 0)) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User email is mandatory.", null));
		}

		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, login, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}

		if (userInfo != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + login + " already exists.", null));
		}
		if (email != null && email.length() > 0) {
			if (!email.contains("@")) { //$NON-NLS-1$
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid user email.", null));
			}

			userInfo = null;
			try {
				userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.EMAIL, email.toLowerCase(), false, false);
			} catch (CoreException e) {
				LogHelper.log(e);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
			}

			if (userInfo != null) {
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Email address already in use: {0}.", email), null));
			}
		}

		userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		userInfo.setProperty(UserConstants2.PASSWORD, password);
		if (identifier != null) {
			userInfo.setProperty(UserConstants2.OAUTH, identifier);
		}
		if (email != null && email.length() > 0) {
			userInfo.setProperty(UserConstants2.EMAIL, email);
		}
		if (isEmailRequired) {
			userInfo.setProperty(UserConstants2.BLOCKED, "true");
			userInfo.setProperty(UserConstants2.EMAIL_CONFIRMATION_ID, getUniqueEmailConfirmationId());
		}

		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Error creating user: {0}", login), null));
		}

		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		if (logger.isInfoEnabled())
			logger.info("Account created: " + login); //$NON-NLS-1$ 

		try {
			//give the user access to their own user profile
			String location = '/' + UserConstants.KEY_USERS + '/' + userInfo.getUniqueId();
			AuthorizationService.addUserRight(userInfo.getUniqueId(), location);
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User rights could not be added.", e));
		}

		URI userLocation = URIUtil.append(ServletResourceHandler.getURI(req), userInfo.getUniqueId());

		if (isEmailRequired) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(req, userInfo);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CREATED, NLS.bind("Your account {0} has been successfully created. You have been sent an email address verification. Follow the instructions in this email to login to your Orion account.", login), null));
			} catch (URISyntaxException e) {
				LogHelper.log(e);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not send confirmation email to " + userInfo.getProperty(UserConstants2.EMAIL), null));
			}
		}
		OrionServlet.writeJSONResponse(req, resp, formJson(userInfo, userLocation, req.getContextPath()));
		if (email != null && email.length() > 0 && UserEmailUtil.getUtil().isEmailConfigured()) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(req, userInfo);
			} catch (URISyntaxException e) {
				LogHelper.log(e);
			}
		}

		return true;
	}

	private String getUniqueEmailConfirmationId() {
		return System.currentTimeMillis() + "-" + Math.random();
	}

	/**
	 * Validates that the provided login is valid. Login must consistent of alphanumeric characters only for now.
	 * @return <code>null</code> if the login is valid, and otherwise a string message stating the reason
	 * why it is not valid.
	 */
	private String validateLogin(String login) {
		if (login == null || login.length() == 0)
			return "User login not specified";
		int length = login.length();
		if (length < USERNAME_MIN_LENGTH)
			return NLS.bind("Username must contain at least {0} characters", USERNAME_MIN_LENGTH);
		if (length > USERNAME_MAX_LENGTH)
			return NLS.bind("Username must contain no more than {0} characters", USERNAME_MAX_LENGTH);
		if (login.equals("ultramegatron"))
			return "Nice try, Mark";

		for (int i = 0; i < length; i++) {
			if (!Character.isLetterOrDigit(login.charAt(i)))
				return NLS.bind("Username {0} contains invalid character ''{1}''", login, login.charAt(i));
		}
		return null;
	}

	/**
	 * Validates the provided password is valid. The password must be at least PASSWORD_MIN_LENGTH characters
	 * long and contain a mix of alpha and non alpha characters.
	 * @param password The provided password
	 * @return <code>null</code> if the password is valid, and otherwise a string message stating the reason
	 * why it is not valid.
	 */
	private String validatePassword(String password) {
		if ((password == null || password.length() == 0)) {
			return "Password not specified.";
		}

		if (password.length() < PASSWORD_MIN_LENGTH) {
			return NLS.bind("Password must be at least {0} characters long", PASSWORD_MIN_LENGTH);
		}

		if (Pattern.matches("[a-zA-Z]+", password) || Pattern.matches("[^a-zA-Z]+", password)) {
			return "Password must contain at least one alpha character and one non alpha character";
		}

		return null;
	}

	private boolean handleUserPut(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException, IOException, CoreException, JSONException {
		JSONObject data = OrionServlet.readJSONRequest(req);

		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, userId, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}

		if (userInfo == null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));
		}
		String emailConfirmationid = userInfo.getProperty(UserConstants2.EMAIL_CONFIRMATION_ID);

		//users other than admin have to know the old password to set a new one
		if (!isAdmin(req.getRemoteUser())) {
			if (data.has(UserConstants2.PASSWORD) && userInfo.getProperty(UserConstants2.PASSWORD) != null && (!data.has(UserConstants.KEY_OLD_PASSWORD) || !userInfo.getProperty(UserConstants2.PASSWORD).equals(data.getString(UserConstants.KEY_OLD_PASSWORD)))) {
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid old password", null));
			}
		}

		String newPassword = null;
		if (data.has(UserConstants2.PASSWORD)) {
			newPassword = data.getString(UserConstants2.PASSWORD);
		}
		String passwordMsg = validatePassword(newPassword);
		if (data.has(UserConstants.KEY_OLD_PASSWORD) && passwordMsg != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, passwordMsg, null));
		}

		if (data.has(UserConstants.KEY_LOGIN)) {
			userInfo.setUserName(data.getString(UserConstants.KEY_LOGIN));
		}
		if (data.has(UserConstants2.FULL_NAME)) {
			userInfo.setFullName(data.getString(UserConstants2.FULL_NAME));
		}
		if (data.has(UserConstants2.PASSWORD)) {
			userInfo.setProperty(UserConstants2.PASSWORD, data.getString(UserConstants2.PASSWORD));
		}
		if (data.has(UserConstants2.EMAIL)) {
			userInfo.setProperty(UserConstants2.EMAIL, data.getString(UserConstants2.EMAIL));
		}
		if (data.has(UserConstants2.OAUTH)) {
			userInfo.setProperty(UserConstants2.OAUTH, data.getString(UserConstants2.OAUTH));
		}
		if (data.has(UserConstants2.OPENID)) {
			userInfo.setProperty(UserConstants2.OPENID, data.getString(UserConstants2.OPENID));
		}

		if (data.has(UserConstants.KEY_PROPERTIES)) {
			JSONObject propertiesObject = data.getJSONObject(UserConstants.KEY_PROPERTIES);
			Iterator<?> propertyIterator = propertiesObject.keys();
			while (propertyIterator.hasNext()) {
				String propertyKey = (String) propertyIterator.next();
				userInfo.setProperty(propertyKey, propertiesObject.getString(propertyKey));
			}
		}

		try {
			OrionConfiguration.getMetaStore().updateUser(userInfo);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}

		if (userInfo.getProperty(UserConstants2.EMAIL_CONFIRMATION_ID) != null && !userInfo.getProperty(UserConstants2.EMAIL_CONFIRMATION_ID).equals(emailConfirmationid)) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(req, userInfo);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.INFO, HttpServletResponse.SC_OK, "Confirmation email has been sent to " + userInfo.getProperty(UserConstants2.EMAIL), null));
			} catch (Exception e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Error while sending email" + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ". See http://wiki.eclipse.org/Orion/Server_admin_guide#Email_configuration for email configuration guide."));
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not send confirmation email to " + userInfo.getProperty(UserConstants2.EMAIL), null));
			}
		}

		return true;
	}

	/**
	 * Returns true if this user is an administrator, and false otherwise
	 */
	private boolean isAdmin(String user) {
		String creators = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null);
		if (creators != null) {
			String[] admins = creators.split(",");
			for (String admin : admins) {
				if (admin.equals(user))
					return true;
			}
		}
		return false;
	}

	private boolean handleUserDelete(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException {
		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, userId, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Removing " + userId + " failed.", e));
		}

		if (userInfo == null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));
		}

		// Delete user from metadata store
		try {
			@SuppressWarnings("unused")
			Activator r = Activator.getDefault();
			final IMetaStore metastore = OrionConfiguration.getMetaStore();
			if (userInfo.getWorkspaceIds().size() > 0) {
				for (String workspaceId : userInfo.getWorkspaceIds()) {
					WorkspaceInfo workspaceInfo = metastore.readWorkspace(workspaceId);
					if (workspaceInfo.getProjectNames().size() > 0) {
						for (String projectName : workspaceInfo.getProjectNames()) {
							ProjectInfo projectInfo = metastore.readProject(workspaceId, projectName);
							if (projectInfo != null) {
								WorkspaceResourceHandler.removeProject(userId, workspaceInfo, projectInfo);
							}
						}
					}
				}
			}
			metastore.deleteUser(userId);
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Removing " + userId + " failed.", e));
		}
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		if (logger.isInfoEnabled())
			logger.info("Account deleted: " + userId); //$NON-NLS-1$ 

		return true;
	}

	private JSONObject formJson(UserInfo userInfo, URI location, String contextPath) throws JSONException, CoreException {
		JSONObject json = new JSONObject();
		json.put(UserConstants.KEY_UID, userInfo.getUniqueId());
		json.put(UserConstants.KEY_LOCATION, location);
		json.put(UserConstants2.FULL_NAME, userInfo.getFullName());
		json.put(UserConstants.KEY_LOGIN, userInfo.getUserName());
		String email = userInfo.getProperty(UserConstants2.EMAIL);
		json.put(UserConstants2.EMAIL, email);
		boolean emailConfirmed = (email != null && email.length() > 0) ? userInfo.getProperty(UserConstants2.EMAIL_CONFIRMATION_ID) == null : false;
		json.put(UserConstants.KEY_EMAIL_CONFIRMED, emailConfirmed);
		json.put(UserConstants.KEY_HAS_PASSWORD, userInfo.getProperty(UserConstants2.PASSWORD) == null ? false : true);

		if (userInfo.getProperty(UserConstants2.OAUTH) != null) {
			json.put(UserConstants2.OAUTH, userInfo.getProperty(UserConstants2.OAUTH));
		}
		if (userInfo.getProperty(UserConstants2.OPENID) != null) {
			json.put(UserConstants2.OPENID, userInfo.getProperty(UserConstants2.OPENID));
		}

		json.put(UserConstants2.LAST_LOGIN_TIMESTAMP, userInfo.getProperty(UserConstants2.LAST_LOGIN_TIMESTAMP));
		json.put(UserConstants2.DISK_USAGE_TIMESTAMP, userInfo.getProperty(UserConstants2.DISK_USAGE_TIMESTAMP));
		json.put(UserConstants2.DISK_USAGE, userInfo.getProperty(UserConstants2.DISK_USAGE));

		JSONArray plugins = new JSONArray();
		try {
			JSONObject plugin = new JSONObject();
			URI result = userInfo.getProperty(UserConstants2.PASSWORD) == null ? new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), contextPath + "/plugins/user/nopasswordProfilePlugin.html", null, null) : new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), contextPath + "/plugins/user/userProfilePlugin.html", null, null);
			plugin.put(UserConstants.KEY_PLUGIN_LOCATION, result);
			plugins.put(plugin);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		}

		return json;
	}
}
