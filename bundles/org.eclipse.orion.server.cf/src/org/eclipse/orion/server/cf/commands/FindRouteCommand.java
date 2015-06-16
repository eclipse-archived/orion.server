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
package org.eclipse.orion.server.cf.commands;

import java.net.URI;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private App application;
	private String domainGUID;
	private String appHost;

	public String getAppHost() {
		return appHost;
	}

	private String commandName;

	protected FindRouteCommand(Target target, App app, String domainGUID) {
		super(target);
		this.application = app;
		this.domainGUID = domainGUID;

		String[] bindings = {domainGUID};
		this.commandName = NLS.bind("Find route within the given domain ({0})", bindings);
	}

	@Override
	protected ServerStatus _doIt() {
		try {

			/* create cloud foundry application */
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI routesURI = targetURI.resolve("/v2/routes"); //$NON-NLS-1$

			GetMethod findRouteMethod = new GetMethod(routesURI.toString());
			ServerStatus confStatus = HttpUtil.configureHttpMethod(findRouteMethod, target.getCloud());
			if (!confStatus.isOK())
				return confStatus;
			
			findRouteMethod.setQueryString("inline-relations-depth=1&q=host:" + appHost + ";domain_guid:" + domainGUID); //$NON-NLS-1$ //$NON-NLS-2$

			ServerStatus status = HttpUtil.executeMethod(findRouteMethod);
			if (!status.isOK())
				return status;

			JSONObject response = status.getJsonData();
			if (response.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Route not found", null);

			/* retrieve the GUID */
			JSONArray resources = response.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
			JSONObject route = resources.getJSONObject(0);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, route);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	@Override
	protected IStatus validateParams() {
		try {
			/* read deploy parameters */
			ManifestParseTree manifest = application.getManifest();
			ManifestParseTree app = manifest.get("applications").get(0); //$NON-NLS-1$

			String appName = null;
			if (application.getName() != null)
				appName = application.getName();
			else
				appName = app.get(CFProtocolConstants.V2_KEY_NAME).getValue();

			/* extract host information if present */
			ManifestParseTree hostNode = app.getOpt(CFProtocolConstants.V2_KEY_HOST);
			appHost = (hostNode != null) ? hostNode.getValue() : ManifestUtils.slugify(appName);

			if (appHost != null)
				return Status.OK_STATUS;

			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Route not found", null);

		} catch (InvalidAccessException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		}
	}
}
