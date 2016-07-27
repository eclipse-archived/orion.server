/*******************************************************************************
 * Copyright (c) 2012, 2015 IBM Corporation and others.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.UserEmailUtil;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

public class EmailConfirmationServlet extends OrionServlet {

	private static final long serialVersionUID = 2029138177545673411L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String[] userPathInfoParts = req.getPathInfo().split("\\/", 2);

		// handle calls to /users/[userId]
		String userId = userPathInfoParts[1];

		UserInfo userInfo = null;
		try {
			userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, userId, false, false);
		} catch (CoreException e) {
			LogHelper.log(e);
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User " + userId + " not found.");
			return;
		}

		if (userInfo == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User " + userId + " not found.");
			return;
		}

		if (req.getParameter(UserConstants.PASSWORD_RESET_ID) != null) {
			resetPassword(userInfo, req, resp);
		} else {
			confirmEmail(userInfo, req, resp);
		}

	}

	private void resetPassword(UserInfo userInfo, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (userInfo.getProperty(UserConstants.PASSWORD_RESET_ID) == null || "".equals(userInfo.getProperty(UserConstants.PASSWORD_RESET_ID))) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
					"You have not requested to reset your password or this password reset request was already completed");
			return;
		}

		if (!userInfo.getProperty(UserConstants.PASSWORD_RESET_ID).equals(req.getParameter(UserConstants.PASSWORD_RESET_ID))) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "This password reset request is out of date");
			return;
		}

		String newPass = getRandomPassword();

		userInfo.setProperty(UserConstants.PASSWORD, newPass);
		userInfo.setProperty(UserConstants.PASSWORD_RESET_ID, null);

		try {
			UserEmailUtil.getUtil().sendPasswordResetEmail(userInfo);
		} catch (Exception e) {
			LogHelper.log(e);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Your password could not be changed, because confirmation email could not be sent. To reset your password contact your administrator.");
			return;
		}

		try {
			OrionConfiguration.getMetaStore().updateUser(userInfo);
		} catch (Exception e) {
			LogHelper.log(e);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Your password could not be changed. To reset your password contact your administrator.");
			return;
		}

		resp.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
		resp.getWriter().write(
				"<html><body><p>Your password has been successfully reset. Your new password has been sent to the email address associated with your account.</p></body></html>");
		return;

	}

	private void confirmEmail(UserInfo userInfo, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID) == null) {
			resp.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
			resp.getWriter().write("<html><body><p>Your email address has already been confirmed. Thank you!</p></body></html>");
			return;
		}

		if (req.getParameter(UserConstants.EMAIL_CONFIRMATION_ID) == null
				|| !req.getParameter(UserConstants.EMAIL_CONFIRMATION_ID).equals(userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID))) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email could not be confirmed.");
			return;
		}

		try {
			userInfo.setProperty(UserConstants.EMAIL_CONFIRMATION_ID, null);
			userInfo.setProperty(UserConstants.BLOCKED, null);
			OrionConfiguration.getMetaStore().updateUser(userInfo);
		} catch (CoreException e) {
			LogHelper.log(e);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
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
		resp.getWriter().write("<html><body><p>Your email address has been confirmed. Thank you! <a href=\"" + host
				+ "\">Click here</a> to continue and login to your account.</p></body></html>");
		return;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String[] userPathInfoParts = req.getPathInfo() == null ? new String[0] : req.getPathInfo().split("\\/", 2);
		if (userPathInfoParts.length > 1 && userPathInfoParts[1] != null && "cansendemails".equalsIgnoreCase(userPathInfoParts[1])) {
			JSONObject jsonResp = new JSONObject();
			try {
				jsonResp.put("EmailConfigured", UserEmailUtil.getUtil().isEmailConfigured());
				writeJSONResponse(req, resp, jsonResp);
			} catch (JSONException e) {
				// this should never happen
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			}
			return;
		}

		String userEmail = null;
		String userName = null;
		try {
			JSONObject json = OrionServlet.readJSONRequest(req);
			if (json.has(UserConstants.EMAIL)) {
				userEmail = json.getString(UserConstants.EMAIL);
			}
			if (json.has(UserConstants.USER_NAME)) {
				userName = json.getString(UserConstants.USER_NAME);
			}
		} catch (JSONException e) {
			getStatusHandler().handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not parse json request", e));
			return;
		}

		List<UserInfo> users = new ArrayList<UserInfo>();

		if (userName != null && userName.trim().length() > 0) {
			// reset using login
			UserInfo userInfo = null;
			try {
				userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, userName.trim(), false, false);
			} catch (CoreException e) {
				LogHelper.log(e);
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				return;
			}

			if (userInfo == null) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User " + userName + " not found.");
				return;
			}
			if (userEmail != null && userEmail.trim().length() > 0) {
				if (!isEmailConfirmed(userInfo)) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User " + userName + " email has not been yet confirmed."
							+ " Please follow the instructions from the confirmation email in your inbox and then request a password reset again.");
					return;
				}
				if (!userEmail.equals(userInfo.getProperty(UserConstants.EMAIL))) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User " + userName + " with email " + userEmail + " does not exist.");
					return;
				}
			}
			users.add(userInfo);
		} else if (userEmail != null && userEmail.trim().length() > 0) {
			// reset using email address
			UserInfo userInfo = null;
			try {
				userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.EMAIL, userEmail.trim().toLowerCase(), false, false);
			} catch (CoreException e) {
				LogHelper.log(e);
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				return;
			}

			if (userInfo != null && isEmailConfirmed(userInfo)) {
				users.add(userInfo);
			}
			if (users.size() == 0) {
				if (userInfo == null) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User with email " + userEmail + " not found.");
				} else {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email " + userName + " has not been yet confirmed."
							+ " Please follow the instructions from the confirmation email in your inbox and then request a password reset again.");
				}
				return;
			}
		}

		MultiStatus multiStatus = new MultiStatus(ServerConstants.PI_SERVER_CORE, IStatus.OK, null, null);

		req.getRequestURI();

		final URI baseURI = URI.create(req.getRequestURL().toString());
		for (UserInfo userInfo : users)
			multiStatus.add(sendPasswordResetConfirmation(userInfo, baseURI));

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
		getStatusHandler().handleRequest(req, resp,
				new ServerStatus(IStatus.INFO, HttpServletResponse.SC_OK, "Confirmation email has been sent to " + userEmail, null));

	}

	private IStatus sendPasswordResetConfirmation(UserInfo userInfo, URI baseUri) {
		if (userInfo.getProperty(UserConstants.EMAIL) == null || userInfo.getProperty(UserConstants.EMAIL).length() == 0) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
					"User " + userInfo.getUniqueId() + " doesn't have its email set. Contact administrator to reset your password.", null);
		}

		if (!isEmailConfirmed(userInfo)) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Your email has not been yet confirmed."
					+ " Please follow the instructions from the confirmation email in your inbox and then request a password reset again.", null);
		}

		try {
			userInfo.setProperty(UserConstants.PASSWORD_RESET_ID, getUniqueEmailConfirmationId());
			OrionConfiguration.getMetaStore().updateUser(userInfo);
		} catch (CoreException e) {
			LogHelper.log(e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}

		try {
			UserEmailUtil.getUtil().sendResetPasswordConfirmation(baseUri, userInfo);
			return new ServerStatus(IStatus.INFO, HttpServletResponse.SC_OK, "Confirmation email has been sent.", null);
		} catch (Exception e) {
			LogHelper.log(e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not send confirmation email.", null);
		}
	}

	/**
	 * Generates random string that may be used as auto-generated password after user reset.
	 * 
	 * @return a random string.
	 */
	private String getRandomPassword() {
		return getRandomString(5, 25);
	}

	private String getRandomString(int lo, int hi) {
		int n = getRandomNumber(lo, hi);
		byte b[] = new byte[n];
		for (int i = 0; i < n; i++)
			b[i] = (byte) getRandomNumber('1', 'Z');
		return new String(b);
	}

	private int getRandomNumber(int lo, int hi) {
		Random rn = new Random();
		int n = hi - lo + 1;
		int i = rn.nextInt(n);
		if (i < 0)
			i = -i;
		return lo + i;
	}

	private boolean isEmailConfirmed(UserInfo userInfo) {
		String email = userInfo.getProperty(UserConstants.EMAIL);
		return (email != null && email.length() > 0) ? userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID) == null : false;
	}

	private static String getUniqueEmailConfirmationId() {
		return System.currentTimeMillis() + "-" + Math.random();
	}
}
