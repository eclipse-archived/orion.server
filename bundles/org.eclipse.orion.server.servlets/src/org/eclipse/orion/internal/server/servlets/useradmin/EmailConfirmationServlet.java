/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.useradmin;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.user.profile.RandomPasswordGenerator;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.eclipse.orion.server.useradmin.UserEmailUtil;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.json.JSONException;
import org.json.JSONObject;

public class EmailConfirmationServlet extends OrionServlet {

	private static final long serialVersionUID = 2029138177545673411L;

	private IOrionCredentialsService getUserAdmin() {
		return UserServiceHelper.getDefault().getUserStore();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String[] userPathInfoParts = req.getPathInfo().split("\\/", 2);

		// handle calls to /users/[userId]
		String userId = userPathInfoParts[1];

		IOrionCredentialsService userAdmin = getUserAdmin();
		User user = (User) userAdmin.getUser(UserConstants.KEY_UID, userId);

		if (user == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User " + userId + " not found.");
			return;
		}

		if (req.getParameter(UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID) != null) {
			resetPassword(user, req, resp);
		} else {
			confirmEmail(user, req, resp);
		}

	}

	private void resetPassword(User user, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (user.getProperty(UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID) == null || "".equals(user.getProperty(UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID))) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "You have not requested to reset your password or this password reset request was already completed");
			return;
		}

		if (!user.getProperty(UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID).equals(req.getParameter(UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID))) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "This password reset request is out of date");
			return;
		}

		String newPass = RandomPasswordGenerator.getRanromPassword();

		user.setPassword(newPass);
		user.removeProperty(UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID);

		try {
			UserEmailUtil.getUtil().setPasswordResetEmail(user);
		} catch (Exception e) {
			LogHelper.log(e);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Your password could not be changed, because confirmation email could not be sent. To reset your password contact your administrator.");
			return;
		}

		IStatus status = getUserAdmin().updateUser(user.getUid(), user);
		if (!status.isOK()) {
			if (status instanceof ServerStatus) {
				resp.sendError(((ServerStatus) status).getHttpCode(), status.getMessage());
				return;
			}
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, status.getMessage());
			return;
		}
		resp.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
		resp.getWriter().write("<html><body><p>Your password has been successfully reset. Your new password has been sent to the email address associated with your account.</p></body></html>");
		return;

	}

	private void confirmEmail(User user, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (user.getConfirmationId() == null) {
			resp.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
			resp.getWriter().write("<html><body><p>Your email address has already been confirmed. Thank you!</p></body></html>");
			return;
		}

		if (req.getParameter(UserConstants.KEY_CONFIRMATION_ID) == null || !req.getParameter(UserConstants.KEY_CONFIRMATION_ID).equals(user.getConfirmationId())) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email could not be confirmed.");
			return;
		}

		user.confirmEmail();
		IStatus status = getUserAdmin().updateUser(user.getUid(), user);

		if (!status.isOK()) {
			if (status instanceof ServerStatus) {
				resp.sendError(((ServerStatus) status).getHttpCode(), status.getMessage());
				return;
			}
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, status.getMessage());
			return;
		}
		resp.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
		StringBuffer host = new StringBuffer();
		String scheme = req.getScheme();
		host.append(scheme);
		host.append(":////");
		String servername = req.getServerName();
		host.append(servername);
		host.append(":");
		int port = req.getServerPort();
		host.append(port);
		resp.getWriter().write("<html><body><p>Your email address has been confirmed. Thank you! <a href=\"" + host + "\">Click here</a> to continue and login to your Orion account.</p></body></html>");
		return;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String[] userPathInfoParts = req.getPathInfo() == null ? new String[0] : req.getPathInfo().split("\\/", 2);
		if (userPathInfoParts.length > 1 && userPathInfoParts[1] != null && "cansendemails".equalsIgnoreCase(userPathInfoParts[1])) {
			JSONObject jsonResp = new JSONObject();
			try {
				jsonResp.put("emailConfigured", UserEmailUtil.getUtil().isEmailConfigured());
				writeJSONResponse(req, resp, jsonResp);
			} catch (JSONException e) {
				//this should never happen
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			}
			return;
		}

		String userEmail;
		String userLogin;
		try {
			JSONObject data = OrionServlet.readJSONRequest(req);
			userEmail = data.getString(UserConstants.KEY_EMAIL);
			userLogin = data.getString(UserConstants.KEY_LOGIN);
		} catch (JSONException e) {
			getStatusHandler().handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not parse json request", e));
			return;
		}
		IOrionCredentialsService userAdmin = getUserAdmin();

		Set<User> users = new HashSet<User>();

		if (userLogin != null && userLogin.trim().length() > 0) {
			//reset using login
			User user = userAdmin.getUser(UserConstants.KEY_LOGIN, userLogin.trim());
			if (user == null) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User " + userLogin + " not found.");
				return;
			}
			if (userEmail != null && userEmail.trim().length() > 0) {
				if (!user.isEmailConfirmed()) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User " + userLogin + " email has not been yet confirmed." + " Please follow the instructions from the confirmation email in your inbox and then request a password reset again.");
					return;
				}
				if (!userEmail.equals(user.getEmail())) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User " + userLogin + " with email " + userEmail + " does not exist.");
					return;
				}
			}
			users.add(user);
		} else if (userEmail != null && userEmail.trim().length() > 0) {
			//reset using email address
			User user = userAdmin.getUser(UserConstants.KEY_EMAIL, userEmail.trim());
			if (user != null && user.isEmailConfirmed())
				users.add(user);
			if (users.size() == 0) {
				if (user == null) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User with email " + userEmail + " not found.");
				} else {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email " + userLogin + " has not been yet confirmed." + " Please follow the instructions from the confirmation email in your inbox and then request a password reset again.");
				}
				return;
			}
		}

		MultiStatus multiStatus = new MultiStatus(ServerConstants.PI_SERVER_CORE, IStatus.OK, null, null);

		req.getRequestURI();

		final URI baseURI = URI.create(req.getRequestURL().toString());
		for (User user : users)
			multiStatus.add(sendPasswordResetConfirmation(user, baseURI));

		if (!multiStatus.isOK()) {
			for (int i = 0; i < multiStatus.getChildren().length; i++) {
				IStatus status = multiStatus.getChildren()[i];
				if (status.isOK()) {
					continue;
				}
				getStatusHandler().handleRequest(req, resp, status);
				return;
			}
		}
		getStatusHandler().handleRequest(req, resp, new ServerStatus(IStatus.INFO, HttpServletResponse.SC_OK, "Confirmation email has been sent to " + userEmail, null));

	}

	private IStatus sendPasswordResetConfirmation(User user, URI baseUri) {
		if (user.getEmail() == null || user.getEmail().length() == 0) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "User " + user.getLogin() + " doesn't have its email set. Contact administrator to reset your password.", null);
		}

		if (!user.isEmailConfirmed()) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Your email has not been yet confirmed." + " Please follow the instructions from the confirmation email in your inbox and then request a password reset again.", null);
		}

		IOrionCredentialsService userAdmin = getUserAdmin();

		user.addProperty(UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID, User.getUniqueEmailConfirmationId());
		IStatus status = userAdmin.updateUser(user.getUid(), user);

		if (!status.isOK()) {
			return status;
		}

		try {
			UserEmailUtil.getUtil().sendResetPasswordConfirmation(baseUri, user);
			return new ServerStatus(IStatus.INFO, HttpServletResponse.SC_OK, "Confirmation email has been sent.", null);
		} catch (Exception e) {
			LogHelper.log(e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not send confirmation email.", null);
		}
	}

}
