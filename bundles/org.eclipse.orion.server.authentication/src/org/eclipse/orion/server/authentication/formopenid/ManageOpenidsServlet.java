/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.formopenid;

import static org.eclipse.orion.server.authentication.formopenid.FormOpenIdAuthenticationService.OPENIDS_PROPERTY;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.authentication.Activator;
import org.eclipse.orion.server.authentication.oauth.OAuthException;
import org.eclipse.orion.server.authentication.openid.OpenIdException;
import org.eclipse.orion.server.authentication.openid.OpenIdHelper;
import org.eclipse.orion.server.authentication.openid.OpendIdProviderDescription;
import org.eclipse.orion.server.authentication.openid.OpenidConsumer;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ManageOpenidsServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5478748783512325610L;
	private FormOpenIdAuthenticationService authenticationService;
	private ManageOAuthServlet manageOAuthServlet;
	private OpenidConsumer consumer;

	public ManageOpenidsServlet(FormOpenIdAuthenticationService formOpenIdAuthenticationService) {
		this.authenticationService = formOpenIdAuthenticationService;
		this.manageOAuthServlet = new ManageOAuthServlet();
	}

	private static void writeOpenIdError(String error, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (req.getParameter("redirect") == null) {
			resp.setContentType("text/html; charset=UTF-8");
			PrintWriter out = resp.getWriter();
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

	private List<OpendIdProviderDescription> getSupportedOpenids(HttpServletRequest req) {
		List<OpendIdProviderDescription> openidProviders;
		String customOpenids = (String) req.getAttribute(OPENIDS_PROPERTY);
		if (customOpenids == null || customOpenids.trim().length() == 0) {
			openidProviders = OpenIdHelper.getDefaultOpenIdProviders();
		} else {
			try {
				openidProviders = OpenIdHelper.getSupportedOpenIdProviders(customOpenids);
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, "Cannot load openid list, JSON format expected", e)); //$NON-NLS-1$
				openidProviders = OpenIdHelper.getDefaultOpenIdProviders();
			}
		}
		return openidProviders;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$

		if (pathInfo.startsWith("/oauth")){
			try {
				manageOAuthServlet.handleGetAndLink(req, response);
			} catch (OAuthException e) {
				writeOpenIdError(e.getMessage(), req, response);
			}
			return;
		} else if (pathInfo.startsWith("/openid")) { //$NON-NLS-1$
			try {
				String openid = req.getParameter(OpenIdHelper.OPENID);
				if (openid != null) {
					consumer = OpenIdHelper.redirectToOpenIdProvider(req, response, consumer);
					return;
				}

				String op_return = req.getParameter(OpenIdHelper.OP_RETURN);
				if (op_return != null) {
					OpenIdHelper.handleOpenIdReturn(req, response, consumer);
					return;
				}

			} catch (OpenIdException e) {
				writeOpenIdError(e.getMessage(), req, response);
				return;
			}
		}

		JSONArray providersJson = new JSONArray();
		for (OpendIdProviderDescription provider : getSupportedOpenids(req)) {
			JSONObject providerJson = new JSONObject();
			try {
				providerJson.put(ProtocolConstants.KEY_NAME, provider.getName());
				providerJson.put(OpenIdConstants.KEY_IMAGE, provider.getImage());
				providerJson.put(OpenIdConstants.KEY_URL, provider.getAuthSite());
				providersJson.put(providerJson);
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, "Exception writing OpenId provider " + provider.getName(), e)); //$NON-NLS-1$
			}
		}

		response.setContentType("application/json"); //$NON-NLS-1$
		PrintWriter writer = response.getWriter();
		writer.append(providersJson.toString());
		writer.flush();
	}

}
