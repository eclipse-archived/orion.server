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
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private JSONObject manifest;

	private String appName;
	private String appHost;
	private String domainGUID;

	public CreateRouteCommand(String userId, Target target, JSONObject manifest, String domainGUID) {
		super(target, userId);
		this.commandName = "Create route";
		this.manifest = manifest;
		this.domainGUID = domainGUID;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* create cloud foundry application */
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI routesURI = targetURI.resolve("/v2/routes");

			PostMethod createRouteMethod = new PostMethod(routesURI.toString());
			HttpUtil.configureHttpMethod(createRouteMethod, target);

			/* set request body */
			JSONObject routeRequest = new JSONObject();
			routeRequest.put(CFProtocolConstants.V2_KEY_SPACE_GUID, target.getSpace().getCFJSON().getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
			routeRequest.put(CFProtocolConstants.V2_KEY_HOST, appHost);
			routeRequest.put(CFProtocolConstants.V2_KEY_DOMAIN_GUID, domainGUID);
			createRouteMethod.setRequestEntity(new StringRequestEntity(routeRequest.toString(), "application/json", "utf-8"));

			return HttpUtil.executeMethod(createRouteMethod);

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
			JSONObject appJSON = manifest.getJSONArray(CFProtocolConstants.V2_KEY_APPLICATIONS).getJSONObject(0);

			appName = appJSON.getString(CFProtocolConstants.V2_KEY_NAME); /* required */

			/* if none provided, sets a default one */
			String inputHost = appJSON.optString(CFProtocolConstants.V2_KEY_HOST);
			appHost = (!inputHost.isEmpty()) ? inputHost : (appName + "-" + UUID.randomUUID());

			return Status.OK_STATUS;

		} catch (Exception ex) {
			/* parse exception, fail */
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}
