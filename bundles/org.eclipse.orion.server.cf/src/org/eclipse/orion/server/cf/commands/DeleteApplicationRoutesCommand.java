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
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteApplicationRoutesCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String appName;
	private App app;

	public DeleteApplicationRoutesCommand(Target target, App app) {
		super(target);
		this.commandName = "Delete application route";
		this.app = app;
	}

	@Override
	protected ServerStatus _doIt() {
		MultiServerStatus status = new MultiServerStatus();

		try {
			URI targetURI = URIUtil.toURI(target.getUrl());

			// get app details
			// TODO: it should be passed along with App object
			String appsUrl = target.getSpace().getCFJSON().getJSONObject("entity").getString("apps_url"); //$NON-NLS-1$//$NON-NLS-2$
			URI appsURI = targetURI.resolve(appsUrl);
			GetMethod getAppsMethod = new GetMethod(appsURI.toString());
			HttpUtil.configureHttpMethod(getAppsMethod, target.getCloud());
			getAppsMethod.setQueryString("q=name:" + appName + "&inline-relations-depth=1"); //$NON-NLS-1$ //$NON-NLS-2$

			ServerStatus appsStatus = HttpUtil.executeMethod(getAppsMethod);
			status.add(appsStatus);
			if (!status.isOK())
				return status;

			JSONObject jsonData = appsStatus.getJsonData();
			if (!jsonData.has("resources") || jsonData.getJSONArray("resources").length() == 0) //$NON-NLS-1$//$NON-NLS-2$
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Application not found", null);
			JSONArray apps = jsonData.getJSONArray("resources");

			// get app routes
			String routesUrl = apps.getJSONObject(0).getJSONObject("entity").getString("routes_url");
			URI routesURI = targetURI.resolve(routesUrl);
			GetMethod getRoutesMethod = new GetMethod(routesURI.toString());
			HttpUtil.configureHttpMethod(getRoutesMethod, target.getCloud());

			ServerStatus routesStatus = HttpUtil.executeMethod(getRoutesMethod);
			status.add(routesStatus);
			if (!status.isOK())
				return status;

			jsonData = routesStatus.getJsonData();
			if (!jsonData.has("resources") || jsonData.getJSONArray("resources").length() == 0) //$NON-NLS-1$//$NON-NLS-2$
				return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, "No routes for the app", null);
			JSONArray routes = jsonData.getJSONArray("resources");

			for (int i = 0; i < routes.length(); ++i) {
				JSONObject route = routes.getJSONObject(i);

				// delete route
				String routeUrl = route.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_URL);
				URI routeURI = targetURI.resolve(routeUrl); //$NON-NLS-1$
				DeleteMethod deleteRouteMethod = new DeleteMethod(routeURI.toString());
				HttpUtil.configureHttpMethod(deleteRouteMethod, target.getCloud());

				ServerStatus deleteStatus = HttpUtil.executeMethod(deleteRouteMethod);
				status.add(deleteStatus);
				if (!status.isOK())
					return status;
			}

			return status;

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName);
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	@Override
	protected IStatus validateParams() {
		try {
			/* read deploy parameters */
			ManifestParseTree manifest = app.getManifest();
			ManifestParseTree manifestApp = manifest.get("applications").get(0); //$NON-NLS-1$

			if (app.getName() != null) {
				appName = app.getName();
				return Status.OK_STATUS;
			}

			appName = manifestApp.get(CFProtocolConstants.V2_KEY_NAME).getValue();
			return Status.OK_STATUS;
		} catch (InvalidAccessException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		}
	}
}
