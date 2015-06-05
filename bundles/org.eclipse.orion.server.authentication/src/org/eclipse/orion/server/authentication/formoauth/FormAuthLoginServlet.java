/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.formoauth;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.authentication.form.FormAuthHelper;
import org.eclipse.orion.server.authentication.form.FormAuthHelper.LoginResult;
import org.eclipse.orion.server.authentication.oauth.OAuthException;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.core.users.UserConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormAuthLoginServlet extends HttpServlet {

	private FormAuthenticationService authenticationService;
	private ManageOAuthServlet manageOAuthServlet;

	public FormAuthLoginServlet(FormAuthenticationService authenticationService) {
		super();
		this.authenticationService = authenticationService;
		this.manageOAuthServlet = new ManageOAuthServlet();
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$

		if (pathInfo.startsWith("/form")) { //$NON-NLS-1$
			LoginResult authResult = FormAuthHelper.performAuthentication(req, resp);
			if (authResult == LoginResult.OK) {
				// redirection from
				// FormAuthenticationService.setNotAuthenticated
				String versionString = req.getHeader("Orion-Version"); //$NON-NLS-1$
				Version version = versionString == null ? null : new Version(versionString);

				// TODO: This is a workaround for calls
				// that does not include the WebEclipse version header
				String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

				if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
					if (req.getParameter("redirect") != null && !req.getParameter("redirect").equals("")) { //$NON-NLS-1$
						resp.sendRedirect(req.getParameter("redirect"));
					}
				} else {
					resp.setStatus(HttpServletResponse.SC_OK);
					PrintWriter writer = resp.getWriter();
					String uid = (String) req.getSession().getAttribute("user");
					JSONObject userJson;
					try {
						userJson = FormAuthHelper.getUserJson(uid, req.getContextPath());
						writer.print(userJson.toString(2));
						resp.setContentType("application/json"); //$NON-NLS-1$
					} catch (JSONException e) {/* ignore */
					}
				}
				resp.flushBuffer();
			} else if (authResult == LoginResult.BLOCKED) {
				displayError("Your account is not active. Please confirm your email before logging in.", req, resp);
			} else {
				displayError("Invalid user or password", req, resp);
			}
			return;
		}
		if (pathInfo.startsWith("/oauth")) {
			try {
				manageOAuthServlet.handleGetAndLogin(req, resp);
				resp.setStatus(HttpServletResponse.SC_OK);
			} catch (OAuthException e) {
				displayError(e.getMessage(), req, resp);
			}
		}

		if (pathInfo.startsWith("/canaddusers")) {
			JSONObject jsonResp = new JSONObject();
			try {
				jsonResp.put("CanAddUsers", FormAuthHelper.canAddUsers());
				jsonResp.put("ForceEmail", FormAuthHelper.forceEmail());
				jsonResp.put("RegistrationURI", FormAuthHelper.registrationURI());
			} catch (JSONException e) {
			}
			resp.getWriter().print(jsonResp);
			resp.setContentType("application/json");
			return;
		}

		//String user = req.getRemoteUser();
		//// See Bugzilla 468670, this code is temporary
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.login"); //$NON-NLS-1$
		logger.warn("Login hard coded to: ahunter"); //$NON-NLS-1$ 
		String user = "ahunter";
		//if (user == null) {
		//	user = authenticationService.getAuthenticatedUser(req, resp);
		//}

		if (user != null && ! pathInfo.startsWith("/oauth")) {
			try {
				// try to store the login timestamp in the user profile
				UserInfo userInfo = OrionConfiguration.getMetaStore().readUser(user);
				userInfo.setProperty(UserConstants.LAST_LOGIN_TIMESTAMP, new Long(System.currentTimeMillis()).toString());
				
				String cookieToCache = PreferenceHelper.getString("orion.cookie.cached"); //$NON-NLS-1$
				Cookie[] cookies = req.getCookies();
				for(int i = 0; i < cookies.length; i++){
					if(cookieToCache != null && cookieToCache.equals(cookies[i].getName()) && cookies[i].getValue() != null){
						userInfo.setProperty("/cookie/cached/" + cookieToCache, cookies[i].getValue().toString());
					}
				}
				
				OrionConfiguration.getMetaStore().updateUser(userInfo);
			} catch (CoreException e) {
				// just log that the login timestamp was not stored
				LogHelper.log(e);
			}

			resp.setStatus(HttpServletResponse.SC_OK);
			try {
				JSONObject jsonResp = FormAuthHelper.getUserJson(user, req.getContextPath());
				resp.setContentType("application/json");
				resp.getWriter().print(jsonResp);
			} catch (JSONException e) {
				displayError("An error occured when creating JSON object for logged in user", req, resp);
			}
			return;
		}
	}

	private void displayError(String error, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// redirection from
		// FormAuthenticationService.setNotAuthenticated
		String versionString = req.getHeader("Orion-Version"); //$NON-NLS-1$
		Version version = versionString == null ? null : new Version(versionString);

		// TODO: This is a workaround for calls
		// that does not include the WebEclipse version header
		String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

		if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
			String url = "/mixloginstatic/LoginWindow.html";
			if (req.getParameter("redirect") != null) {
				url += "?redirect=" + req.getParameter("redirect");
			}

			if (error == null) {
				error = "Invalid login";
			}
			url += url.contains("?") ? "&" : "?";
			url += "error=" + new String(Base64.encode(error.getBytes()));

			resp.sendRedirect(url);

		} else {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			PrintWriter writer = resp.getWriter();
			JSONObject jsonError = new JSONObject();
			try {
				jsonError.put("error", error); //$NON-NLS-1$
				writer.print(jsonError);
				resp.setContentType("application/json"); //$NON-NLS-1$
			} catch (JSONException e) {/* ignore */
			}
		}
		resp.flushBuffer();
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$
		if (pathInfo.startsWith("/oauth")) { //$NON-NLS-1$
			doPost(req, resp);
			return;
		}
		RequestDispatcher rd = req.getRequestDispatcher("/mixlogin/login"); //$NON-NLS-1$
		rd.forward(req, resp);
	}

}
