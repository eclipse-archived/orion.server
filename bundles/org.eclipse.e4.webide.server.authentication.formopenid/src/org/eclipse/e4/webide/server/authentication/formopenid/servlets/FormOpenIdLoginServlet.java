/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.webide.server.authentication.formopenid.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.e4.webide.server.authentication.form.core.FormAuthHelper;
import org.eclipse.e4.webide.server.authentication.formopenid.FormOpenIdAuthenticationService;
import org.eclipse.e4.webide.server.openid.core.OpenIdHelper;
import org.eclipse.e4.webide.server.openid.core.OpenidConsumer;
import org.eclipse.e4.webide.server.resources.Base64;
import org.eclipse.e4.webide.server.servlets.EclipseWebServlet;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;

public class FormOpenIdLoginServlet extends EclipseWebServlet {

	private FormOpenIdAuthenticationService authenticationService;
	private OpenidConsumer consumer;

	public FormOpenIdLoginServlet(FormOpenIdAuthenticationService authenticationService) {
		super();
		this.authenticationService = authenticationService;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$

		if (pathInfo.startsWith("/form")) { //$NON-NLS-1$
			if (FormAuthHelper.performAuthentication(req, resp)) {
				if (req.getParameter(OpenIdHelper.REDIRECT) != null && !req.getParameter(OpenIdHelper.REDIRECT).equals("")) { //$NON-NLS-1$
					resp.sendRedirect(req.getParameter(OpenIdHelper.REDIRECT));
				} else {
					resp.flushBuffer();
				}
			} else {
				// redirection from
				// FormAuthenticationService.setNotAuthenticated
				String versionString = req.getHeader("EclipseWeb-Version"); //$NON-NLS-1$
				Version version = versionString == null ? null : new Version(versionString);

				// TODO: This is a workaround for calls
				// that does not include the WebEclipse version header
				String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

				String invalidLoginError = "Invalid user or password";

				if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
					RequestDispatcher rd = req.getRequestDispatcher("/mixlogin?error=" + new String(Base64.encode(invalidLoginError.getBytes()))); //$NON-NLS-1$
					rd.include(req, resp);
				} else {
					PrintWriter writer = resp.getWriter();
					JSONObject jsonError = new JSONObject();
					try {
						jsonError.put("error", invalidLoginError); //$NON-NLS-1$
						writer.print(jsonError);
						resp.setContentType("application/json"); //$NON-NLS-1$
					} catch (JSONException e) {/* ignore */
					}
					resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				}
				resp.flushBuffer();
			}
			return;
		}

		if (pathInfo.startsWith("/openid")) { //$NON-NLS-1$
			String openid = req.getParameter(OpenIdHelper.OPENID);
			if (openid != null) {
				consumer = OpenIdHelper.redirectToOpenIdProvider(req, resp, consumer);
				return;
			}

			String op_return = req.getParameter(OpenIdHelper.OP_RETURN);
			if (op_return != null) {
				OpenIdHelper.handleOpenIdReturn(req, resp, consumer);
				return;
			}
		}

		String user;
		if ((user = authenticationService.getAuthenticatedUser(req, resp, new Properties())) != null) {
			resp.setStatus(HttpServletResponse.SC_OK);
			try {
				JSONObject array = new JSONObject();
				array.put("login", user); //$NON-NLS-1$
				resp.getWriter().print(array.toString());
			} catch (JSONException e) {
				handleException(resp, "An error occured when creating JSON object for logged in user", e);
			}
			return;
		}
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$
		if (pathInfo.startsWith("/openid") //$NON-NLS-1$
				&& (req.getParameter(OpenIdHelper.OPENID) != null || req.getParameter(OpenIdHelper.OP_RETURN) != null)) {
			doPost(req, resp);
			return;
		}
		RequestDispatcher rd = req.getRequestDispatcher("/mixlogin/login"); //$NON-NLS-1$
		rd.forward(req, resp);
	}

}
