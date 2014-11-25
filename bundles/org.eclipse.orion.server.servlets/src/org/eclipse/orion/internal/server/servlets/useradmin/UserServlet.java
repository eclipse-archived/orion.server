/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.useradmin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

// POST /users/ creates a new user
// GET /users/ gets list of users
//
// One user methods:
//
// GET /users/[userId] gets user details
// PUT /users/[userId] updates user details
// DELETE /users/[usersId] deletes a user
public class UserServlet extends OrionServlet {

	private static final long serialVersionUID = -6809742538472682623L;

	private List<String> authorizedAccountCreators;
	private ServletResourceHandler<String> userSerializer;

	/**
	 * Checks whether the given path may be accessed by the user.
	 * @param login the user
	 * @param req the request
	 * @return
	 */
	private boolean canAccess(String login, HttpServletRequest req) {
		try {
			String requestPath = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
			if (!AuthorizationService.checkRights(login, requestPath, req.getMethod())) {
				return false;
			}
		} catch (CoreException e) {
			return false;
		}

		return true;
	}

	@Override
	public void init() throws ServletException {
		userSerializer = new ServletUserHandler(getStatusHandler());
		String creators = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null);
		if (creators != null) {
			authorizedAccountCreators = new ArrayList<String>();
			authorizedAccountCreators.addAll(Arrays.asList(creators.split(","))); //$NON-NLS-1$
		}
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String login = req.getRemoteUser();
		JSONObject json = null;
		try {
			json = OrionServlet.readJSONRequest(req);
		} catch (JSONException e1) {
			// just fall through
		}
		if ("POST".equals(req.getMethod())) { //$NON-NLS-1$
			if (json != null && !json.has(UserConstants2.RESET)) {
				// either everyone can create users, or only the specific list
				if (authorizedAccountCreators != null && !authorizedAccountCreators.contains(login)) {
					handleException(resp, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Forbidden access"), HttpServletResponse.SC_FORBIDDEN);
					return;
				}
			} else {
				// only admin users or the account owner can reset their password
				if (login == null || !canAccess(login, req)) {
					handleException(resp, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Forbidden access"), HttpServletResponse.SC_FORBIDDEN);
					return;
				}
			}
		} else {
			if (login == null) {
				handleException(resp, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Forbidden access"), HttpServletResponse.SC_FORBIDDEN);
				return;
			}

			if (!canAccess(login, req)) {
				handleException(resp, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Forbidden access"), HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		}

		traceRequest(req);
		String pathInfo = req.getPathInfo();

		if (pathInfo != null && !pathInfo.equals("/")) {
			String userId = pathInfo.split("\\/")[1];
			UserInfo userInfo = null;
			try {
				userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, userId, false, false);
			} catch (CoreException e) {
				LogHelper.log(e);
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				return;
			}

			if (userInfo == null) {
				handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("User not found: {0}", userId), null));
				return;
			}
		}

		if (userSerializer.handleRequest(req, resp, pathInfo))
			return;
		// finally invoke super to return an error for requests we don't know how to handle
		super.service(req, resp);
	}
}
