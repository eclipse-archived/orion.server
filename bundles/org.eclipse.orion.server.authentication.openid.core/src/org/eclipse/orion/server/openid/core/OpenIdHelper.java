/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.openid.core;

import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.json.JSONException;
import org.json.JSONObject;
import org.openid4java.consumer.ConsumerException;

/**
 * Groups methods to handle session attributes for OpenID authentication.
 * 
 */
public class OpenIdHelper {

	public static final String OPENID = "openid"; //$NON-NLS-1$
	public static final String OP_RETURN = "op_return"; //$NON-NLS-1$
	public static final String REDIRECT = "redirect"; //$NON-NLS-1$
	static final String OPENID_IDENTIFIER = "openid_identifier"; //$NON-NLS-1$
	static final String OPENID_DISC = "openid-disc"; //$NON-NLS-1$

	/**
	 * Checks session attributes to retrieve authenticated user identifier.
	 * 
	 * @param req
	 * @return OpenID identifier of authenticated user of <code>null</code> if
	 *         user is not authenticated
	 * @throws IOException
	 */
	public static String getAuthenticatedUser(HttpServletRequest req) throws IOException {
		HttpSession s = req.getSession(true);
		if (s.getAttribute(OPENID_IDENTIFIER) != null) {
			return (String) s.getAttribute(OPENID_IDENTIFIER);
		}
		return null;
	}

	/**
	 * Redirects to OpenId provider stored in <code>openid</code> parameter of
	 * request. If user is to be redirected after login perform the redirect
	 * site should be stored in <code>redirect<code> parameter.
	 * 
	 * @param req
	 * @param resp
	 * @param consumer
	 *            {@link OpenidConsumer} used to login user by OpenId. The same
	 *            consumer should be used for
	 *            {@link #handleOpenIdReturn(HttpServletRequest, HttpServletResponse, OpenidConsumer)}
	 *            . If <code>null</code> the new {@link OpenidConsumer} will be
	 *            created and returned
	 * @return
	 * @throws IOException
	 */
	public static OpenidConsumer redirectToOpenIdProvider(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException {
		String redirect = req.getParameter(REDIRECT);
		try {
			StringBuffer sb = getRequestServer(req);
			sb.append(req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo())); //$NON-NLS-1$
			sb.append("?").append(OP_RETURN).append("=true"); //$NON-NLS-1$ //$NON-NLS-2$
			if (redirect != null && redirect.length() > 0) {
				sb.append("&").append(REDIRECT).append("="); //$NON-NLS-1$ //$NON-NLS-2$
				sb.append(redirect);
			}
			consumer = new OpenidConsumer(sb.toString());
			consumer.authRequest(req.getParameter(OPENID), req, resp); //$NON-NLS-1$
			// redirection takes place in the authRequest method
		} catch (ConsumerException e) {
			writeOpenIdError(e.getMessage(), req, resp);
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, "An error occured when creating OpenidConsumer", e));
		} catch (CoreException e) {
			writeOpenIdError(e.getMessage(), req, resp);
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, "An error occured when authenticing request", e));
		}
		return consumer;
	}

	private static void writeOpenIdError(String error, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (req.getParameter(REDIRECT) == null) {
			PrintWriter out = resp.getWriter();
			out.println("<html><head></head>"); //$NON-NLS-1$
			// TODO: send a message using
			// window.eclipseMessage.postImmediate(otherWindow, message) from
			// /org.eclipse.e4.webide/static/js/message.js
			out.print("<body onload=\"window.opener.handleOpenIDResponse((window.location+'').split('?')[1],'");
			out.print(error);
			out.println("');window.close();\">"); //$NON-NLS-1$
			out.println("</body>"); //$NON-NLS-1$
			out.println("</html>"); //$NON-NLS-1$

			out.close();
			return;
		}
		PrintWriter out = resp.getWriter();
		out.println("<html><head></head>"); //$NON-NLS-1$
		// TODO: send a message using
		// window.eclipseMessage.postImmediate(otherWindow, message) from
		// /org.eclipse.e4.webide/static/js/message.js

		String url = req.getParameter(REDIRECT);
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

	/**
	 * Parses the response from OpenId provider. If <code>redirect</code>
	 * parameter is not set closes the current window.
	 * 
	 * @param req
	 * @param resp
	 * @param consumer
	 *            same {@link OpenidConsumer} as used in
	 *            {@link #redirectToOpenIdProvider(HttpServletRequest, HttpServletResponse, OpenidConsumer)}
	 * @throws IOException
	 */
	public static void handleOpenIdReturn(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException {
		String redirect = req.getParameter(REDIRECT);
		String op_return = req.getParameter(OP_RETURN);
		if (Boolean.parseBoolean(op_return) && consumer != null) {
			consumer.verifyResponse(req);
			if (redirect != null) {
				resp.sendRedirect(redirect);
				return;
			}
			PrintWriter out = resp.getWriter();
			out.println("<html><head></head>"); //$NON-NLS-1$
			// TODO: send a message using
			// window.eclipseMessage.postImmediate(otherWindow, message) from
			// /org.eclipse.e4.webide/static/js/message.js
			out.println("<body onload=\"window.opener.handleOpenIDResponse((window.location+'').split('?')[1]);window.close();\">"); //$NON-NLS-1$
			out.println("</body>"); //$NON-NLS-1$
			out.println("</html>"); //$NON-NLS-1$

			out.close();
			return;
		}
	}

	private static StringBuffer getRequestServer(HttpServletRequest req) {
		StringBuffer url = new StringBuffer();
		String scheme = req.getScheme();
		int port = req.getServerPort();

		url.append(scheme);
		url.append("://"); //$NON-NLS-1$
		url.append(req.getServerName());
		if ((scheme.equals("http") && port != 80) //$NON-NLS-1$
				|| (scheme.equals("https") && port != 443)) { //$NON-NLS-1$
			url.append(':');
			url.append(req.getServerPort());
		}
		return url;
	}

	/**
	 * Destroys the session attributes that identify the user.
	 * 
	 * @param req
	 */
	public static void performLogout(HttpServletRequest req) {
		HttpSession s = req.getSession(true);
		if (s.getAttribute(OPENID_IDENTIFIER) != null) {
			s.removeAttribute(OPENID_IDENTIFIER);
		}
	}

	/**
	 * Writes a response in JSON that contains user login.
	 * 
	 * @param login
	 * @param resp
	 * @throws IOException
	 */
	public static void writeLoginResponse(String login, HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		try {
			JSONObject array = new JSONObject();
			array.put("login", login); //$NON-NLS-1$
			resp.getWriter().print(array.toString());
		} catch (JSONException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, "An error occured when creating JSON object for logged in user", e));
		}
	}

	public static String getAuthType() {
		return "OpenId"; //$NON-NLS-1$
	}
}
