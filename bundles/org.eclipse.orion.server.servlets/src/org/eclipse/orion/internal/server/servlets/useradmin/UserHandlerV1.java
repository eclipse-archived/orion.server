/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others.
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
import org.eclipse.orion.server.core.UserEmailUtil;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A user handler for Orion User API v 1.0.
 */
public class UserHandlerV1 extends ServletResourceHandler<String> {

	/**
	 * The minimum length of a password.
	 */
	private static final int PASSWORD_MIN_LENGTH = 8;

	/**
	 * The rows query parameter from the /users URL
	 */
	private static final String ROWS = "rows"; //$NON-NLS-1$

	/**
	 * The start query parameter from the /users URL
	 */
	private static final String START = "start"; //$NON-NLS-1$

	/**
	 * The maximum length of a username.
	 */
	private static final int USERNAME_MAX_LENGTH = 20;

	/**
	 * The minimum length of a username.
	 */
	private static final int USERNAME_MIN_LENGTH = 3;

	/**
	 * JSON representation key for the users list. The value's data type is a String.
	 */
	public static final String USERS = "Users"; //$NON-NLS-1$

	/**
	 * JSON representation key for the number of users in the entire users list. The value's data type is a Integer.
	 */
	public static final String USERS_LENGTH = "UsersLength"; //$NON-NLS-1$

	/**
	 * JSON representation key for the rows number in the users list. The value's data type is a Integer.
	 */
	public static final String USERS_ROWS = "UsersRows"; //$NON-NLS-1$

	/**
	 * JSON representation key for the start number in the users list. The value's data type is an Integer.
	 */
	public static final String USERS_START = "UsersStart"; //$NON-NLS-1$

	private ServletResourceHandler<IStatus> statusHandler;

	UserHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	private JSONObject formJson(UserInfo userInfo, URI location, String contextPath) throws JSONException, CoreException {
		JSONObject json = new JSONObject();
		json.put(UserConstants.USER_NAME, userInfo.getUserName());
		json.put(UserConstants.FULL_NAME, userInfo.getFullName());
		json.put(UserConstants.LOCATION, contextPath + location.getPath());
		String email = userInfo.getProperty(UserConstants.EMAIL);
		json.put(UserConstants.EMAIL, email);
		boolean emailConfirmed = (email != null && email.length() > 0) ? userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID) == null : false;
		json.put(UserConstants.EMAIL_CONFIRMED, emailConfirmed);
		json.put(UserConstants.HAS_PASSWORD, userInfo.getProperty(UserConstants.PASSWORD) == null ? false : true);

		if (userInfo.getProperty(UserConstants.OAUTH) != null) {
			json.put(UserConstants.OAUTH, userInfo.getProperty(UserConstants.OAUTH));
		}
		if (userInfo.getProperty(UserConstants.OPENID) != null) {
			json.put(UserConstants.OPENID, userInfo.getProperty(UserConstants.OPENID));
		}

		json.put(UserConstants.LAST_LOGIN_TIMESTAMP, userInfo.getProperty(UserConstants.LAST_LOGIN_TIMESTAMP));
		json.put(UserConstants.DISK_USAGE_TIMESTAMP, userInfo.getProperty(UserConstants.DISK_USAGE_TIMESTAMP));
		json.put(UserConstants.DISK_USAGE, userInfo.getProperty(UserConstants.DISK_USAGE));

		return json;
	}

	private List<String> getAllUsers() throws CoreException {
		// For Bug 372270 - changing the admin getUsers() to return a sorted list.
		List<String> users = OrionConfiguration.getMetaStore().readAllUsers();
		Collections.sort(users, new Comparator<String>() {
			@Override
			public int compare(String userId1, String userId2) {
				return userId1.compareTo(userId2);
			}
		});
		return users;
	}

	private String getUniqueEmailConfirmationId() {
		return System.currentTimeMillis() + "-" + Math.random();
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String userPathInfo) throws ServletException {
		// handle calls to /users
		if (userPathInfo == null) {
			try {
				switch (getMethod(request)) {
				case GET:
					return handleUsersGet(request, response);
				case POST:
					JSONObject json = OrionServlet.readJSONRequest(request);
					if (!json.has(UserConstants.RESET)) {
						return handleUserCreate(request, response, json);
					}
				default:
					return false;
				}
			} catch (JSONException e) {
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
			} catch (Exception e) {
				throw new ServletException("Error handling users", e);
			}
		}

		// handle calls to /users/[username]
		String[] userPathInfoParts = userPathInfo.split("\\/", 2);
		String username = userPathInfoParts[1];
		try {
			switch (getMethod(request)) {
			case GET:
				return handleUserGet(request, response, username);
			case PUT:
				return handleUserPut(request, response, username);
			case DELETE:
				return handleUserDelete(request, response, username);
			case POST:
				JSONObject json = OrionServlet.readJSONRequest(request);
				if (json.has(UserConstants.RESET)) {
					return handleUserReset(request, response, username, json);
				}
			default:
				return false;
			}
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		} catch (Exception e) {
			throw new ServletException(NLS.bind("Error handling user: {0}", username), e);
		}
	}

	private boolean handleUserCreate(HttpServletRequest req, HttpServletResponse resp, JSONObject json)
			throws ServletException, IOException, JSONException, CoreException {
		String username = json.has(UserConstants.USER_NAME) ? json.getString(UserConstants.USER_NAME) : null;
		String fullname = json.has(UserConstants.FULL_NAME) ? json.getString(UserConstants.FULL_NAME) : null;
		String email = json.has(UserConstants.EMAIL) ? json.getString(UserConstants.EMAIL) : null;
		String password = json.has(UserConstants.PASSWORD) ? json.getString(UserConstants.PASSWORD) : null;
		String identifier = json.has("identifier") ? json.getString("identifier") : null;

		boolean isEmailRequired = Boolean.TRUE.toString().equalsIgnoreCase(PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION_FORCE_EMAIL));

		if (fullname == null) {
			fullname = username;
		}

		String msg = validateLogin(username);
		if (msg != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
		}

		String passwordMsg = validatePassword(password);
		if (passwordMsg != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, passwordMsg, null));
		}

		if (isEmailRequired && (email == null || email.length() == 0)) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User email is mandatory.", null));
		}

		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, username, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}

		if (userInfo != null) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + username + " already exists.", null));
		}
		if (email != null && email.length() > 0) {
			if (!email.contains("@")) { //$NON-NLS-1$
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid user email.", null));
			}

			userInfo = null;
			try {
				userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.EMAIL, email.toLowerCase(), false, false);
			} catch (CoreException e) {
				LogHelper.log(e);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
			}

			if (userInfo != null) {
				return statusHandler.handleRequest(req, resp,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Email address already in use: {0}.", email), null));
			}
		}

		userInfo = new UserInfo();
		userInfo.setUserName(username);
		userInfo.setFullName(fullname);
		userInfo.setProperty(UserConstants.PASSWORD, password);
		if (identifier != null) {
			userInfo.setProperty(UserConstants.OAUTH, identifier);
		}
		if (email != null && email.length() > 0) {
			userInfo.setProperty(UserConstants.EMAIL, email);
		}
		if (isEmailRequired) {
			userInfo.setProperty(UserConstants.BLOCKED, "true");
			userInfo.setProperty(UserConstants.EMAIL_CONFIRMATION_ID, getUniqueEmailConfirmationId());
		}

		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Error creating user: {0}", username), null));
		}

		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		if (logger.isInfoEnabled()) {
			logger.info("Account created: " + username); //$NON-NLS-1$
		}

		try {
			// give the user access to their own user profile
			String location = UserConstants.LOCATION_USERS_SERVLET + '/' + userInfo.getUniqueId();
			AuthorizationService.addUserRight(userInfo.getUniqueId(), location);
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User rights could not be added.", e));
		}

		URI userLocation = URIUtil.append(ServletResourceHandler.getURI(req), userInfo.getUniqueId());

		if (isEmailRequired) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(req, userInfo);
				return statusHandler.handleRequest(req, resp,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CREATED,
								NLS.bind(
										"Your account {0} has been successfully created. You have been sent an email address verification. Follow the instructions in this email to login to your account.",
										username),
								null));
			} catch (URISyntaxException e) {
				LogHelper.log(e);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
						"Could not send confirmation email to " + userInfo.getProperty(UserConstants.EMAIL), null));
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

		resp.setStatus(HttpServletResponse.SC_CREATED);

		return true;
	}

	private boolean handleUserDelete(HttpServletRequest req, HttpServletResponse resp, String username) throws ServletException {
		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, username, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Removing " + username + " failed.", e));
		}

		if (userInfo == null) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + username + " could not be found.", null));
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
								WorkspaceResourceHandler.removeProject(username, workspaceInfo, projectInfo);
							}
						}
					}
				}
			}
			metastore.deleteUser(username);
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Removing " + username + " failed.", e));
		}
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		if (logger.isInfoEnabled()) {
			logger.info("Account deleted: " + username); //$NON-NLS-1$
		}

		return true;
	}

	private boolean handleUserGet(HttpServletRequest req, HttpServletResponse resp, String username)
			throws IOException, JSONException, ServletException, CoreException {
		UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, username, false, false);

		if (userInfo == null) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "User not found " + username, null));
		}

		URI location = ServletResourceHandler.getURI(req);
		OrionServlet.writeJSONResponse(req, resp, formJson(userInfo, location, req.getContextPath()));
		return true;
	}

	private boolean handleUserPut(HttpServletRequest req, HttpServletResponse resp, String username)
			throws ServletException, IOException, CoreException, JSONException {
		JSONObject data = OrionServlet.readJSONRequest(req);

		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, username, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}

		if (userInfo == null) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + username + " could not be found.", null));
		}
		String emailConfirmationid = userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID);

		// users other than admin have to know the old password to set a new one
		if (!isAdmin(req.getRemoteUser())) {
			if (data.has(UserConstants.PASSWORD) && userInfo.getProperty(UserConstants.PASSWORD) != null && (!data.has(UserConstants.OLD_PASSWORD)
					|| !userInfo.getProperty(UserConstants.PASSWORD).equals(data.getString(UserConstants.OLD_PASSWORD)))) {
				return statusHandler.handleRequest(req, resp,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid old password", null));
			}
		}

		String newPassword = null;
		if (data.has(UserConstants.PASSWORD)) {
			newPassword = data.getString(UserConstants.PASSWORD);
		}
		String passwordMsg = validatePassword(newPassword);
		if (data.has(UserConstants.OLD_PASSWORD) && passwordMsg != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, passwordMsg, null));
		}

		if (data.has(UserConstants.USER_NAME)) {
			userInfo.setUserName(data.getString(UserConstants.USER_NAME));
		}
		if (data.has(UserConstants.FULL_NAME)) {
			userInfo.setFullName(data.getString(UserConstants.FULL_NAME));
		}
		if (data.has(UserConstants.PASSWORD)) {
			userInfo.setProperty(UserConstants.PASSWORD, data.getString(UserConstants.PASSWORD));
		}
		if (data.has(UserConstants.EMAIL)) {
			userInfo.setProperty(UserConstants.EMAIL, data.getString(UserConstants.EMAIL));
		}
		if (data.has(UserConstants.OAUTH)) {
			userInfo.setProperty(UserConstants.OAUTH, data.getString(UserConstants.OAUTH));
		}
		if (data.has(UserConstants.OPENID)) {
			userInfo.setProperty(UserConstants.OPENID, data.getString(UserConstants.OPENID));
		}

		try {
			OrionConfiguration.getMetaStore().updateUser(userInfo);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}

		if (userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID) != null
				&& !userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID).equals(emailConfirmationid)) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(req, userInfo);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.INFO, HttpServletResponse.SC_OK,
						"Confirmation email has been sent to " + userInfo.getProperty(UserConstants.EMAIL), null));
			} catch (Exception e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS,
						"Error while sending email" + (e.getMessage() == null ? "" : ": " + e.getMessage())
								+ ". See http://wiki.eclipse.org/Orion/Server_admin_guide#Email_configuration for email configuration guide."));
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
						"Could not send confirmation email to " + userInfo.getProperty(UserConstants.EMAIL), null));
			}
		}

		return true;
	}

	private boolean handleUserReset(HttpServletRequest req, HttpServletResponse resp, String username, JSONObject json) throws ServletException, JSONException {
		String password = json.getString(UserConstants.PASSWORD);

		if (username == null || username.length() == 0) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User name not specified.", null));
		}

		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, username, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}

		if (userInfo == null) {
			return statusHandler.handleRequest(req, resp,
					new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "User " + username + " could not be found.", null));
		}

		String passwordMsg = validatePassword(password);
		if (passwordMsg != null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, passwordMsg, null));
		}

		try {
			userInfo.setProperty(UserConstants.PASSWORD, password);
			OrionConfiguration.getMetaStore().updateUser(userInfo);
		} catch (CoreException e) {
			LogHelper.log(e);
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}
		return true;
	}

	private boolean handleUsersGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, JSONException, CoreException {
		List<String> users = getAllUsers();
		String startParam = req.getParameter(START);
		String rowsParam = req.getParameter(ROWS);
		int start = 0, rows = 0, count = 0;
		if (startParam != null && !(startParam.length() == 0)) {
			start = Integer.parseInt(startParam);
			if (start < 0) {
				start = 0;
			}
		} else {
			start = 0;
		}
		if (rowsParam != null && !(rowsParam.length() == 0)) {
			rows = Integer.parseInt(rowsParam);
			if (rows < 0) {
				rows = 20; // default is to return 20 at a time
			}
		} else {
			// if there's no start and no rows then return the default first 20 users
			rows = 20; // default is to return 20 at a time
		}
		ArrayList<JSONObject> userJSONs = new ArrayList<JSONObject>();
		URI location = ServletResourceHandler.getURI(req);
		for (String userId : users) {
			if (count >= start + rows) {
				break;
			}
			if (count++ < start) {
				continue;
			}
			URI userLocation = URIUtil.append(location, userId);
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUser(userId);
			userJSONs.add(formJson(userInfo, userLocation, req.getContextPath()));
		}
		JSONObject json = new JSONObject();
		json.put(USERS, userJSONs);
		json.put(USERS_START, start);
		json.put(USERS_ROWS, rows);
		json.put(USERS_LENGTH, users.size());
		OrionServlet.writeJSONResponse(req, resp, json);
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
				if (admin.equals(user)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Validates that the provided login is valid. Login must consistent of alphanumeric characters only for now.
	 *
	 * @return <code>null</code> if the login is valid, and otherwise a string message stating the reason why it is not
	 *         valid.
	 */
	private String validateLogin(String login) {
		if (login == null || login.length() == 0) {
			return "User login not specified";
		}

		String passwordVerificationDisabled = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_DISABLE_ACCOUNT_RULES, "false").toLowerCase(); //$NON-NLS-1$
		if ("false".equals(passwordVerificationDisabled)) {

			int length = login.length();
			if (length < USERNAME_MIN_LENGTH) {
				return NLS.bind("Username must contain at least {0} characters", USERNAME_MIN_LENGTH);
			}
			if (length > USERNAME_MAX_LENGTH) {
				return NLS.bind("Username must contain no more than {0} characters", USERNAME_MAX_LENGTH);
			}
		}

		for (int i = 0; i < login.length(); i++) {
			if (!Character.isLetterOrDigit(login.charAt(i))) {
				return NLS.bind("Username {0} contains invalid character ''{1}''", login, login.charAt(i));
			}
		}
		return null;
	}

	/**
	 * Validates the provided password is valid. The password must be at least PASSWORD_MIN_LENGTH characters long and
	 * contain a mix of alpha and non alpha characters.
	 *
	 * @param password
	 *            The provided password
	 * @return <code>null</code> if the password is valid, and otherwise a string message stating the reason why it is
	 *         not valid.
	 */
	private String validatePassword(String password) {
		if ((password == null || password.length() == 0)) {
			return "Password not specified.";
		}

		String passwordVerificationDisabled = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_DISABLE_ACCOUNT_RULES, "false").toLowerCase(); //$NON-NLS-1$
		if ("false".equals(passwordVerificationDisabled)) {

			if (password.length() < PASSWORD_MIN_LENGTH) {
				return NLS.bind("Password must be at least {0} characters long", PASSWORD_MIN_LENGTH);
			}

			if (Pattern.matches("[a-zA-Z]+", password) || Pattern.matches("[^a-zA-Z]+", password)) {
				return "Password must contain at least one alpha character and one non alpha character";
			}
		}
		return null;
	}
}
