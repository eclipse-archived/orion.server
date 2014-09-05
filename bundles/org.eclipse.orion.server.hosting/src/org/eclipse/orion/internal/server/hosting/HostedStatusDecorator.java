/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.hosting;

import org.eclipse.orion.server.core.ProtocolConstants;

import org.eclipse.orion.server.core.IWebResourceDecorator;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.json.*;

/**
 * Adds information about the hosting state of a site configuration to its JSON representation.
 */
public class HostedStatusDecorator implements IWebResourceDecorator {

	private static final String SITE_CONFIGURATION_SERVLET_ALIAS = "site"; //$NON-NLS-1$

	@Override
	public void addAtributesFor(HttpServletRequest req, URI resource, JSONObject representation) {
		IPath path = new Path(req.getPathInfo() == null ? "" : req.getPathInfo());

		if (!(("/" + SITE_CONFIGURATION_SERVLET_ALIAS).equals(req.getServletPath())))
			return;

		try {
			UserInfo webUser = getWebUser(req);
			if (path.segmentCount() == 0) {
				if ("GET".equals(req.getMethod())) { //$NON-NLS-1$
					// GET /site/ (get all site configs) 
					JSONArray siteConfigurations = representation.optJSONArray(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS);
					if (siteConfigurations != null) {
						for (int i = 0; i < siteConfigurations.length(); i++) {
							addStatus(req, siteConfigurations.getJSONObject(i), webUser, resource);
						}
					}
				} else if ("POST".equals(req.getMethod())) { //$NON-NLS-1$
					// POST /site/ (create a site config)
					addStatus(req, representation, webUser, resource);
				}
			} else if (path.segmentCount() == 1) {
				// GET /site/siteConfigId (get a single site config)
				addStatus(req, representation, webUser, resource);
			}
		} catch (JSONException e) {
			// Shouldn't happen, but since we are just decorating someone else's response we shouldn't cause a failure
			LogHelper.log(e);
		}
	}

	private static UserInfo getWebUser(HttpServletRequest req) {
		String remoteUser = req.getRemoteUser();
		if (remoteUser != null) {
			try {
				return HostingActivator.getDefault().getMetastore().readUser(remoteUser);
			} catch (CoreException e) {
				//ignore and fall through
			}
		}
		return null;
	}

	/**
	 * Adds status field to a representation of a site configuration.
	 * @param siteConfigJson The JSONObject representing a single site configuration.
	 * @param user The user making the request.
	 * @param resource The original request passed to the decorator.
	 */
	private void addStatus(HttpServletRequest req, JSONObject siteConfigJson, UserInfo user, URI resource) throws JSONException {
		String id = siteConfigJson.optString(ProtocolConstants.KEY_ID);
		if (id == null) {
			return;
		}
		SiteInfo siteConfiguration = SiteInfo.getSite(user, id);
		if (siteConfiguration == null)
			return;
		IHostedSite site = HostingActivator.getDefault().getHostingService().get(siteConfiguration, user);
		JSONObject hostingStatus = new JSONObject();
		if (site != null) {
			try {
				hostingStatus.put(SiteConfigurationConstants.KEY_HOSTING_STATUS_STATUS, "started"); //$NON-NLS-1$
				// Site generates a root URL, need to add contextPath
				URI uri = new URI(site.getUrl());
				URI newURI = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), req.getContextPath(), null, null);
				hostingStatus.put(SiteConfigurationConstants.KEY_HOSTING_STATUS_URL, newURI.toString());
			} catch (URISyntaxException e) {
				LogHelper.log(e);
			}
		} else {
			hostingStatus.put(SiteConfigurationConstants.KEY_HOSTING_STATUS_STATUS, "stopped"); //$NON-NLS-1$
		}
		siteConfigJson.put(SiteConfigurationConstants.KEY_HOSTING_STATUS, hostingStatus);
	}
}
