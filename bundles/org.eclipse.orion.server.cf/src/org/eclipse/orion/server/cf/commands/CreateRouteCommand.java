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
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.objects.App;
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
	private App application;

	private String appName;
	private String appHost;
	private String domainGUID;

	public CreateRouteCommand(Target target, App app, String domainGUID) {
		super(target);

		String[] bindings = {domainGUID};
		this.commandName = NLS.bind("Create a new route (domain guid: {0})", bindings);

		this.application = app;
		this.domainGUID = domainGUID;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* create cloud foundry application */
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI routesURI = targetURI.resolve("/v2/routes"); //$NON-NLS-1$

			PostMethod createRouteMethod = new PostMethod(routesURI.toString());
			HttpUtil.configureHttpMethod(createRouteMethod, target);

			/* set request body */
			JSONObject routeRequest = new JSONObject();
			routeRequest.put(CFProtocolConstants.V2_KEY_SPACE_GUID, target.getSpace().getCFJSON().getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
			routeRequest.put(CFProtocolConstants.V2_KEY_HOST, appHost);
			routeRequest.put(CFProtocolConstants.V2_KEY_DOMAIN_GUID, domainGUID);
			createRouteMethod.setRequestEntity(new StringRequestEntity(routeRequest.toString(), "application/json", "utf-8")); //$NON-NLS-1$//$NON-NLS-2$

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
			ManifestParseTree manifest = application.getManifest();
			ManifestParseTree app = manifest.get("applications").get(0); //$NON-NLS-1$
			appName = app.get(CFProtocolConstants.V2_KEY_NAME).getValue();

			/* if none provided, generate a default one */
			ManifestParseTree hostNode = app.getOpt(CFProtocolConstants.V2_KEY_HOST);
			appHost = (hostNode != null) ? hostNode.getValue() : (appName + "-" + UUID.randomUUID()); //$NON-NLS-1$

			return Status.OK_STATUS;

		} catch (InvalidAccessException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		}
	}
}
