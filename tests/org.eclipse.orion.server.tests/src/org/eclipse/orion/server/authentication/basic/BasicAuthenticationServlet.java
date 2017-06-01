/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.basic;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

public class BasicAuthenticationServlet extends OrionServlet {

	private static final long serialVersionUID = -4208832384205633048L;

	private BasicAuthenticationService authService;

	public BasicAuthenticationServlet(BasicAuthenticationService authService) {
		super();
		this.authService = authService;
	}

	private JSONObject getUserJson(String username) throws JSONException {
		JSONObject json = new JSONObject();
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, username, false, false);
			if (userInfo == null) {
				return null;
			}
			json.put(UserConstants.USER_NAME, userInfo.getUserName());
			json.put(UserConstants.FULL_NAME, userInfo.getFullName());
			json.put(UserConstants.LOCATION, UserConstants.LOCATION_USERS_SERVLET + '/' + username);
			String email = userInfo.getProperty(UserConstants.EMAIL);
			json.put(UserConstants.EMAIL, email);
			boolean emailConfirmed = (email != null && email.length() > 0) ? userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID) == null : false;
			json.put(UserConstants.EMAIL_CONFIRMED, emailConfirmed);
			json.put(UserConstants.HAS_PASSWORD, userInfo.getProperty(UserConstants.PASSWORD) == null ? false : true);

			json.put(UserConstants.LAST_LOGIN_TIMESTAMP, userInfo.getProperty(UserConstants.LAST_LOGIN_TIMESTAMP));
			json.put(UserConstants.DISK_USAGE_TIMESTAMP, userInfo.getProperty(UserConstants.DISK_USAGE_TIMESTAMP));
			json.put(UserConstants.DISK_USAGE, userInfo.getProperty(UserConstants.DISK_USAGE));
		} catch (IllegalArgumentException e) {
			LogHelper.log(e);
		} catch (CoreException e) {
			LogHelper.log(e);
		}

		return json;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setCharacterEncoding("UTF-8");
		try {
			resp.getWriter().print(getUserJson(authService.getAuthenticatedUser(req, resp)));
		} catch (JSONException e) {
			//can't fail
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		authService.authenticateUser(req, resp);
	}
}
