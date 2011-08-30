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
package org.eclipse.orion.server.authentication.form.servlets;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.authentication.form.Activator;
import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.useradmin.UnsupportedUserStoreException;
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

		if (req.getParameter("login") == null && FormAuthHelper.getAuthenticatedUser(req) != null) {
			FormAuthHelper.writeLoginResponse(FormAuthHelper.getAuthenticatedUser(req), resp);
			return;
		}

		try {
			if (FormAuthHelper.performAuthentication(req, resp)) {
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
					} else {
						writeLoginResponse(req, resp);
						resp.flushBuffer();
					}
				} else {
					resp.setStatus(HttpServletResponse.SC_OK);
					PrintWriter writer = resp.getWriter();
					String uid = (String) req.getSession().getAttribute("user");
					JSONObject userJson;
					try {
						userJson = FormAuthHelper.getUserJson(uid);
						writer.print(userJson);
						resp.setContentType("application/json"); //$NON-NLS-1$
					} catch (JSONException e) {/* ignore */
					}
				}
				resp.flushBuffer();
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
					//RequestDispatcher rd = req.getRequestDispatcher("/loginform?error=" + new String(Base64.encode(invalidLoginError.getBytes()))); //$NON-NLS-1$
					RequestDispatcher rd = req.getRequestDispatcher("/loginstatic/LoginWindow.html");
					rd.include(req, resp);
				} else {
					resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					PrintWriter writer = resp.getWriter();
					JSONObject jsonError = new JSONObject();
					try {
						jsonError.put("error", invalidLoginError); //$NON-NLS-1$
						writer.print(jsonError);
						resp.setContentType("application/json"); //$NON-NLS-1$
					} catch (JSONException e) {/* ignore */
					}
				}
				resp.flushBuffer();
			}
		} catch (UnsupportedUserStoreException e) {
			LogHelper.log(e);
			resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
		}
	}

	private static void writeLoginResponse(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String uid = (String) req.getSession().getAttribute("user");
		if (uid == null || "".equals(uid))
			return;

		try {
			JSONObject userJson = FormAuthHelper.getUserJson(uid);

			PrintWriter out = resp.getWriter();
			out.println("<html><head></head>"); //$NON-NLS-1$
			out.print("<body onload=\"localStorage.setItem('" + Activator.FORM_AUTH_SIGNIN_KEY + "',  '");
			out.print(userJson.toString().replaceAll("\\\"", "&quot;"));
			out.println("');window.close();\">"); //$NON-NLS-1$
			out.println("</body>"); //$NON-NLS-1$
			out.println("</html>"); //$NON-NLS-1$

			out.close();
		} catch (JSONException e) {
			LogHelper.log(e);
		}
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}
}
