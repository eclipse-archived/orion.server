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

import java.util.Iterator;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.objects.*;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindRouteCommand extends AbstractCFApplicationCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	/* shared properties */
	private String appDomain;
	private Domain domain;
	private JSONObject route;
	private boolean noRoute;

	public JSONObject getRoute() {
		return route;
	}

	public String getAppDomain() {
		return appDomain;
	}

	public String getDomainName() {
		return (domain != null) ? domain.getDomainName() : null;
	}

	public BindRouteCommand(Target target, App app) {
		super(target, app);

		String[] bindings = {app.getName(), app.getGuid()};
		this.commandName = NLS.bind("Bind a new route to application {1} (guid: {2})", bindings);
	}

	@Override
	protected ServerStatus _doIt() {
		/* multi server status */
		MultiServerStatus status = new MultiServerStatus();

		try {

			if (noRoute)
				/* nothing to do */
				return status;

			/* get available domains */
			GetDomainsCommand getDomainsCommand = new GetDomainsCommand(target);
			ServerStatus jobStatus = (ServerStatus) getDomainsCommand.doIt(); /* FIXME: unsafe type cast */
			status.add(jobStatus);

			if (!jobStatus.isOK())
				return status;

			List<Domain> domains = getDomainsCommand.getDomains();
			if (domains == null || domains.size() == 0) {
				status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Failed to find available domains in target", null));
				return status;
			}

			if (!appDomain.isEmpty()) {
				/* look if the domain is available */
				for (Iterator<Domain> iterator = domains.iterator(); iterator.hasNext();) {
					Domain domain = iterator.next();
					if (appDomain.equals(domain.getDomainName())) {
						this.domain = domain;
						break;
					}
				}

				/* client requested an unavailable domain, fail */
				if (domain == null) {
					String msg = NLS.bind("Failed to find domain {0} in target", appDomain);
					status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
					return status;
				}
			} else {
				/* client has not requested a specific domain, get the first available */
				this.domain = domains.get(0);
			}

			/* find out whether the declared host can be reused */
			String routeGUID = null;
			FindRouteCommand findRouteCommand = new FindRouteCommand(target, getApplication(), domain.getGuid());
			jobStatus = (ServerStatus) findRouteCommand.doIt(); /* FIXME: unsafe type cast */
			status.add(jobStatus);

			if (jobStatus.isOK()) {
				/* extract route guid */
				route = jobStatus.getJsonData();
				routeGUID = route.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

				/* attach route to application */
				AttachRouteCommand attachRoute = new AttachRouteCommand(target, getApplication(), routeGUID);
				jobStatus = (ServerStatus) attachRoute.doIt(); /* FIXME: unsafe type cast */
				status.add(jobStatus);

				if (jobStatus.isOK())
					return status;

				/* the route is bound to another space */
				String msg = NLS.bind("The host {0} is already used in another space.", findRouteCommand.getAppHost());
				status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CONFLICT, msg, null));
				return status;
			}

			/* create a new route */
			CreateRouteCommand createRoute = new CreateRouteCommand(target, domain, getApplication());
			jobStatus = (ServerStatus) createRoute.doIt(); /* FIXME: unsafe type cast */
			status.add(jobStatus);

			if (!jobStatus.isOK())
				return status;

			/* extract route guid */
			route = jobStatus.getJsonData();
			routeGUID = route.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

			/* attach route to application */
			AttachRouteCommand attachRoute = new AttachRouteCommand(target, getApplication(), routeGUID);
			jobStatus = (ServerStatus) attachRoute.doIt(); /* FIXME: unsafe type cast */
			status.add(jobStatus);

			if (!jobStatus.isOK())
				return status;

			return status;

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return status;
		}
	}

	@Override
	protected IStatus validateParams() {
		try {
			/* read deploy parameters */
			ManifestParseTree manifest = getApplication().getManifest();
			ManifestParseTree app = manifest.get("applications").get(0); //$NON-NLS-1$

			/* optional */
			ManifestParseTree domainNode = app.getOpt(CFProtocolConstants.V2_KEY_DOMAIN);
			appDomain = (domainNode != null) ? domainNode.getValue() : ""; //$NON-NLS-1$

			ManifestParseTree noRouteNode = app.getOpt(CFProtocolConstants.V2_KEY_NO_ROUTE);
			noRoute = (noRouteNode != null) ? Boolean.parseBoolean(noRouteNode.getValue()) : false;

			return Status.OK_STATUS;

		} catch (InvalidAccessException e) {
			return new MultiServerStatus(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null));
		}
	}
}
