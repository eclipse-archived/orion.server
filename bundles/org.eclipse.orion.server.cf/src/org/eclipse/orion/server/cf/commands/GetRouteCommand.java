/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others 
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
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String domainName;
	private String hostName;

	public GetRouteCommand(Target target, String domainName, String hostName) {
		super(target);
		this.commandName = "Get Route";
		this.domainName = domainName;
		this.hostName = hostName;
	}

	protected ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI routesURI = targetURI.resolve("/v2/shared_domains");
			GetMethod getSharedRoutesMethod = new GetMethod(routesURI.toString());
			HttpUtil.configureHttpMethod(getSharedRoutesMethod, target.getCloud());
			getSharedRoutesMethod.setQueryString("q=" + CFProtocolConstants.V2_KEY_NAME + ":" + domainName);

			ServerStatus getSharedRouteStatus = HttpUtil.executeMethod(getSharedRoutesMethod);
			if (!getSharedRouteStatus.isOK())
				return getSharedRouteStatus;

			JSONObject routesSharedJSON = getSharedRouteStatus.getJsonData();

			if (routesSharedJSON.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) >= 1) {

				JSONArray resources = routesSharedJSON.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
				String domainId = resources.getJSONObject(0).getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

				routesURI = targetURI.resolve("/v2/routes");
				GetMethod getRouteMethod = new GetMethod(routesURI.toString());
				HttpUtil.configureHttpMethod(getRouteMethod, target.getCloud());
				getRouteMethod.setQueryString("inline-relations-depth=1&" + "q=" + CFProtocolConstants.V2_KEY_HOST + ":" + hostName + ";" + CFProtocolConstants.V2_KEY_DOMAIN_GUID + ":" + domainId);

				ServerStatus getRouteStatus = HttpUtil.executeMethod(getRouteMethod);
				if (!getRouteStatus.isOK())
					return getRouteStatus;

				JSONObject result = getRouteStatus.getJsonData();
				if (result.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1) {
					return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null);
				}
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);

			}

			return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
