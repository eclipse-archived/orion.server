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
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.user.profile.*;
import org.eclipse.orion.server.useradmin.*;
import org.eclipse.osgi.util.NLS;
import org.json.*;

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
						return handleUserCreate(request, response);
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
			URI userLocation = URIUtil.append(location, user.getLogin());
			userNode = getUserProfileService().getUserProfileNode(user.getLogin(), true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
			userJSONs.add(formJson(user, userNode, userLocation));
		}
		JSONObject json = new JSONObject();
		json.put(UserConstants.KEY_USERS, userJSONs);
		OrionServlet.writeJSONResponse(req, resp, json);
		return true;
	}

	private boolean handleUserGet(HttpServletRequest req, HttpServletResponse resp, String userId) throws IOException, JSONException, ServletException, CoreException {
		User user = (User) getUserAdmin().getUser(UserConstants.KEY_LOGIN, userId);
		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User not found " + userId, null));

		IOrionUserProfileNode userNode = getUserProfileService().getUserProfileNode(userId, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		URI location = OrionServlet.getURI(req);
		OrionServlet.writeJSONResponse(req, resp, formJson(user, userNode, location));
		return true;
	}

	private boolean handleUserCreate(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String store = req.getParameter(UserConstants.KEY_STORE);
		String login = req.getParameter(UserConstants.KEY_LOGIN);
		String name = req.getParameter(ProtocolConstants.KEY_NAME);
		String password = req.getParameter(UserConstants.KEY_PASSWORD);

		if (login == null || login.length() == 0)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User login not specified.", null));

		IOrionCredentialsService userAdmin;
		try {
			userAdmin = (store == null || "".equals(store)) ? getUserAdmin() : getUserAdmin(store);
		} catch (UnsupportedUserStoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User store is not available: " + store, e));
		}

		if (userAdmin.getUser(UserConstants.KEY_LOGIN, login) != null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + login + " already exists.", null));

		User newUser = new User(login, name != null ? name : "", password == null ? "" : password);

		if (userAdmin.createUser(newUser) == null) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Error creating user: {0}", login), null));
		}

		try {
			AuthorizationService.addUserRight(newUser.getLogin(), newUser.getLocation());
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User rights could not be added.", e));
		}

		return true;
	}

	private boolean handleUserPut(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException, IOException, CoreException, JSONException {
		JSONObject data = OrionServlet.readJSONRequest(req);

		String store = data.has(UserConstants.KEY_STORE) ? data.getString(UserConstants.KEY_STORE) : null;

		IOrionCredentialsService userAdmin;
		try {
			userAdmin = (store == null || "".equals(store)) ? getUserAdmin() : getUserAdmin(store);
		} catch (UnsupportedUserStoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User store is not available: " + store, e));
		}

		User user = (User) userAdmin.getUser(UserConstants.KEY_LOGIN, userId);

		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));

		if (data.has(UserConstants.KEY_LOGIN))
			user.setLogin(data.getString(UserConstants.KEY_LOGIN));
		if (data.has(ProtocolConstants.KEY_NAME))
			user.setName(data.getString(ProtocolConstants.KEY_NAME));
		if (data.has(UserConstants.KEY_PASSWORD))
			user.setPassword(data.getString(UserConstants.KEY_PASSWORD));
		userAdmin.updateUser(userId, user);

		IOrionUserProfileNode userNode = getUserProfileService().getUserProfileNode(userId, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		if (data.has("GitMail"))
			userNode.put("GitMail", data.getString("GitMail"), false);
		if (data.has("GitName"))
			userNode.put("GitName", data.getString("GitName"), false);
		userNode.flush();

		return true;
	}

	private boolean handleUserDelete(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException {
		String store = req.getParameter(UserConstants.KEY_STORE);

		IOrionCredentialsService userAdmin;
		try {
			userAdmin = (store == null || "".equals(store)) ? getUserAdmin() : getUserAdmin(store);
		} catch (UnsupportedUserStoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User store is not available: " + store, e));
		}

		if (userAdmin.deleteUser((User) userAdmin.getUser("login", userId)) == false) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));
		}
		return true;
	}

	private JSONObject formJson(User user, IOrionUserProfileNode userProfile, URI location) throws JSONException, CoreException {
		JSONObject json = new JSONObject();
		json.put(ProtocolConstants.KEY_LOCATION, location);
		json.put(ProtocolConstants.KEY_NAME, user.getName());
		json.put(UserConstants.KEY_LOGIN, user.getLogin());

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
			URI result = new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), "/plugins/user/userProfilePlugin.html", null, null);
			plugin.put(UserConstants.KEY_PLUGIN_LOCATION, result);
			plugins.put(plugin);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		}
		json.put(UserConstants.KEY_PLUGINS, plugins);
		return json;
	}

	private IOrionCredentialsService getUserAdmin(String userStoreId) throws UnsupportedUserStoreException {
		return UserServiceHelper.getDefault().getUserStore(userStoreId);
	}

	private IOrionCredentialsService getUserAdmin() {
		return UserServiceHelper.getDefault().getUserStore();
	}

	private IOrionUserProfileService getUserProfileService() {
		return UserServiceHelper.getDefault().getUserProfileService();
	}
}
