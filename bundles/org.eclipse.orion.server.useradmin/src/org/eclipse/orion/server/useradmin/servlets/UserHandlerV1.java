/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin.servlets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.user.profile.*;
import org.eclipse.orion.server.useradmin.*;
import org.eclipse.osgi.util.NLS;
import org.json.*;
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
	private static final String PATH_EMAIL_CONFIRMATION = "../useremailconfirmation";
	private ServletResourceHandler<IStatus> statusHandler;

	UserHandlerV1(UserServiceHelper userServiceHelper, ServletResourceHandler<IStatus> statusHandler) {
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

		// handle calls to /users/[userId]/roles
		String[] userPathInfoParts = userPathInfo.split("\\/", 2);
		if (userPathInfoParts.length > 2 && userPathInfoParts[2].equals(UserConstants.KEY_ROLES)) {
			//use UserRoleHandlerV1
			return false;
		}

		// handle calls to /users/[userId]
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

	private boolean handleUsersGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, JSONException, CoreException {
		// For Bug 372270 - changing the admin getUsers() to return a sorted list.
		Collection<User> users = getUserAdmin().getUsers();
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
		URI location = OrionServlet.getURI(req);
		IOrionUserProfileNode userNode = null;
		for (User user : users) {
			if (count >= start + rows)
				break;
			if (count++ < start)
				continue;
			URI userLocation = URIUtil.append(location, user.getUid());
			userNode = getUserProfileService().getUserProfileNode(user.getUid(), true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
			userJSONs.add(formJson(user, userNode, userLocation, req.getContextPath()));
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
		User user = (User) getUserAdmin().getUser(UserConstants.KEY_UID, userId);
		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User not found " + userId, null));

		IOrionUserProfileNode userNode = getUserProfileService().getUserProfileNode(userId, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		URI location = OrionServlet.getURI(req);
		OrionServlet.writeJSONResponse(req, resp, formJson(user, userNode, location, req.getContextPath()));
		return true;
	}

	private boolean handleUserReset(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String password = req.getParameter(UserConstants.KEY_PASSWORD);

		if (login == null || login.length() == 0)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User login not specified.", null));

		IOrionCredentialsService userAdmin = getUserAdmin();

		User user = userAdmin.getUser(UserConstants.KEY_LOGIN, login);

		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "User " + login + " could not be found.", null));

		if (password == null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Provide new password", null));
		}

		user.setPassword(password);
		IStatus status = userAdmin.updateUser(user.getUid(), user);
		if (!status.isOK()) {
			return statusHandler.handleRequest(req, resp, status);
		}

		return true;
	}

	private boolean handleUserCreate(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, JSONException, CoreException {
		//		String store = req.getParameter(UserConstants.KEY_STORE);
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String name = req.getParameter(ProtocolConstants.KEY_NAME);
		String email = req.getParameter(UserConstants.KEY_EMAIL);

		if (name == null)
			name = login;
		String password = req.getParameter(UserConstants.KEY_PASSWORD);

		String msg = validateLogin(login);
		if (msg != null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));

		if (password == null || password.length() == 0) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Cannot create user with empty password.", null));
		}

		boolean requireEmail = Boolean.TRUE.toString().equalsIgnoreCase(PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION_FORCE_EMAIL));
		if (requireEmail && (email == null || email.length() == 0)) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User email is mandatory.", null));
		}

		IOrionCredentialsService userAdmin = getUserAdmin();

		if (userAdmin.getUser(UserConstants.KEY_LOGIN, login) != null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + login + " already exists.", null));

		if (email != null && email.length() > 0) {
			if (!email.contains("@")) //$NON-NLS-1$
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid user email.", null));
			if (userAdmin.getUser(UserConstants.KEY_EMAIL, email) != null)
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Email address already in use: {0}.", email), null));
		}

		User newUser = new User(login, name, password);
		if (email != null && email.length() > 0) {
			newUser.setEmail(email);
		}
		if (requireEmail)
			newUser.setBlocked(true);

		newUser = userAdmin.createUser(newUser);

		if (newUser == null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Error creating user: {0}", login), null));
		}

		//create user object for workspace service
		WebUser webUser = WebUser.fromUserId(newUser.getUid());
		webUser.setUserName(login);
		webUser.setName(name);
		webUser.save();

		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		if (logger.isInfoEnabled())
			logger.info("Account created: " + login); //$NON-NLS-1$ 

		try {
			//give the user access to their own user profile
			AuthorizationService.addUserRight(newUser.getUid(), newUser.getLocation());
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User rights could not be added.", e));
		}

		URI userLocation = URIUtil.append(OrionServlet.getURI(req), newUser.getUid());
		IOrionUserProfileNode userNode = getUserProfileService().getUserProfileNode(newUser.getUid(), true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		if (newUser.getBlocked()) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(getURI(req).resolve(PATH_EMAIL_CONFIRMATION), newUser);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CREATED, NLS.bind("User {0} has been succesfully created. To log in please confirm your email first.", login), null));
			} catch (URISyntaxException e) {
				LogHelper.log(e);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not send confirmation email to " + newUser.getEmail(), null));
			}
		}
		OrionServlet.writeJSONResponse(req, resp, formJson(newUser, userNode, userLocation, req.getContextPath()));
		if (email != null && email.length() > 0 && UserEmailUtil.getUtil().isEmailConfigured()) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(getURI(req).resolve(PATH_EMAIL_CONFIRMATION), newUser);
			} catch (URISyntaxException e) {
				LogHelper.log(e);
			}
		}

		return true;
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

	private boolean handleUserPut(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException, IOException, CoreException, JSONException {
		JSONObject data = OrionServlet.readJSONRequest(req);

		IOrionCredentialsService userAdmin = getUserAdmin();

		User user = userAdmin.getUser(UserConstants.KEY_UID, userId);

		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));
		String emailConfirmationid = user.getConfirmationId();

		//users other than admin have to know the old password to set a new one
		if (!isAdmin(req.getRemoteUser())) {
			if (data.has(UserConstants.KEY_PASSWORD) && user.getPassword() != null && (!data.has(UserConstants.KEY_OLD_PASSWORD) || !user.getPassword().equals(data.getString(UserConstants.KEY_OLD_PASSWORD)))) {
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid old password", null));
			}
		}

		if (data.has(UserConstants.KEY_OLD_PASSWORD) && (!data.has(UserConstants.KEY_PASSWORD) || data.getString(UserConstants.KEY_PASSWORD).length() == 0)) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Password cannot be empty", null));
		}
		if (data.has(UserConstants.KEY_LOGIN))
			user.setLogin(data.getString(UserConstants.KEY_LOGIN));
		if (data.has(ProtocolConstants.KEY_NAME))
			user.setName(data.getString(ProtocolConstants.KEY_NAME));
		if (data.has(UserConstants.KEY_PASSWORD))
			user.setPassword(data.getString(UserConstants.KEY_PASSWORD));
		if (data.has(UserConstants.KEY_EMAIL)) {
			user.setEmail(data.getString(UserConstants.KEY_EMAIL));
		}

		if (data.has(UserConstants.KEY_PROPERTIES)) {
			JSONObject propertiesObject = data.getJSONObject(UserConstants.KEY_PROPERTIES);
			Iterator<?> propertyIterator = propertiesObject.keys();
			while (propertyIterator.hasNext()) {
				String propertyKey = (String) propertyIterator.next();
				user.addProperty(propertyKey, propertiesObject.getString(propertyKey));
			}
		}
		IStatus status = userAdmin.updateUser(userId, user);
		if (!status.isOK()) {
			return statusHandler.handleRequest(req, resp, status);
		}

		IOrionUserProfileNode userNode = getUserProfileService().getUserProfileNode(userId, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		if (data.has("GitMail"))
			userNode.put("GitMail", data.getString("GitMail"), false);
		if (data.has("GitName"))
			userNode.put("GitName", data.getString("GitName"), false);
		userNode.flush();

		if (user.getConfirmationId() != null && !user.getConfirmationId().equals(emailConfirmationid)) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(getURI(req).resolve(PATH_EMAIL_CONFIRMATION), user);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.INFO, HttpServletResponse.SC_OK, "Confirmation email has been sent to " + user.getEmail(), null));
			} catch (Exception e) {
				LogHelper.log(e);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not send confirmation email to " + user.getEmail(), null));
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
		IOrionCredentialsService userAdmin = getUserAdmin();
		User user = userAdmin.getUser(UserConstants.KEY_UID, userId);
		WebUser webUser = WebUser.fromUserId(user.getUid());
		if (!userAdmin.deleteUser(user)) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));
		}
		try {
			webUser.delete();
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Removing " + userId + " failed.", e));
		}
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		if (logger.isInfoEnabled())
			logger.info("Account deleted: " + userId); //$NON-NLS-1$ 

		return true;
	}

	private JSONObject formJson(User user, IOrionUserProfileNode userProfile, URI location, String contextPath) throws JSONException, CoreException {
		JSONObject json = new JSONObject();
		json.put(UserConstants.KEY_UID, user.getUid());
		json.put(ProtocolConstants.KEY_LOCATION, location);
		json.put(ProtocolConstants.KEY_NAME, user.getName());
		json.put(UserConstants.KEY_LOGIN, user.getLogin());
		json.put(UserConstants.KEY_EMAIL, user.getEmail());
		json.put(UserConstants.KEY_EMAIL_CONFIRMED, user.isEmailConfirmed());
		json.put(UserConstants.KEY_HAS_PASSWORD, user.getPassword() == null ? false : true);

		JSONObject properties = new JSONObject();
		Enumeration<?> userProperties = user.getProperties().keys();
		while (userProperties.hasMoreElements()) {
			String property = (String) userProperties.nextElement();
			properties.put(property, user.getProperty(property));
		}
		json.put(UserConstants.KEY_PROPERTIES, properties);

		json.put(UserConstants.KEY_LAST_LOGIN_TIMESTAMP, userProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, ""));

		json.put("GitMail", userProfile.get("GitMail", null));
		json.put("GitName", userProfile.get("GitName", null));
		
		JSONArray plugins = new JSONArray();
		try {
			JSONObject plugin = new JSONObject();
			URI result = user.getPassword() == null ? new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), contextPath + "/plugins/user/nopasswordProfilePlugin.html", null, null) : new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), contextPath + "/plugins/user/userProfilePlugin.html", null, null);
			plugin.put(UserConstants.KEY_PLUGIN_LOCATION, result);
			plugins.put(plugin);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		}

		return json;
	}

	private IOrionCredentialsService getUserAdmin() {
		return UserServiceHelper.getDefault().getUserStore();
	}

	private IOrionUserProfileService getUserProfileService() {
		return UserServiceHelper.getDefault().getUserProfileService();
	}
}
