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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.useradmin.UserServiceHelper;

/**
 * A user role handler for Orion User API v 1.0.
 */
public class UserRoleHandlerV1 extends ServletResourceHandler<String> {

	private UserServiceHelper userServiceHelper;

	private ServletResourceHandler<IStatus> statusHandler;

	UserRoleHandlerV1(UserServiceHelper userServiceHelper, ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
		this.userServiceHelper = userServiceHelper;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String userPathInfo) throws ServletException {
		return false;
	}

	//	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String userPathInfo) throws ServletException {
	//		
	//		String userId = userPathInfo.split("\\/")[1];
	//		if (UserServiceHelper.getDefault().getUserProfileService().getUserProfileNode(userId, false) == null)
	//		
	//		
	//		try {
	//			switch (getMethod(request)) {
	//				case GET :
	//					return handleGet(request, response, dir);
	//				case PUT :
	//					return handlePut(request, response, dir);
	//				case POST :
	//					return handlePost(request, response, dir);
	//				case DELETE :
	//					return handleDelete(request, response, dir);
	//			}
	//		} catch (JSONException e) {
	//			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
	//		} catch (Exception e) {
	//			throw new ServletException(NLS.bind("Error retrieving user: {0}", dir), e);
	//		}
	//		return false;
	//	}

	//	@Override
	//	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
	//		String pathString = req.getPathInfo();
	//		String pathSegments[] = (pathString == null) ? new String[0] : pathString.split("\\/");
	//		if (pathSegments.length == 0) {
	//			Collection<User> users = getUserAdmin().getUsers();
	//			try {
	//				Set<JSONObject> userjsons = new HashSet<JSONObject>();
	//				for (User user : users) {
	//					userjsons.add(formJson(user));
	//				}
	//				JSONObject json = new JSONObject();
	//				json.put("users", userjsons);
	//				writeJSONResponse(req, resp, json);
	//			} catch (JSONException e) {
	//				handleException(resp, "Cannot get users list", e);
	//			}
	//		} else if (pathSegments.length > 2 && "roles".equals(pathSegments[2])) {
	//			String login = pathSegments[1];
	//			try {
	//				User user = (User) getUserAdmin().getUser("login", login);
	//				if (user == null) {
	//					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found " + login);
	//					return;
	//				}
	//				writeJSONResponse(req, resp, formJson(user).getJSONArray("roles"));
	//			} catch (JSONException e) {
	//				handleException(resp, "Cannot get users details", e);
	//			}
	//		} else {
	//			String login = pathSegments[1];
	//			try {
	//				User user = (User) getUserAdmin().getUser("login", login);
	//				if (user == null) {
	//					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found " + login);
	//					return;
	//				}
	//				writeJSONResponse(req, resp, formJson(user));
	//			} catch (JSONException e) {
	//				handleException(resp, "Cannot get users details", e);
	//			}
	//		}
	//	}
	//
	//	@Override
	//	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
	//		String pathInfo = req.getPathInfo();
	//		if (pathInfo == null || "/".equals(pathInfo)) {
	//			String createError;
	//			try {
	//				createError = createUser(req.getParameter("store"), req.getParameter("login"), req.getParameter("name"), req.getParameter("email"), req.getParameter("workspace"), req.getParameter("password"));
	//			} catch (UnsupportedUserStoreException e) {
	//				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User store is not available: " + req.getParameter("store"));
	//				return;
	//			}
	//
	//			if (createError != null) {
	//				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, createError);
	//			}
	//		}
	//	}
	//
	//	private String createUser(String userStore, String login, String name, String email, String workspace, String password) throws UnsupportedUserStoreException {
	//		if (login == null || login.length() == 0) {
	//			return "User login is required";
	//		}
	//		IOrionCredentialsService userAdmin;
	//		
	//		userAdmin = (userStore == null || "".equals(userStore)) ? getUserAdmin() : getUserAdmin(userStore);
	//		
	//		if (userAdmin.getUser("login", login) != null) {
	//			return "User " + login + " already exists";
	//		}
	//		User newUser = new User(login, name == null ? "" : name, password == null ? "" : password);
	//
	//		if (userAdmin.createUser(newUser) == null) {
	//			return "User could not be created";
	//		}
	//		try {
	//			AuthorizationService.addUserRight(newUser.getLogin(), newUser.getLocation());
	//		} catch (CoreException e) {
	//			String error = "User rights could not be added";
	//			LogHelper.log(e.getStatus());
	//			error += e.getMessage() == null ? "." : (": " + e.getMessage() + ".");
	//			return error;
	//		}
	//		return null;
	//	}
	//
	//	@Override
	//	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
	//		String pathString = req.getPathInfo();
	//
	//		IOrionCredentialsService userAdmin;
	//		try {
	//			userAdmin = req.getParameter("store") == null ? getUserAdmin() : getUserAdmin(req.getParameter("store"));
	//		} catch (UnsupportedUserStoreException e) {
	//			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User store not be found: " + req.getParameter("store"));
	//			return;
	//		}
	//
	//		String login = null;
	//		User user = null;
	//		String pathSegments[] = pathString.split("\\/");
	//		if (pathSegments.length > 2 && "roles".equals(pathSegments[2])) {
	//			login = pathSegments[1];
	//			user = (User) userAdmin.getUser("login", login);
	//
	//			if (user == null) {
	//				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found: " + login);
	//				return;
	//			}
	//			if (req.getParameter("roles") != null) {
	//				user.getRoles().clear();
	//				String roles = req.getParameter("roles");
	//				if (roles != null) {
	//					StringTokenizer tokenizer = new StringTokenizer(roles, ",");
	//					while (tokenizer.hasMoreTokens()) {
	//						String role = tokenizer.nextToken();
	//						user.addRole(userAdmin.getRole(role));
	//					}
	//				}
	//			}
	//		} else if (pathString.startsWith("/")) {
	//			login = pathString.substring(1);
	//			user = (User) userAdmin.getUser("login", login);
	//
	//			if (user == null) {
	//				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found");
	//				return;
	//			}
	//			if (req.getParameter("login") != null) {
	//				user.setLogin(req.getParameter("login"));
	//			}
	//			if (req.getParameter("name") != null) {
	//				user.setName(req.getParameter("name"));
	//			}
	//			if (req.getParameter("password") != null) {
	//				user.setPassword(req.getParameter("password"));
	//			}
	//		}
	//		userAdmin.updateUser(login, user);
	//	}
	//
	//	@Override
	//	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
	//		String pathString = req.getPathInfo();
	//
	//		IOrionCredentialsService userAdmin;
	//		try {
	//			userAdmin = req.getParameter("store") == null ? getUserAdmin() : getUserAdmin(req.getParameter("store"));
	//		} catch (UnsupportedUserStoreException e) {
	//			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User store not be found: " + req.getParameter("store"));
	//			return;
	//		}
	//
	//		String pathSegments[] = pathString.split("\\/");
	//		if (pathSegments.length > 2 && "roles".equals(pathSegments[2])) {
	//			String login = pathSegments[1];
	//			User user = (User) userAdmin.getUser("login", login);
	//			if (user == null) {
	//				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found: " + login);
	//			}
	//			if (req.getParameter("roles") != null) {
	//				String roles = req.getParameter("roles");
	//				if (roles != null) {
	//					StringTokenizer tokenizer = new StringTokenizer(roles, ",");
	//					while (tokenizer.hasMoreTokens()) {
	//						String role = tokenizer.nextToken();
	//						user.removeRole(userAdmin.getRole(role));
	//					}
	//				}
	//			}
	//			userAdmin.updateUser(login, user);
	//		} else if (pathString.startsWith("/")) {
	//			String login = pathString.substring(1);
	//			if (userAdmin.deleteUser((User) userAdmin.getUser("login", login)) == false) {
	//				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found");
	//			}
	//		}
	//
	//	}
	//
	//	private IOrionCredentialsService getUserAdmin(String userStoreId) throws UnsupportedUserStoreException {
	//		return UserServiceHelper.getDefault().getUserStore(userStoreId);
	//	}
	//
	//	private IOrionCredentialsService getUserAdmin() {
	//		return UserServiceHelper.getDefault().getUserStore();
	//	}
	//	
	//	private IOrionUserProfileService getUserProfileService(){
	//		return UserServiceHelper.getDefault().getUserProfileService();
	//	}
	//	
	////	private JSONObject getProfile(User user) {
	////		JSONObject json = new JSONObject();
	////		
	////		IOrionUserProfileNode[] partNodes = getUserProfileService().getUserProfileParts(user.getLogin());
	////		
	////		for(IOrionUserProfileNode partNode : partNodes){
	////			json.put(partNode, "elo");
	////		}
	////	}
	//
	//	private JSONObject formJson(User user) throws JSONException {
	//		JSONObject json = new JSONObject();
	//		json.put("login", user.getLogin());
	//		json.put("name", user.getName());
	//		Set<String> roles = new HashSet<String>();
	//		for (Role role : user.getRoles()) {
	//			roles.add(role.getName());
	//		}
	//		json.put("roles", roles);
	//		return json;
	//	}
}
