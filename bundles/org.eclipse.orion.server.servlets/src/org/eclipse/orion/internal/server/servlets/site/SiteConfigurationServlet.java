/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.site;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Servlet for managing site configurations.
 */
public class SiteConfigurationServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	private ServletResourceHandler<SiteConfiguration> siteConfigurationResourceHandler;

	public SiteConfigurationServlet() {
		siteConfigurationResourceHandler = new SiteConfigurationResourceHandler(getStatusHandler());
	}

	@Override
	protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 0) {
			doGetAllSiteConfigurations(req, resp);
			return;
		} else if (pathInfo.segmentCount() == 1) {
			SiteConfiguration site = getExistingSiteConfig(req, resp);
			if (siteConfigurationResourceHandler.handleRequest(req, resp, site)) {
				return;
			}
		} else {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request", null));
			return;
		}
		super.doGet(req, resp);
	}

	@Override
	protected synchronized void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 0) {
			if (siteConfigurationResourceHandler.handleRequest(req, resp, null /*doesn't exist yet*/)) {
				return;
			}
		} else {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request", null));
			return;
		}
		super.doPost(req, resp);
	}

	@Override
	protected synchronized void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 1) {
			SiteConfiguration site = getExistingSiteConfig(req, resp);
			if (siteConfigurationResourceHandler.handleRequest(req, resp, site)) {
				return;
			}
		} else {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request", null));
			return;
		}
		super.doPut(req, resp);
	}

	@Override
	protected synchronized void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		if (getPathInfo(req).segmentCount() == 1) {
			SiteConfiguration site = getExistingSiteConfig(req, resp);
			if (siteConfigurationResourceHandler.handleRequest(req, resp, site)) {
				return;
			}
		} else {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request", null));
		}
		super.doDelete(req, resp);
	}

	/**
	 * @return The SiteConfiguration whose id matches the 0th segment of the request pathInfo, or null.
	 */
	private SiteConfiguration getExistingSiteConfig(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String userName = getUserName(req);
		WebUser user = WebUser.fromUserId(userName);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 1) {
			return user.getSiteConfiguration(pathInfo.segment(0));
		}
		return null;
	}

	// TODO: allow filtering by hosting state via query parameter?
	private boolean doGetAllSiteConfigurations(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String userName = getUserName(req);
		try {
			WebUser user = WebUser.fromUserId(userName);
			JSONArray siteConfigurations = user.getSiteConfigurationsJSON(ServletResourceHandler.getURI(req));
			JSONObject jsonResponse = new JSONObject();
			jsonResponse.put(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS, siteConfigurations);
			writeJSONResponse(req, resp, jsonResponse, JsonURIUnqualificationStrategy.LOCATION_ONLY);
		} catch (Exception e) {
			handleException(resp, "An error occurred while obtaining site configurations", e);
		}
		return true;
	}

	/**
	 * @return The request's PathInfo as an IPath.
	 */
	private static IPath getPathInfo(HttpServletRequest req) {
		String pathString = req.getPathInfo();
		IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
		if (req.getContextPath().length() != 0) {
			IPath contextPath = new Path(req.getContextPath());
			if (contextPath.isPrefixOf(path)) {
				path = path.removeFirstSegments(contextPath.segmentCount());
			}
		}
		return path;
	}

	/**
	 * Obtain and return the user name from the request headers.
	 */
	private static String getUserName(HttpServletRequest req) {
		return req.getRemoteUser();
	}

}
