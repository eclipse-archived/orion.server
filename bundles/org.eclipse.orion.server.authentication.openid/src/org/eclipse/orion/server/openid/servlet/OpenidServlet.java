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
package org.eclipse.orion.server.openid.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.openid.Activator;
import org.eclipse.orion.server.openid.core.OpenIdException;
import org.eclipse.orion.server.openid.core.OpenIdHelper;
import org.eclipse.orion.server.openid.core.OpenidConsumer;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenidServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2395713347747299772L;
	private OpenidConsumer consumer;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			String openid = req.getParameter(OpenIdHelper.OPENID);
			if (openid != null) {
				consumer = OpenIdHelper.redirectToOpenIdProvider(req, resp, consumer);
				return;
			}

			String op_return = req.getParameter(OpenIdHelper.OP_RETURN);
			if (op_return != null) {
				OpenIdHelper.handleOpenIdReturnAndLogin(req, resp, consumer);
				writeLoginResponse(req, resp);
				return;
			}
		} catch (OpenIdException e) {
			writeOpenIdError(e.getMessage(), req, resp);
			return;
		}

		if (OpenIdHelper.getAuthenticatedUser(req) != null) {
			OpenIdHelper.writeLoginResponse(OpenIdHelper.getAuthenticatedUser(req), resp);
			return;
		}

		super.doGet(req, resp);
	}
	
	private static void writeOpenIdError(String error, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (req.getParameter("redirect") == null) {
			PrintWriter out = resp.getWriter();
			resp.setContentType("text/html; charset=UTF-8");
			out.println("<html><head></head>"); //$NON-NLS-1$
			// TODO: send a message using
			// window.eclipseMessage.postImmediate(otherWindow, message) from
			// /org.eclipse.e4.webide/web/orion/message.js
			out.print("<body onload=\"window.opener.handleOpenIDResponse((window.location+'').split('?')[1],'");
			out.print(error);
			out.println("');window.close();\">"); //$NON-NLS-1$
			out.println("</body>"); //$NON-NLS-1$
			out.println("</html>"); //$NON-NLS-1$

			out.close();
			return;
		}
		resp.setContentType("text/html; charset=UTF-8");
		PrintWriter out = resp.getWriter();
		out.println("<html><head></head>"); //$NON-NLS-1$
		// TODO: send a message using
		// window.eclipseMessage.postImmediate(otherWindow, message) from
		// /org.eclipse.e4.webide/web/orion/message.js

		String url = req.getParameter("redirect");
		url = url.replaceAll("/&error(\\=[^&]*)?(?=&|$)|^error(\\=[^&]*)?(&|$)/", ""); // remove
																						// "error"
																						// parameter
		out.print("<body onload=\"window.location.replace('");
		out.print(url.toString());
		if (url.contains("?")) {
			out.print("&error=");
		} else {
			out.print("?error=");
		}
		out.print(new String(Base64.encode(error.getBytes())));
		out.println("');\">"); //$NON-NLS-1$
		out.println("</body>"); //$NON-NLS-1$
		out.println("</html>"); //$NON-NLS-1$
	}

	private static void writeLoginResponse(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String uid = (String) req.getSession().getAttribute("user");
		if (uid == null || "".equals(uid)) {
			return;
		}
		try {
			JSONObject userJson = FormAuthHelper.getUserJson(uid, req.getContextPath());

			PrintWriter out = resp.getWriter();
			out.println("<html><head></head>"); //$NON-NLS-1$
			out.print("<body onload=\"localStorage.setItem('" + Activator.OPENID_AUTH_SIGNIN_KEY + "',  '");
			out.print(userJson.toString().replaceAll("\\\"", "&quot;"));
			out.println("');window.close();\">"); //$NON-NLS-1$
			out.println("</body>"); //$NON-NLS-1$
			out.println("</html>"); //$NON-NLS-1$

			out.close();
		} catch (JSONException e) {
			LogHelper.log(e);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (OpenIdHelper.getAuthenticatedUser(req) != null) {
			OpenIdHelper.writeLoginResponse(OpenIdHelper.getAuthenticatedUser(req), resp);
			return;
		}
	}

}
