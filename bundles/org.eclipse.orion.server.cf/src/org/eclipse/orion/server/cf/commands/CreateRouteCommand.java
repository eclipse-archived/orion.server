/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others 
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
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.cf.objects.*;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private Domain domain;
	private String hostName;
	private App app;

	private Route route;

	public CreateRouteCommand(Target target, Domain domain, String hostName) {
		super(target);

		this.domain = domain;
		this.hostName = hostName;
		this.commandName = NLS.bind("Create a new route (domain guid: {0})", domain.getGuid());
	}

	public CreateRouteCommand(Target target, Domain domain, App app) {
		super(target);

		this.domain = domain;
		this.app = app;
		this.commandName = NLS.bind("Create a new route (domain guid: {0})", domain.getGuid());
	}

	public Route getRoute() {
		return route;
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
			routeRequest.put(CFProtocolConstants.V2_KEY_HOST, hostName);
			routeRequest.put(CFProtocolConstants.V2_KEY_DOMAIN_GUID, domain.getGuid());
			createRouteMethod.setRequestEntity(new StringRequestEntity(routeRequest.toString(), "application/json", "utf-8")); //$NON-NLS-1$//$NON-NLS-2$
			createRouteMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

			ServerStatus createRouteStatus = HttpUtil.executeMethod(createRouteMethod);
			if (!createRouteStatus.isOK())
				return createRouteStatus;

			route = new Route().setCFJSON(createRouteStatus.getJsonData());

			return createRouteStatus;
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	@Override
	protected IStatus validateParams() {
		try {
			if (app == null && hostName != null)
				return Status.OK_STATUS;
			else if (app == null && hostName == null)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Missing host name parameter", null);

			/* read deploy parameters */
			ManifestParseTree manifest = app.getManifest();
			ManifestParseTree appNode = manifest.get("applications").get(0); //$NON-NLS-1$

			String appName = null;
			if (app.getName() != null)
				appName = app.getName();
			else
				appName = appNode.get(CFProtocolConstants.V2_KEY_NAME).getValue();

			/* if none provided, generate a default one */
			ManifestParseTree hostNode = appNode.getOpt(CFProtocolConstants.V2_KEY_HOST);
			hostName = (hostNode != null) ? hostNode.getValue() : ManifestUtils.slugify(appName);

			return Status.OK_STATUS;
		} catch (InvalidAccessException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		}
	}
}
