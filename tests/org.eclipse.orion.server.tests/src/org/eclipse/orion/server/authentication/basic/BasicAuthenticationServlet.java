/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others 
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
import org.eclipse.orion.server.core.users.UserConstants2;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.json.JSONException;
import org.json.JSONObject;

public class BasicAuthenticationServlet extends OrionServlet {

	private static final long serialVersionUID = -4208832384205633048L;

	private BasicAuthenticationService authService;

	public BasicAuthenticationServlet(BasicAuthenticationService authService) {
		super();
		this.authService = authService;
	}

	private JSONObject getUserJson(String uid) throws JSONException {
		JSONObject obj = new JSONObject();
		obj.put(UserConstants.KEY_LOGIN, uid);

		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, uid, false, false);
			if (userInfo == null) {
				return null;
			}
			obj.put(UserConstants.KEY_UID, uid);
			obj.put(UserConstants.KEY_LOGIN, userInfo.getUserName());
			obj.put(UserConstants.KEY_LOCATION, '/' + UserConstants.KEY_USERS + '/' + uid);
			obj.put(UserConstants2.FULL_NAME, userInfo.getFullName());
			if (userInfo.getProperties().containsKey(UserConstants2.LAST_LOGIN_TIMESTAMP)) {
				Long lastLogin = Long.parseLong(userInfo.getProperty(UserConstants2.LAST_LOGIN_TIMESTAMP));
				obj.put(UserConstants2.LAST_LOGIN_TIMESTAMP, lastLogin);
			}
			if (userInfo.getProperties().containsKey(UserConstants2.DISK_USAGE_TIMESTAMP)) {
				Long lastLogin = Long.parseLong(userInfo.getProperty(UserConstants2.DISK_USAGE_TIMESTAMP));
				obj.put(UserConstants2.DISK_USAGE_TIMESTAMP, lastLogin);
			}
			if (userInfo.getProperties().containsKey(UserConstants2.DISK_USAGE)) {
				Long lastLogin = Long.parseLong(userInfo.getProperty(UserConstants2.DISK_USAGE));
				obj.put(UserConstants2.DISK_USAGE, lastLogin);
			}
		} catch (IllegalArgumentException e) {
			LogHelper.log(e);
		} catch (CoreException e) {
			LogHelper.log(e);
		}

		return obj;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
