/*******************************************************************************
 * Copyright (c) 2011, 2016 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.authentication.IAuthenticationService;
import org.eclipse.orion.server.core.EncodingUtils;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.osgi.service.http.HttpContext;

/**
 * The filter checks whether the request is made by an authorized user.
 */
public class AuthorizedUserFilter implements Filter {

	private IAuthenticationService authenticationService;

	public void init(FilterConfig filterConfig) throws ServletException {
		while (Activator.getDefault() == null) {
			// the singleton for the activator will return null if the bundle
			// is not yet active. We can wait for it in this filter.
			String msg = "Authentication service is not active. AuthorizedUserFilter is waiting."; //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, msg, null));
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				break;
			}
		}
		while (Activator.getDefault().getAuthService() == null) {
			// the authentication service will be null if the bundle
			// is not yet active. We can wait for it in this filter.
			String msg = "Authentication service is not active. AuthorizedUserFilter is waiting. The server configuration must specify an authentication scheme, or use \"None\" to indicate no authentication"; //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, msg, null));
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				break;
			}
		}
		authenticationService = Activator.getDefault().getAuthService();
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String remoteUser = httpRequest.getRemoteUser();

		String userName = remoteUser;
		if (userName == null) {
			userName = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
			if (userName == null)
				userName = IAuthenticationService.ANONYMOUS_LOGIN_VALUE;
		}

		try {
			String requestPath = httpRequest.getServletPath() + (httpRequest.getPathInfo() == null ? "" : httpRequest.getPathInfo());
			if(httpRequest.getHeader("checkExistence") != null){
				//Automatically add sourceLoc and username-OrionContent to the request path;
				String[] requestPathSplits = requestPath.split("/");
				String sourceLoc = requestPathSplits[1];
				requestPathSplits[1] = userName + "-OrionContent";
				requestPath = requestPath.join("/", requestPathSplits);
				requestPath =  "/" + sourceLoc + requestPath;
				httpRequest.setAttribute("checkExistence-userName", userName);
			}
			if (!AuthorizationService.checkRights(userName, requestPath, httpRequest.getMethod())) {
				if (IAuthenticationService.ANONYMOUS_LOGIN_VALUE.equals(userName)) {
					userName = authenticationService.authenticateUser(httpRequest, httpResponse);
					if (userName == null)
						return;
				} else {
					setNotAuthorized(httpRequest, httpResponse, requestPath);
					return;
				}
			}

			String xCreateOptions = httpRequest.getHeader("X-Create-Options");
			if (xCreateOptions != null) {
				String sourceLocation = null;;
				try {
					String method = xCreateOptions.contains("move") ? "POST" : "GET";
					JSONObject requestObject = OrionServlet.readJSONRequest(httpRequest);
					sourceLocation = requestObject.getString(ProtocolConstants.KEY_LOCATION);
					String normalizedLocation = new URI(sourceLocation).normalize().getPath();
					normalizedLocation = normalizedLocation.startsWith(httpRequest.getContextPath()) ? normalizedLocation.substring(httpRequest.getContextPath().length()) : null;
					if (normalizedLocation == null || !AuthorizationService.checkRights(userName, normalizedLocation, method)) {
						setNotAuthorized(httpRequest, httpResponse, sourceLocation);
						return;
					}
				} catch (URISyntaxException e) {
					setNotAuthorized(httpRequest, httpResponse, sourceLocation);
					return;
				} catch (JSONException e) {
					// ignore, and fall through
				}
			}

			if (remoteUser == null && !IAuthenticationService.ANONYMOUS_LOGIN_VALUE.equals(userName)) {
				request.setAttribute(HttpContext.REMOTE_USER, userName);
				request.setAttribute(HttpContext.AUTHENTICATION_TYPE, authenticationService.getAuthType());
			}
		} catch (CoreException e) {
			httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		chain.doFilter(request, response);
	}

	private void setNotAuthorized(HttpServletRequest req, HttpServletResponse resp, String resourceURL) throws IOException {
		String versionString = req.getHeader("Orion-Version"); //$NON-NLS-1$
		Version version = versionString == null ? null : new Version(versionString);

		// TODO: This is a workaround for calls
		// that does not include the WebEclipse version header
		String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

		String msg = "You are not authorized to access " + resourceURL;
		if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, msg);
		} else {
			resp.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
			ServerStatus serverStatus = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, EncodingUtils.encodeForHTML(msg), null);
			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			resp.getWriter().print(serverStatus.toJSON().toString());
		}
	}

	public void destroy() {
		// TODO Auto-generated method stub
	}
}
