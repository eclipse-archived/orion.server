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
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.Route;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetRoutesCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	public GetRoutesCommand(String userId, Target target) {
		super(target);
		this.commandName = "Get Spaces";
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* get available orgs */
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI orgsURI = targetURI.resolve("/v2/routes");

			GetMethod getRoutesMethod = new GetMethod(orgsURI.toString());
			HttpUtil.configureHttpMethod(getRoutesMethod, target);
			getRoutesMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

			ServerStatus status = HttpUtil.executeMethod(getRoutesMethod);
			if (!status.isOK())
				return status;

			/* extract available routes */
			JSONObject routes = status.getJsonData();

			if (routes.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1) {
				return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null);
			}

			JSONObject result = new JSONObject();
			JSONArray resources = routes.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
			for (int k = 0; k < resources.length(); ++k) {
				JSONObject routeJSON = resources.getJSONObject(k);
				Route route = new Route();
				route.setCFJSON(routeJSON);
				result.append("Routes", route.toJSON());
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
