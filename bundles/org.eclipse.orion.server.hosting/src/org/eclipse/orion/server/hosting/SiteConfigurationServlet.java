/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.hosting;

import java.io.IOException;
import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.hosting.SiteConfigurationConstants;
import org.eclipse.orion.internal.server.hosting.SiteConfigurationResourceHandler;
import org.eclipse.orion.internal.server.hosting.SiteInfo;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Servlet for managing site configurations.
 */
public class SiteConfigurationServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	private ServletResourceHandler<SiteInfo> siteConfigurationResourceHandler;

	public SiteConfigurationServlet() {
		siteConfigurationResourceHandler = new SiteConfigurationResourceHandler(getStatusHandler());
	}

	@Override
	protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String userName = req.getRemoteUser();
		if (userName == null) {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request: authenticated user is null", null));
			return;
		}
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 0) {
			doGetAllSiteConfigurations(req, resp, userName);
			return;
		} else if (pathInfo.segmentCount() == 1) {
			SiteInfo site = getExistingSiteConfig(req, resp, userName);
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
		String userName = req.getRemoteUser();
		if (userName == null) {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request: authenticated user is null", null));
			return;
		}
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 1) {
			SiteInfo site = getExistingSiteConfig(req, resp, userName);
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
		String userName = req.getRemoteUser();
		if (userName == null) {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request: authenticated user is null", null));
			return;
		}
		if (getPathInfo(req).segmentCount() == 1) {
			SiteInfo site = getExistingSiteConfig(req, resp, userName);
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
	private SiteInfo getExistingSiteConfig(HttpServletRequest req, HttpServletResponse resp, String userName) {
		IPath pathInfo = getPathInfo(req);
		try {
			//bad request
			if (pathInfo.segmentCount() != 1)
				return null;
			UserInfo user = OrionConfiguration.getMetaStore().readUser(userName);
			return SiteInfo.getSite(user, pathInfo.segment(0));
		} catch (CoreException e) {
			//backing store failure
			LogHelper.log(e);
		}
		return null;
	}

	// TODO: allow filtering by hosting state via query parameter?
	private boolean doGetAllSiteConfigurations(HttpServletRequest req, HttpServletResponse resp, String userName) throws ServletException {
		try {
			UserInfo user = OrionConfiguration.getMetaStore().readUser(userName);
			//user info stores an object where key is site id, value is site info, but we just want the values
			URI base = ServletResourceHandler.getURI(req);
			JSONArray configurations = new JSONArray();
			JSONObject sites = SiteInfo.getSites(user);
			final String[] names = JSONObject.getNames(sites);
			if (names != null) {
				for (String siteId : names) {
					//add site resource location based on current request URI
					final JSONObject siteInfo = sites.getJSONObject(siteId);
					siteInfo.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(base, siteId));
					configurations.put(siteInfo);
				}
			}
			JSONObject jsonResponse = new JSONObject();
			jsonResponse.put(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS, configurations);
			writeJSONResponse(req, resp, jsonResponse, JsonURIUnqualificationStrategy.LOCATION_ONLY);
		} catch (Exception e) {
			LogHelper.log(e);
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
}
