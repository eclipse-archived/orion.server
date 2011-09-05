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
package org.eclipse.orion.server.authentication.formopenid.servlets;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;
import org.eclipse.orion.server.authentication.formopenid.Activator;
import org.eclipse.orion.server.authentication.formopenid.FormOpenIdAuthenticationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.openid.core.OpenIdException;
import org.eclipse.orion.server.openid.core.OpenIdHelper;
import org.eclipse.orion.server.openid.core.OpenidConsumer;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.server.useradmin.UnsupportedUserStoreException;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;

public class FormOpenIdLoginServlet extends OrionServlet {

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
						if (req.getParameter(OpenIdHelper.REDIRECT) != null && !req.getParameter(OpenIdHelper.REDIRECT).equals("")) { //$NON-NLS-1$
							resp.sendRedirect(req.getParameter(OpenIdHelper.REDIRECT));
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
					displayError("Invalid user or password", req, resp);
				}
			} catch (UnsupportedUserStoreException e) {
				LogHelper.log(e);
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
			}
			return;
		}

		if (pathInfo.startsWith("/openid")) { //$NON-NLS-1$
			String openid = req.getParameter(OpenIdHelper.OPENID);
			if (openid != null) {
				try {
					consumer = OpenIdHelper.redirectToOpenIdProvider(req, resp, consumer);
				} catch (OpenIdException e) {
					LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, "An error occured redirecting to OpenId provider", e));
					displayError(e.getMessage(), req, resp);
				}
				return;
			}

			String op_return = req.getParameter(OpenIdHelper.OP_RETURN);
			if (op_return != null) {
				try {
					OpenIdHelper.handleOpenIdReturnAndLogin(req, resp, consumer);
				} catch (OpenIdException e) {
					e.printStackTrace();//FIXME
					displayError(e.getMessage(), req, resp);
				}
				return;
			}
		}
		
		if(pathInfo.startsWith("/canaddusers")){
			JSONObject jsonResp = new JSONObject();
			try {
				jsonResp.put("CanAddUsers", FormAuthHelper.canAddUsers());
			} catch (JSONException e) {
			}
			resp.getWriter().print(jsonResp);
			resp.setContentType("application/json");
			return;
		}

		String user;
		if ((user = authenticationService.getAuthenticatedUser(req, resp, authenticationService.getDefaultAuthenticationProperties())) != null) {
			resp.setStatus(HttpServletResponse.SC_OK);
			try {
				resp.getWriter().print(FormAuthHelper.getUserJson(user));
			} catch (JSONException e) {
				handleException(resp, "An error occured when creating JSON object for logged in user", e);
			}
			return;
		}
	}
	
	private void displayError(String error, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
		// redirection from
		// FormAuthenticationService.setNotAuthenticated
		String versionString = req.getHeader("Orion-Version"); //$NON-NLS-1$
		Version version = versionString == null ? null : new Version(versionString);

		// TODO: This is a workaround for calls
		// that does not include the WebEclipse version header
		String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

		if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
			String url = "/mixloginstatic/LoginWindow.html";
			if(req.getParameter("redirect")!=null){
				url+="?redirect=" + req.getParameter("redirect");
			}
			
			if(error==null){
				error="Invalid login";				
			}
			url+=url.contains("?") ? "&" : "?";
			url+= "error=" + new String(Base64.encode(error.getBytes()));
			
			resp.sendRedirect(url);
			
		} else {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			PrintWriter writer = resp.getWriter();
			JSONObject jsonError = new JSONObject();
			try {
				jsonError.put("error", error); //$NON-NLS-1$
				writer.print(jsonError);
				resp.setContentType("application/json"); //$NON-NLS-1$
			} catch (JSONException e) {/* ignore */
			}
		}
		resp.flushBuffer();
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
