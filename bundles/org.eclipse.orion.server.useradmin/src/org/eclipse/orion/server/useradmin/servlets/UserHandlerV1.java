/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
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
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A user handler for Orion User API v 1.0.
 */
public class UserHandlerV1 extends ServletResourceHandler<String> {

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
		Collection<User> users = getUserAdmin().getUsers();
		Set<JSONObject> userJSONs = new HashSet<JSONObject>();
		URI location = OrionServlet.getURI(req);
		IOrionUserProfileNode userNode = null;
		for (User user : users) {
			URI userLocation = URIUtil.append(location, user.getUid());
			userNode = getUserProfileService().getUserProfileNode(user.getUid(), true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
			userJSONs.add(formJson(user, userNode, userLocation));
		}
		JSONObject json = new JSONObject();
		json.put(UserConstants.KEY_USERS, userJSONs);
		OrionServlet.writeJSONResponse(req, resp, json);
		return true;
	}

	private boolean handleUserGet(HttpServletRequest req, HttpServletResponse resp, String userId) throws IOException, JSONException, ServletException, CoreException {
		User user = (User) getUserAdmin().getUser(UserConstants.KEY_UID, userId);
		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User not found " + userId, null));

		IOrionUserProfileNode userNode = getUserProfileService().getUserProfileNode(userId, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		URI location = OrionServlet.getURI(req);
		OrionServlet.writeJSONResponse(req, resp, formJson(user, userNode, location));
		return true;
	}

	private boolean handleUserReset(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String password = req.getParameter(UserConstants.KEY_PASSWORD);

		if (login == null || login.length() == 0)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User login not specified.", null));

		IOrionCredentialsService userAdmin = getUserAdmin();

		User user = (User) userAdmin.getUser(UserConstants.KEY_LOGIN, login);

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
		String store = req.getParameter(UserConstants.KEY_STORE);
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String name = req.getParameter(ProtocolConstants.KEY_NAME);
		String password = req.getParameter(UserConstants.KEY_PASSWORD);

		if (login == null || login.length() == 0)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User login not specified.", null));

		IOrionCredentialsService userAdmin = getUserAdmin();

		if (userAdmin.getUser(UserConstants.KEY_LOGIN, login) != null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + login + " already exists.", null));

		User newUser = new User(login, name != null ? name : "", password == null ? "" : password);

		newUser = userAdmin.createUser(newUser);

		if (newUser == null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Error creating user: {0}", login), null));
		}

		try {
			AuthorizationService.addUserRight(newUser.getUid(), newUser.getLocation());
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User rights could not be added.", e));
		}

		URI userLocation = URIUtil.append(OrionServlet.getURI(req), newUser.getUid());
		IOrionUserProfileNode userNode = getUserProfileService().getUserProfileNode(newUser.getUid(), true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		OrionServlet.writeJSONResponse(req, resp, formJson(newUser, userNode, userLocation));

		return true;
	}

	private boolean handleUserPut(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException, IOException, CoreException, JSONException {
		JSONObject data = OrionServlet.readJSONRequest(req);

		String store = data.has(UserConstants.KEY_STORE) ? data.getString(UserConstants.KEY_STORE) : null;

		IOrionCredentialsService userAdmin = getUserAdmin();

		User user = (User) userAdmin.getUser(UserConstants.KEY_UID, userId);

		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));

		//users other than admin have to know the old password to set a new one
		if (!isAdmin(req.getRemoteUser())) {
			if (data.has(UserConstants.KEY_PASSWORD) && user.getPassword() != null && (!data.has(UserConstants.KEY_OLD_PASSWORD) || !user.getPassword().equals(data.getString(UserConstants.KEY_OLD_PASSWORD)))) {
				return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid old password", null));
			}
		}

		if (data.has(UserConstants.KEY_LOGIN))
			user.setLogin(data.getString(UserConstants.KEY_LOGIN));
		if (data.has(ProtocolConstants.KEY_NAME))
			user.setName(data.getString(ProtocolConstants.KEY_NAME));
		if (data.has(UserConstants.KEY_PASSWORD))
			user.setPassword(data.getString(UserConstants.KEY_PASSWORD));
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
		String store = req.getParameter(UserConstants.KEY_STORE);

		IOrionCredentialsService userAdmin = getUserAdmin();

		if (userAdmin.deleteUser((User) userAdmin.getUser("uid", userId)) == false) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));
		}
		return true;
	}

	private JSONObject formJson(User user, IOrionUserProfileNode userProfile, URI location) throws JSONException, CoreException {
		JSONObject json = new JSONObject();
		json.put(UserConstants.KEY_UID, user.getUid());
		json.put(ProtocolConstants.KEY_LOCATION, location);
		json.put(ProtocolConstants.KEY_NAME, user.getName());
		json.put(UserConstants.KEY_LOGIN, user.getLogin());
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

		//		Set<String> roles = new HashSet<String>();
		//			for (Role role : user.getRoles()) {
		//				roles.add(role.getName());
		//			}
		//			json.put("roles", roles);

		JSONArray plugins = new JSONArray();
		try {
			JSONObject plugin = new JSONObject();
			URI result = user.getPassword() == null ? new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), "/plugins/user/nopasswordProfilePlugin.html", null, null) : new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), "/plugins/user/userProfilePlugin.html", null, null);
			plugin.put(UserConstants.KEY_PLUGIN_LOCATION, result);
			plugins.put(plugin);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		}
		json.put(UserConstants.KEY_PLUGINS, plugins);
		return json;
	}

	private IOrionCredentialsService getUserAdmin() {
		return UserServiceHelper.getDefault().getUserStore();
	}

	private IOrionUserProfileService getUserProfileService() {
		return UserServiceHelper.getDefault().getUserProfileService();
	}
}
