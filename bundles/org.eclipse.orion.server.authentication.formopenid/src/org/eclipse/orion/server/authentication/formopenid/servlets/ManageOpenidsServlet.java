/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.formopenid.servlets;

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
import org.eclipse.orion.server.authentication.formopenid.Activator;
import org.eclipse.orion.server.authentication.formopenid.FormOpenIdAuthenticationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.openid.core.OpenIdException;
import org.eclipse.orion.server.openid.core.OpenIdHelper;
import org.eclipse.orion.server.openid.core.OpendIdProviderDescription;
import org.eclipse.orion.server.openid.core.OpenidConsumer;
import org.json.JSONException;

public class ManageOpenidsServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5478748783512325610L;
	private FormOpenIdAuthenticationService authenticationService;
	private OpenidConsumer consumer;

	public ManageOpenidsServlet(FormOpenIdAuthenticationService formOpenIdAuthenticationService) {
		this.authenticationService = formOpenIdAuthenticationService;
	}

	private String getConfiguredOpenIds() {
		return (String) (authenticationService.getDefaultAuthenticationProperties() == null ? null : authenticationService.getDefaultAuthenticationProperties().get(OPENIDS_PROPERTY));
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
		String customOpenids = req.getAttribute(OPENIDS_PROPERTY) == null ? getConfiguredOpenIds() : (String) req.getAttribute(OPENIDS_PROPERTY);
		if (customOpenids == null || customOpenids.trim().length() == 0) {
			openidProviders = OpenIdHelper.getDefaultOpenIdProviders();
		} else {
			try {
				openidProviders = OpenIdHelper.getSupportedOpenIdProviders(customOpenids);
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, "Cannot load openid list, JSON format expected", e)); //$NON-NLS-1$
				openidProviders = OpenIdHelper.getDefaultOpenIdProviders();
			}
		}
		return openidProviders;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$
		try {
			if (pathInfo.startsWith("/openid")) { //$NON-NLS-1$
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
			}
		} catch (OpenIdException e) {
			writeOpenIdError(e.getMessage(), req, response);
			return;
		}

		response.setContentType("text/html"); //$NON-NLS-1$
		PrintWriter writer = response.getWriter();
		writer.println("<!DOCTYPE html>"); //$NON-NLS-1$
		writer.println("<html>"); //$NON-NLS-1$
		writer.println("<head>"); //$NON-NLS-1$
		writer.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">"); //$NON-NLS-1$
		writer.println("<script type=\"text/javascript\""); //$NON-NLS-1$
		writer.println("src=\"/org.dojotoolkit/dojo/dojo.js.uncompressed.js\"></script>"); //$NON-NLS-1$
		writer.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"/mixloginstatic/css/manageOpenids.css\" />"); //$NON-NLS-1$
		writer.println("<script type=\"text/javascript\" src=\"/mixloginstatic/js/manageOpenids.js\"></script>"); //$NON-NLS-1$
		writer.println("</head>"); //$NON-NLS-1$
		writer.println("<body>"); //$NON-NLS-1$
		writer.println("<div id=\"newOpenId\"><h2>Add external account: ");
		for (OpendIdProviderDescription provider : getSupportedOpenids(req)) {
			writer.print(provider.toJsImage().replaceAll("\\\\'", "'"));
		}
		writer.println("<h2></div>");
		writer.println("<div id=\"openidList\"></div>"); //$NON-NLS-1$
		writer.println("</body>"); //$NON-NLS-1$
		writer.println("</html>"); //$NON-NLS-1$

		writer.flush();
	}

}
