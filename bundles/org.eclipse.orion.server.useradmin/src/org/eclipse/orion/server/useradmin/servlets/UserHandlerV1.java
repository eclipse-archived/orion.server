/*******************************************************************************
 * Copyright (c) 2010, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin.servlets;

import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.OrionConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.internal.server.servlets.workspace.WebWorkspace;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.resources.Base64Counter;
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
	private static final String PATH_EMAIL_CONFIRMATION = "../useremailconfirmation"; //$NON-NLS-1$
	private static final String GUEST_UID_PREFIX = "user"; //$NON-NLS-1$
	private static final boolean requirePassword = false;

	private static final Base64Counter guestUserCounter = new Base64Counter();

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

	private Collection<User> getAllUsers() {
		// For Bug 372270 - changing the admin getUsers() to return a sorted list.
		Collection<User> users = getUserAdmin().getUsers();
		IOrionCredentialsService guestUserAdmin = getGuestUserAdmin();
		if (guestUserAdmin != null) {
			users.addAll(guestUserAdmin.getUsers());
		}
		return users;
	}

	private boolean handleUsersGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, JSONException, CoreException {
		Collection<User> users = getAllUsers();
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
		User user = null;
		// Try guest user admin first
		if (getGuestUserAdmin() != null)
			user = getGuestUserAdmin().getUser(UserConstants.KEY_UID, userId);
		// Fallback to regular user admin
		if (user == null)
			user = getUserAdmin().getUser(UserConstants.KEY_UID, userId);

		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "User not found " + userId, null));

		IOrionUserProfileNode userNode = getUserProfileService().getUserProfileNode(userId, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		URI location = ServletResourceHandler.getURI(req);
		OrionServlet.writeJSONResponse(req, resp, formJson(user, userNode, location, req.getContextPath()));
		return true;
	}

	private boolean handleUserReset(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String password = req.getParameter(UserConstants.KEY_PASSWORD);

		if (login == null || login.length() == 0)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User login not specified.", null));

		// If it's a guest user, do not allow their password to be reset.
		if (getGuestUserAdmin() != null) {
			User user = getGuestUserAdmin().getUser(UserConstants.KEY_LOGIN, login);
			if (user != null)
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "User " + login + "is a guest, cannot reset password.", null));
		}

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
		String uid = null;
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String name = req.getParameter(ProtocolConstants.KEY_NAME);
		String email = req.getParameter(UserConstants.KEY_EMAIL);
		String password = req.getParameter(UserConstants.KEY_PASSWORD);

		boolean isGuestUser = req.getParameter(UserConstants.KEY_GUEST) != null;
		boolean isPasswordRequired = requirePassword;
		boolean isEmailRequired = Boolean.TRUE.toString().equalsIgnoreCase(PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION_FORCE_EMAIL));

		IOrionCredentialsService userAdmin;
		if (isGuestUser) {
			if (!Boolean.TRUE.toString().equals(PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION_GUEST, Boolean.FALSE.toString()))) {
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Guest user creation is not allowed on this server.", null));
			}
			isPasswordRequired = false;
			isEmailRequired = false;
			userAdmin = getGuestUserAdmin();
			if (userAdmin == null) {
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot create guest users. Contact your server administrator.", null));
			}
			uid = login = nextGuestUserUid(userAdmin);
		} else {
			userAdmin = getUserAdmin();
		}

		if (name == null)
			name = login;

		String msg = validateLogin(login, isGuestUser);
		if (msg != null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));

		if (isPasswordRequired && (password == null || password.length() == 0)) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Cannot create user with empty password.", null));
		}

		if (isEmailRequired && (email == null || email.length() == 0)) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User email is mandatory.", null));
		}

		if (userAdmin.getUser(UserConstants.KEY_LOGIN, login) != null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + login + " already exists.", null));
		if (email != null && email.length() > 0) {
			if (!email.contains("@")) //$NON-NLS-1$
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid user email.", null));
			if (userAdmin.getUser(UserConstants.KEY_EMAIL, email) != null)
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Email address already in use: {0}.", email), null));
		}

		//		if (isGuestUser) {
		//			// Before creating a new guest user, remove any excess guest accounts
		//			int maxGuestAccounts = Math.max(0, PreferenceHelper.getInt(ServerConstants.CONFIG_AUTH_USER_CREATION_GUEST_LIMIT, 100) - 1);
		//			deleteGuestAccounts(WebUser.getGuestAccountsToDelete(maxGuestAccounts));
		//		}

		User newUser;
		if (isGuestUser) {
			// Guest users get distinctive UIDs
			newUser = new User(uid, login, name, password);
		} else {
			newUser = new User(login, name, password);
		}
		if (email != null && email.length() > 0) {
			newUser.setEmail(email);
		}
		if (isEmailRequired)
			newUser.setBlocked(true);
		if (isGuestUser)
			newUser.addProperty(UserConstants.KEY_GUEST, Boolean.TRUE.toString());

		newUser = userAdmin.createUser(newUser);

		if (newUser == null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Error creating user: {0}", login), null));
		}

		//persist new user in metadata store
		UserInfo userInfo = new UserInfo();
		userInfo.setUniqueId(newUser.getUid());
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		userInfo.setGuest(isGuestUser);
		Activator r = Activator.getDefault();
		OrionConfiguration.getMetaStore().createUser(userInfo);

		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		//TODO Don't do cleanup as part of creation, it can be separate op (command line tool, etc)
		//		if (isGuestUser) {
		//			// Remove excess guest accounts
		//			int maxGuestAccounts = PreferenceHelper.getInt(ServerConstants.CONFIG_AUTH_USER_CREATION_GUEST_LIMIT, 100);
		//			deleteGuestAccounts(WebUser.getGuestAccountsToDelete(maxGuestAccounts));
		//		}

		if (logger.isInfoEnabled())
			logger.info("Account created: " + login); //$NON-NLS-1$ 

		try {
			//give the user access to their own user profile
			AuthorizationService.addUserRight(newUser.getUid(), newUser.getLocation());
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User rights could not be added.", e));
		}

		URI userLocation = URIUtil.append(ServletResourceHandler.getURI(req), newUser.getUid());
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
	 * Returns the next available guest user id.
	 */
	private String nextGuestUserUid(IOrionCredentialsService userAdmin) {
		synchronized (guestUserCounter) {
			String candidate;
			do {
				candidate = GUEST_UID_PREFIX + guestUserCounter.toString();
				guestUserCounter.increment();
			} while (userAdmin.getUser("key", candidate) == null);
			return candidate;
		}

	}

	//	private void deleteGuestAccounts(Collection<String> uids) {
	//		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account");
	//		IOrionCredentialsService userAdmin = getGuestUserAdmin();
	//		for (String uid : uids) {
	//			try {
	//				User user = userAdmin.getUser(UserConstants.KEY_UID, uid);
	//				WebUser webUser = WebUser.fromUserId(uid);
	//				if (webUser == null) {
	//					if (logger.isInfoEnabled())
	//						logger.info("WebUser " + uid + " could not be found in backing store");
	//				}
	//				if (!userAdmin.deleteUser(user)) {
	//					if (logger.isInfoEnabled())
	//						logger.info("User " + uid + " could not be found in the credential store");
	//				}
	//				deleteUserAndArtifacts(webUser);
	//				if (logger.isInfoEnabled())
	//					logger.info("Removed user " + uid + " (too many guest accounts).");
	//			} catch (CoreException e) {
	//				if (logger.isInfoEnabled())
	//					logger.info("Removing " + uid + " failed.");
	//			}
	//		}
	//	}

	/**
	 * Helper for deleting a WebUser leaving no traces. Removes the WebUser, and all WebWorkspaces 
	 * the user has access to, and any WebProjects therein. All files in the projects are
	 * deleted from the filesystem.<p>This method only produces a consistent backing store when
	 * <code>user</code> is the sole owner of the workspaces and projects they have access to.
	 * If workspaces or projects are shared among users, this method should not be called.
	 * @param user The user to delete.
	 * 
	 * TODO Move into WebUser?
	 */
	private void deleteUserArtifacts(UserInfo user) throws CoreException {
		// Delete filesystem contents
		for (String workspaceId : user.getWorkspaceIds()) {
			WebWorkspace webWorkspace = WebWorkspace.fromId(workspaceId);
			for (WebProject project : webWorkspace.getProjects()) {
				project.deleteContents();
				webWorkspace.removeProject(project);
				project.removeNode();
			}
			webWorkspace.removeNode();
		}
	}

	/**
	 * Validates that the provided login is valid. Login must consistent of alphanumeric characters only for now.
	 * @return <code>null</code> if the login is valid, and otherwise a string message stating the reason
	 * why it is not valid.
	 */
	private String validateLogin(String login, boolean isGuest) {
		if (login == null || login.length() == 0)
			return "User login not specified";
		int length = login.length();
		if (length < USERNAME_MIN_LENGTH)
			return NLS.bind("Username must contain at least {0} characters", USERNAME_MIN_LENGTH);
		if (length > USERNAME_MAX_LENGTH)
			return NLS.bind("Username must contain no more than {0} characters", USERNAME_MAX_LENGTH);
		if (login.equals("ultramegatron"))
			return "Nice try, Mark";

		// Guest usernames can contain a few special characters (eg. +, /)
		if (!isGuest) {
			for (int i = 0; i < length; i++) {
				if (!Character.isLetterOrDigit(login.charAt(i)))
					return NLS.bind("Username {0} contains invalid character ''{1}''", login, login.charAt(i));
			}
		}
		return null;
	}

	private boolean handleUserPut(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException, IOException, CoreException, JSONException {
		JSONObject data = OrionServlet.readJSONRequest(req);

		IOrionCredentialsService userAdmin = null;
		User user = null;
		if (getGuestUserAdmin() != null) {
			userAdmin = getGuestUserAdmin();
			user = userAdmin.getUser(UserConstants.KEY_UID, userId);
		}
		// Fallback to regular user admin
		if (user == null) {
			userAdmin = getUserAdmin();
			user = userAdmin.getUser(UserConstants.KEY_UID, userId);
		}

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
		if (userNode != null) {
			if (data.has("GitMail"))
				userNode.put("GitMail", data.getString("GitMail"), false);
			if (data.has("GitName"))
				userNode.put("GitName", data.getString("GitName"), false);
			userNode.flush();
		}

		if (user.getConfirmationId() != null && !user.getConfirmationId().equals(emailConfirmationid)) {
			try {
				UserEmailUtil.getUtil().sendEmailConfirmation(getURI(req).resolve(PATH_EMAIL_CONFIRMATION), user);
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.INFO, HttpServletResponse.SC_OK, "Confirmation email has been sent to " + user.getEmail(), null));
			} catch (Exception e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Error while sending email" + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ". See http://wiki.eclipse.org/Orion/Server_admin_guide#Email_configuration for email configuration guide."));
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
		IOrionCredentialsService userAdmin = null;
		User user = null;

		boolean isGuest = false;
		if (getGuestUserAdmin() != null) {
			userAdmin = getGuestUserAdmin();
			user = userAdmin.getUser(UserConstants.KEY_UID, userId);
			isGuest = (user != null);
		}
		if (user == null) {
			userAdmin = getUserAdmin();
			user = userAdmin.getUser(UserConstants.KEY_UID, userId);
		}

		// Delete user from credential store
		if (!userAdmin.deleteUser(user)) {
			if (!isGuest) {
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));
			}
		}
		// Delete user from metadata store
		try {
			Activator r = Activator.getDefault();
			final IMetaStore metastore = OrionConfiguration.getMetaStore();
			if (isGuest)
				deleteUserArtifacts(metastore.readUser(userId));
			metastore.deleteUser(userId);
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

		if (userProfile != null) {
			json.put(UserConstants.KEY_LAST_LOGIN_TIMESTAMP, userProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, ""));
			json.put("GitMail", userProfile.get("GitMail", null));
			json.put("GitName", userProfile.get("GitName", null));
		}

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

	private IOrionCredentialsService getGuestUserAdmin() {
		return UserServiceHelper.getDefault().getGuestUserStore();
	}

	private IOrionUserProfileService getUserProfileService() {
		return UserServiceHelper.getDefault().getUserProfileService();
	}
}
