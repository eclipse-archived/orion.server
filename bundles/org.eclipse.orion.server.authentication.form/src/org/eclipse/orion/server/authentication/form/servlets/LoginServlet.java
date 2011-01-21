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
package org.eclipse.orion.server.authentication.form.servlets;

import org.eclipse.orion.server.core.resources.Base64;

import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;

public class LoginServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (FormAuthHelper.getAuthenticatedUser(req) != null) {
			FormAuthHelper.writeLoginResponse(FormAuthHelper.getAuthenticatedUser(req), resp);
			return;
		}

		if (FormAuthHelper.performAuthentication(req, resp)) {
			if (req.getParameter("redirect") != null && !req.getParameter("redirect").equals(""))
				resp.sendRedirect(req.getParameter("redirect"));
			else {
				resp.flushBuffer();
			}
		} else {
			// redirection from
			// FormAuthenticationService.setNotAuthenticated
			String versionString = req.getHeader("Orion-Version"); //$NON-NLS-1$
			Version version = versionString == null ? null : new Version(versionString);

			// TODO: This is a workaround for calls
			// that does not include the WebEclipse version header
			String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

			String invalidLoginError = "Invalid user or password";

			if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
				RequestDispatcher rd = req.getRequestDispatcher("/loginform?error=" + new String(Base64.encode(invalidLoginError.getBytes()))); //$NON-NLS-1$
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

	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		RequestDispatcher rd = req.getRequestDispatcher("/loginform/login");
		rd.forward(req, resp);
	}

}
