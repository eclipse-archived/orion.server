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
import org.eclipse.orion.server.cf.manifest.ManifestUtils;
import org.eclipse.orion.server.cf.manifest.ParseException;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteApplicationRoutesCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App application;
	private String appHost;
	private String appDomain;
	private String domainGUID;

	public DeleteApplicationRoutesCommand(Target target, App app) {
		super(target);
		this.commandName = "Delete application route";
		this.application = app;
	}

	@Override
	protected ServerStatus _doIt() {
		try {

			if (appHost.isEmpty())
				/* application route is generated at random, nothing to delete */
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

			ServerStatus getDomainJob = getDomainGUID();
			if (!getDomainJob.isOK())
				return getDomainJob;

			URI targetURI = URIUtil.toURI(target.getUrl());

			/* find routes attached to the application */
			URI routesURI = targetURI.resolve("/v2/routes"); //$NON-NLS-1$
			GetMethod getRoutesMethod = new GetMethod(routesURI.toString());
			HttpUtil.configureHttpMethod(getRoutesMethod, target);
			getRoutesMethod.setQueryString("inline-relations-depth=1&results-per-page=1000"); //$NON-NLS-1$ /* FIXME: Replace with page traversing */

			ServerStatus status = HttpUtil.executeMethod(getRoutesMethod);
			if (!status.isOK())
				return status;

			JSONArray routes = status.getJsonData().getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
			for (int i = 0; i < routes.length(); ++i) {

				JSONObject route = routes.getJSONObject(i);
				JSONObject routeEntity = route.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY);

				if (routeEntity.getString("host").equals(appHost) && routeEntity.getString("domain_guid").equals(domainGUID)) { //$NON-NLS-1$ //$NON-NLS-2$

					/* delete route */
					String routeGUID = route.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);
					URI routeURI = targetURI.resolve("/v2/routes/" + routeGUID); //$NON-NLS-1$
					DeleteMethod deleteRouteMethod = new DeleteMethod(routeURI.toString());
					HttpUtil.configureHttpMethod(deleteRouteMethod, target);

					status = HttpUtil.executeMethod(deleteRouteMethod);
					if (!status.isOK())
						return status;
				}
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
			JSONObject appJSON = ManifestUtils.getApplication(application.getManifest());
			appDomain = appJSON.optString(CFProtocolConstants.V2_KEY_DOMAIN); /* optional */

			/* if none provided, sets a default one */
			String inputHost = appJSON.optString(CFProtocolConstants.V2_KEY_HOST);
			appHost = (!inputHost.isEmpty()) ? inputHost : ""; //$NON-NLS-1$

			return Status.OK_STATUS;

		} catch (ParseException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		}
	}

	private ServerStatus getDomainGUID() {
		try {
			/* get available domains */
			GetDomainsCommand getDomains = new GetDomainsCommand(target);
			ServerStatus status = (ServerStatus) getDomains.doIt(); /* FIXME: unsafe type cast */

			if (!status.isOK())
				return status;

			/* extract available domains */
			JSONObject domains = status.getJsonData();

			if (domains.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Failed to find available domains in target", null);

			if (!appDomain.isEmpty()) {
				/* look if the domain is available */
				int resources = domains.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).length();
				for (int k = 0; k < resources; ++k) {
					JSONObject resource = domains.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(k);
					String tmpDomainName = resource.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_NAME);
					if (appDomain.equals(tmpDomainName)) {
						domainGUID = resource.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);
						break;
					}
				}

				/* client requested an unavailable domain, fail */
				if (domainGUID == null) {
					String msg = NLS.bind("Failed to find domain {1} in target", appDomain);
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
				}

			} else {
				/* client has not requested a specific domain, get the first available */
				JSONObject resource = domains.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(0);
				domainGUID = resource.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);
			}

			return status;
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName);
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
