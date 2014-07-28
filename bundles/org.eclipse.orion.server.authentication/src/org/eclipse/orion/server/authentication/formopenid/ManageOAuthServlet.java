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
package org.eclipse.orion.server.authentication.formopenid;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.authentication.oauth.GoogleOAuthParams;
import org.eclipse.orion.server.authentication.oauth.OAuthConsumer;
import org.eclipse.orion.server.authentication.oauth.OAuthException;
import org.eclipse.orion.server.authentication.oauth.OAuthHelper;
import org.eclipse.orion.server.authentication.oauth.OAuthParams;
import org.eclipse.orion.server.servlets.OrionServlet;

/**
 * Methods to handles OAuth requests.
 * @author Aidan Redpath
 *
 */
public class ManageOAuthServlet extends OrionServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3863741024714602634L;
	
	private OAuthParams oauthParams;

	private void handleGet(HttpServletRequest req, HttpServletResponse resp, Boolean login) throws ServletException, IOException, OAuthException {
		traceRequest(req);
		String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo(); //$NON-NLS-1$
		if (pathInfo.startsWith("/oauth")) {
			String oauthParam = req.getParameter(OAuthHelper.OAUTH);
			if(oauthParam != null){
					OAuthHelper.redirectToOAuthProvider(req, resp, getOAuthParams(oauthParam, login));
			}else {
				OAuthConsumer oauthConsumer = OAuthHelper.handleOAuthReturnAndTokenAccess(req, resp, getOAuthParams());
				if(login)
					OAuthHelper.handleLogin(req, resp, oauthConsumer);
				else
					OAuthHelper.handleReturnAndLinkAccount(req, resp, oauthConsumer);
			}		
		}
	}
	
	private OAuthParams getOAuthParams(String type, boolean login) throws OAuthException{
		if(type.equals("google")){
			oauthParams = new GoogleOAuthParams(login);
		}else{
			throw new OAuthException("No OAuth provider given");
		}
		return getOAuthParams();
	}
	
	private OAuthParams getOAuthParams(){
		return oauthParams;
	}
	
	public void handleGetAndLink(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, OAuthException {
		handleGet(req, resp, false);
	}
	
	public void handleGetAndLogin(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, OAuthException {
		handleGet(req, resp, true);
	}
}
