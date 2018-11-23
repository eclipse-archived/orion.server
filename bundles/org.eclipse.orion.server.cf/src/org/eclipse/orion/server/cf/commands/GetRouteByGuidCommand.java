/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
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
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.cf.objects.Route;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetRouteByGuidCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String routeGuid;
	private Route route;

	public GetRouteByGuidCommand(Cloud cloud, String routeGuid) {
		super(cloud);
		this.commandName = "Get route";
		this.routeGuid = routeGuid;
	}

	public ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(getCloud().getUrl());

			// Get the app
			URI appsURI = targetURI.resolve("/v2/routes/" + routeGuid);
			GetMethod getRoutesMethod = new GetMethod(appsURI.toString());
			HttpUtil.configureHttpMethod(getRoutesMethod, getCloud());

			ServerStatus getStatus = HttpUtil.executeMethod(getRoutesMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject routeJSON = getStatus.getJsonData();
			this.route = new Route().setCFJSON(routeJSON);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, this.route.toJSON());
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	public Route getRoute() {
		return route;
	}
}
