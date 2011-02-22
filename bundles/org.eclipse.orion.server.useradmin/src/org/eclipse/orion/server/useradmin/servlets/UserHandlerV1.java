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
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.UnsupportedUserStoreException;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A user handler for Orion User API v 1.0.
 */
public class UserHandlerV1 extends ServletResourceHandler<String> {

	private UserServiceHelper userServiceHelper;

	private ServletResourceHandler<IStatus> statusHandler;

	UserHandlerV1(UserServiceHelper userServiceHelper, ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
		this.userServiceHelper = userServiceHelper;
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
		if (userPathInfoParts.length > 2 && userPathInfoParts[2].equals("roles")) {
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

	private boolean handleUsersGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, JSONException {
		Collection<User> users = getUserAdmin().getUsers();
		Set<JSONObject> userjsons = new HashSet<JSONObject>();
		URI location = OrionServlet.getURI(req);
		for (User user : users) {
			URI userLocation = URIUtil.append(location, user.getLogin());
			userjsons.add(formJson(user, userLocation));
		}
		JSONObject json = new JSONObject();
		json.put("users", userjsons);
		OrionServlet.writeJSONResponse(req, resp, json);
		return true;
	}

	private boolean handleUserGet(HttpServletRequest req, HttpServletResponse resp, String userId) throws IOException, JSONException, ServletException {
		User user = (User) getUserAdmin().getUser("login", userId);
		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User not found " + userId, null));

		URI location = OrionServlet.getURI(req);
		OrionServlet.writeJSONResponse(req, resp, formJson(user, location));
		return true;
	}

	private boolean handleUserCreate(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String store = req.getParameter("store");
		String login = req.getParameter("login");
		String name = req.getParameter("name");
		String password = req.getParameter("password");

		if (login == null || login.length() == 0)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User login not specified.", null));

		IOrionCredentialsService userAdmin;
		try {
			userAdmin = (store == null || "".equals(store)) ? getUserAdmin() : getUserAdmin(store);
		} catch (UnsupportedUserStoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User store is not available: " + store, e));
		}

		if (userAdmin.getUser("login", login) != null)
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

	private boolean handleUserPut(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException {
		String store = req.getParameter("store");

		IOrionCredentialsService userAdmin;
		try {
			userAdmin = (store == null || "".equals(store)) ? getUserAdmin() : getUserAdmin(store);
		} catch (UnsupportedUserStoreException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User store is not available: " + store, e));
		}

		User user = (User) userAdmin.getUser("login", userId);

		if (user == null)
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + userId + " could not be found.", null));

		if (req.getParameter("login") != null) {
			user.setLogin(req.getParameter("login"));
		}
		if (req.getParameter("name") != null) {
			user.setName(req.getParameter("name"));
		}
		if (req.getParameter("password") != null) {
			user.setPassword(req.getParameter("password"));
		}

		userAdmin.updateUser(userId, user);
		return true;
	}

	private boolean handleUserDelete(HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException {
		String store = req.getParameter("store");

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

	private JSONObject formJson(User user, URI location) throws JSONException {
		JSONObject json = new JSONObject();
		json.put(ProtocolConstants.KEY_LOCATION, location);
		json.put("login", user.getLogin());
		json.put("name", user.getName());
		Set<String> roles = new HashSet<String>();
		//			for (Role role : user.getRoles()) {
		//				roles.add(role.getName());
		//			}
		//			json.put("roles", roles);

		JSONArray plugins = new JSONArray();
		try {
			JSONObject plugin = new JSONObject();
			URI result = new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), "/plugins/user/userProfilePlugin.html", null, null);
			plugin.put("Url", result);
			plugins.put(plugin);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		}
		json.put("Plugins", plugins);
		return json;
	}

	private IOrionCredentialsService getUserAdmin(String userStoreId) throws UnsupportedUserStoreException {
		return UserServiceHelper.getDefault().getUserStore(userStoreId);
	}

	private IOrionCredentialsService getUserAdmin() {
		return UserServiceHelper.getDefault().getUserStore();
	}
}
