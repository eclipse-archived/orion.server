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
package org.eclipse.orion.server.useradmin.servlets;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.useradmin.OrionUserAdmin;
import org.eclipse.orion.server.useradmin.UnsupportedUserStoreException;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.useradmin.Role;

//POST /users/ creates a new user
//GET /users/ gets list of users
//GET /users/[userId] gets user details
//GET /users/[usersId]/roles returns a list of roles for given user
//DELETE /users/[usersId] deletes a user
//DELETE /users/[usersId]/roles removes roles for given a user
//PUT /users/[userId] updates user details
//PUT /users/[usersId]/roles adds roles for given user
public class UserServlet extends OrionServlet {

	private static final long serialVersionUID = -6809742538472682623L;

	public static final String USERS_URI = "/users";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathString = req.getPathInfo();
		String pathSegments[] = (pathString == null) ? new String[0] : pathString.split("\\/");
		if (pathSegments.length == 0) {
			Collection<User> users = getUserAdmin().getUsers();
			try {
				Set<JSONObject> userjsons = new HashSet<JSONObject>();
				for (User user : users) {
					userjsons.add(formJson(user));
				}
				JSONObject json = new JSONObject();
				json.put("users", userjsons);
				writeJSONResponse(req, resp, json);
			} catch (JSONException e) {
				handleException(resp, "Cannot get users list", e);
			}
		} else if (pathSegments.length > 2 && "roles".equals(pathSegments[2])) {
			String login = pathSegments[1];
			try {
				User user = (User) getUserAdmin().getUser("login", login);
				if (user == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found " + login);
					return;
				}
				writeJSONResponse(req, resp, formJson(user).getJSONArray("roles"));
			} catch (JSONException e) {
				handleException(resp, "Cannot get users details", e);
			}
		} else {
			String login = pathSegments[1];
			try {
				User user = (User) getUserAdmin().getUser("login", login);
				if (user == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found " + login);
					return;
				}
				writeJSONResponse(req, resp, formJson(user));
			} catch (JSONException e) {
				handleException(resp, "Cannot get users details", e);
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || "/".equals(pathInfo)) {
			String createError;
			try {
				createError = createUser(req.getParameter("store"), req.getParameter("login"), req.getParameter("name"), req.getParameter("email"), req.getParameter("workspace"), req.getParameter("password"));
			} catch (UnsupportedUserStoreException e) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User store is not available: " + req.getParameter("store"));
				return;
			}

			if (createError != null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, createError);
			}
		}
	}

	private String createUser(String userStore, String login, String name, String email, String workspace, String password) throws UnsupportedUserStoreException {
		if (login == null || login.length() == 0) {
			return "User login is required";
		}
		OrionUserAdmin userAdmin;
		
		userAdmin = (userStore == null || "".equals(userStore)) ? getUserAdmin() : getUserAdmin(userStore);
		
		if (userAdmin.getUser("login", login) != null) {
			return "User " + login + " already exists";
		}
		User newUser = new User(login, name == null ? "" : name, password == null ? "" : password);

		if (userAdmin.createUser(newUser) == null) {
			return "User could not be created";
		}
		try {
			AuthorizationService.addUserRight(newUser.getLogin(), newUser.getLocation());
		} catch (CoreException e) {
			String error = "User rights could not be added";
			LogHelper.log(e.getStatus());
			error += e.getMessage() == null ? "." : (": " + e.getMessage() + ".");
			return error;
		}
		return null;
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathString = req.getPathInfo();

		OrionUserAdmin userAdmin;
		try {
			userAdmin = req.getParameter("store") == null ? getUserAdmin() : getUserAdmin(req.getParameter("store"));
		} catch (UnsupportedUserStoreException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User store not be found: " + req.getParameter("store"));
			return;
		}

		String login = null;
		User user = null;
		String pathSegments[] = pathString.split("\\/");
		if (pathSegments.length > 2 && "roles".equals(pathSegments[2])) {
			login = pathSegments[1];
			user = (User) userAdmin.getUser("login", login);

			if (user == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found: " + login);
				return;
			}
			if (req.getParameter("roles") != null) {
				user.getRoles().clear();
				String roles = req.getParameter("roles");
				if (roles != null) {
					StringTokenizer tokenizer = new StringTokenizer(roles, ",");
					while (tokenizer.hasMoreTokens()) {
						String role = tokenizer.nextToken();
						user.addRole(userAdmin.getRole(role));
					}
				}
			}
		} else if (pathString.startsWith("/")) {
			login = pathString.substring(1);
			user = (User) userAdmin.getUser("login", login);

			if (user == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found");
				return;
			}
			if (req.getParameter("login") != null) {
				user.setLogin(req.getParameter("login"));
			}
			if (req.getParameter("name") != null) {
				user.setName(req.getParameter("name"));
			}
			if (req.getParameter("password") != null) {
				user.setPassword(req.getParameter("password"));
			}
		}
		userAdmin.updateUser(login, user);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathString = req.getPathInfo();

		OrionUserAdmin userAdmin;
		try {
			userAdmin = req.getParameter("store") == null ? getUserAdmin() : getUserAdmin(req.getParameter("store"));
		} catch (UnsupportedUserStoreException e) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User store not be found: " + req.getParameter("store"));
			return;
		}

		String pathSegments[] = pathString.split("\\/");
		if (pathSegments.length > 2 && "roles".equals(pathSegments[2])) {
			String login = pathSegments[1];
			User user = (User) userAdmin.getUser("login", login);
			if (user == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found: " + login);
			}
			if (req.getParameter("roles") != null) {
				String roles = req.getParameter("roles");
				if (roles != null) {
					StringTokenizer tokenizer = new StringTokenizer(roles, ",");
					while (tokenizer.hasMoreTokens()) {
						String role = tokenizer.nextToken();
						user.removeRole(userAdmin.getRole(role));
					}
				}
			}
			userAdmin.updateUser(login, user);
		} else if (pathString.startsWith("/")) {
			String login = pathString.substring(1);
			if (userAdmin.deleteUser((User) userAdmin.getUser("login", login)) == false) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found");
			}
		}

	}

	private OrionUserAdmin getUserAdmin(String userStoreId) throws UnsupportedUserStoreException {
		return UserServiceHelper.getDefault().getUserStore(userStoreId);
	}

	private OrionUserAdmin getUserAdmin() {
		return UserServiceHelper.getDefault().getUserStore();
	}

	private JSONObject formJson(User user) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("login", user.getLogin());
		json.put("name", user.getName());
		Set<String> roles = new HashSet<String>();
		for (Role role : user.getRoles()) {
			roles.add(role.getName());
		}
		json.put("roles", roles);
		return json;
	}
}
