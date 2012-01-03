/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.eclipse.orion.server.useradmin.UserServiceHelper;

public class EmailConfirmationServlet extends OrionServlet {

	private static final long serialVersionUID = 2029138177545673411L;

	private IOrionCredentialsService getUserAdmin() {
		return UserServiceHelper.getDefault().getUserStore();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String[] userPathInfoParts = req.getPathInfo().split("\\/", 2);

		// handle calls to /users/[userId]
		String userId = userPathInfoParts[1];

		IOrionCredentialsService userAdmin = getUserAdmin();
		User user = (User) userAdmin.getUser(UserConstants.KEY_UID, userId);

		if (user == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User " + userId + " not found.");
			return;
		}

		if (user.getConfirmationId() == null) {
			resp.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
			resp.getWriter().write("<html><body><p>Email address <b>" + user.getEmail() + "</b> has already been confirmed. Thank you!</p></body></html>");
			return;
		}

		if (req.getParameter(UserConstants.KEY_CONFIRMATION_ID) == null || !req.getParameter(UserConstants.KEY_CONFIRMATION_ID).equals(user.getConfirmationId())) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email could not be confirmed.");
			return;
		}

		user.confirmEmail();

		IStatus status = userAdmin.updateUser(userId, user);

		if (!status.isOK()) {
			if (status instanceof ServerStatus) {
				resp.sendError(((ServerStatus) status).getHttpCode(), status.getMessage());
				return;
			}
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, status.getMessage());
			return;
		}
		resp.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
		resp.getWriter().write("<html><body><p>Email address <b>" + user.getEmail() + "</b> has been confirmed. Thank you!</p></body></html>");
		return;
	}
}
