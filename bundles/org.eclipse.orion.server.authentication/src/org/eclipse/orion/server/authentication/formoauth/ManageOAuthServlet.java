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
import org.eclipse.orion.server.authentication.oauth.github.GitHubOAuthParams;
import org.eclipse.orion.server.authentication.oauth.google.GoogleOAuthParams;
import org.eclipse.orion.server.core.resources.Base64;

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
			resp.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
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
		resp.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
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

	private OAuthParams getOAuthParams(HttpServletRequest req, String type, boolean login) throws OAuthException{
		if(type.equals("google")){
			oauthParams = new GoogleOAuthParams(req, login);
		}else if(type.equals("github")){
			oauthParams = new GitHubOAuthParams(req, login);
		}else{
			throw new OAuthException("No OAuth provider given");
		}
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
