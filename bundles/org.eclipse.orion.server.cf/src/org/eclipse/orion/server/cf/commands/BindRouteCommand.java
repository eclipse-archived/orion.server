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

import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App application;
	private JSONObject manifest;

	/* shared properties */
	private String appDomain;
	private String domainName;
	private JSONObject route;

	public JSONObject getRoute() {
		return route;
	}

	public String getAppDomain() {
		return appDomain;
	}

	public String getDomainName() {
		return domainName;
	}

	public BindRouteCommand(String userId, Target target, App app, JSONObject manifest) {
		super(target, userId);
		this.commandName = "Bind route to application";
		this.application = app;
		this.manifest = manifest;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* get available domains */
			GetDomainsCommand getDomains = new GetDomainsCommand(userId, target);
			ServerStatus jobStatus = (ServerStatus) getDomains.doIt(); /* TODO: unsafe type cast */
			if (!jobStatus.isOK())
				return jobStatus;

			/* extract available domains */
			JSONObject domains = jobStatus.getJsonData();

			if (domains.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

			String domainGUID = null;
			if (!appDomain.isEmpty()) {
				/* look if the domain is available */
				int resources = domains.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).length();
				for (int k = 0; k < resources; ++k) {
					JSONObject resource = domains.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(k);
					String tmpDomainName = resource.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_NAME);
					if (appDomain.equals(tmpDomainName)) {
						domainGUID = resource.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);
						domainName = tmpDomainName;
						break;
					}
				}

				/* client requested an unavailable domain, fail */
				if (domainGUID == null)
					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);

			} else {
				/* client has not requested a specific domain, get the first available */
				JSONObject resource = domains.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(0);
				domainName = resource.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_NAME);
				domainGUID = resource.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);
			}

			/* new application, we do not need to check for attached routes, create a new one */
			CreateRouteCommand createRoute = new CreateRouteCommand(userId, target, manifest, domainGUID);
			jobStatus = (ServerStatus) createRoute.doIt(); /* TODO: unsafe type cast */
			if (!jobStatus.isOK())
				return jobStatus;

			/* extract route guid */
			route = jobStatus.getJsonData();
			String routeGUID = route.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

			/* attach route to application */
			AttachRouteCommand attachRoute = new AttachRouteCommand(userId, target, application, routeGUID);
			jobStatus = (ServerStatus) attachRoute.doIt(); /* TODO: unsafe type cast */
			if (!jobStatus.isOK())
				return jobStatus;

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

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
			appDomain = appJSON.optString(CFProtocolConstants.V2_KEY_DOMAIN); /* optional */

			return Status.OK_STATUS;

		} catch (Exception ex) {
			/* parse exception, fail */
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}
