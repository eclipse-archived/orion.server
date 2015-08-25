/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.formoauth;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.authentication.oauth.OAuthConsumer;
import org.eclipse.orion.server.authentication.oauth.OAuthException;
import org.eclipse.orion.server.authentication.oauth.OAuthHelper;
import org.eclipse.orion.server.authentication.oauth.OAuthParams;
import org.eclipse.orion.server.authentication.oauth.OAuthParamsFactory;
import org.eclipse.orion.server.core.resources.Base64;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * Methods to handles OAuth requests.
 * @author Aidan Redpath
 *
 */
public class ManageOAuthServlet extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = -3863741024714602634L;

	private OAuthParams oauthParams;

	private static void writeOAuthError(String error, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (req.getParameter("redirect") == null) {
			resp.setContentType("text/html; charset=UTF-8");
			PrintWriter out = resp.getWriter();
			out.println("<html><head></head>"); //$NON-NLS-1$
			// TODO: send a message using
			// window.eclipseMessage.postImmediate(otherWindow, message) from
			// /org.eclipse.e4.webide/web/orion/message.js
			out.print("<body onload=\"window.opener.handleOAuthResponse((window.location+'').split('?')[1],'");
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

	private void handleGet(HttpServletRequest req, HttpServletResponse resp, Boolean login) throws ServletException, IOException, OAuthException {
		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$
		if (pathInfo.startsWith("/oauth")) {
			String oauthParam = req.getParameter(OAuthHelper.OAUTH);
			if(oauthParam != null){
				OAuthHelper.redirectToOAuthProvider(req, resp, getOAuthParams(req, oauthParam, login));
			}else {
				OAuthConsumer oauthConsumer = OAuthHelper.handleOAuthReturnAndTokenAccess(req, resp, getOAuthParams());
				if(login)
					OAuthHelper.handleLogin(req, resp, oauthConsumer);
				else
					OAuthHelper.handleReturnAndLinkAccount(req, resp, oauthConsumer);
			}
		}
	}

	/**
	 * This method retrieves OSGi context to find a service of class OAuthParamsFactory
	 * registered with 'provider' property matching providerName.
	 * @param providerName: string matching the one returned by OAuthParamsFactory.getOAuthProviderName()
	 * @return Factory to create OAuthParams object
	 * @throws OAuthException
	 */
	private OAuthParamsFactory getParamsFactory(String providerName) throws OAuthException {
		BundleContext ctx = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
		if (ctx == null) {
			throw new OAuthException("Plug-in context non available");
		}

		String filter = "(" + OAuthParamsFactory.PROVIDER + "=" + providerName + ")";
		ServiceReference oauthServiceReferences[];
		try {
			oauthServiceReferences = ctx.getServiceReferences(OAuthParamsFactory.class.getName(), filter);
		} catch (InvalidSyntaxException e) {
			throw new OAuthException(e);
		}
		if (oauthServiceReferences == null || oauthServiceReferences.length == 0) {
			throw new OAuthException("Plug-in for OAuth provider <" + providerName + "> is not running");
		}
		else if (oauthServiceReferences.length >= 2) {
			throw new OAuthException("Multiple services registered for OAuth provider <" + providerName + ">");
		}

		return (OAuthParamsFactory) ctx.getService(oauthServiceReferences[0]);
	}

	private OAuthParams getOAuthParams(HttpServletRequest req, String type, boolean login) throws OAuthException {
		oauthParams = getParamsFactory(type).getOAuthParams(req, login);
		return getOAuthParams();
	}

	private OAuthParams getOAuthParams() throws OAuthException{
		if (oauthParams == null)
			throw new OAuthException("No OAuth provider given");
		return oauthParams;
	}

	public void handleGetAndLink(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try{
			handleGet(req, resp, false);
		} catch (OAuthException e) {
			writeOAuthError(e.getMessage(), req, resp);
		}
	}

	public void handleGetAndLogin(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, OAuthException {
		handleGet(req, resp, true);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$
		if (pathInfo.startsWith("/oauth")){
			handleGetAndLink(req, resp);
		}
	}
}
