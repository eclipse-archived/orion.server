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
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.objects.Route;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String domainName;
	private String hostName;

	private List<Route> routes;

	public CheckRouteCommand(Target target, String domainName, String hostName) {
		super(target);
		this.commandName = "Check Route";
		this.domainName = domainName;
		this.hostName = hostName;
		
	}

	public List<Route> getRoute() {
		assertWasRun();
		return routes;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());
			ServerStatus getRouteStatus;

			GetDomainsCommand getDomainsCommand = new GetDomainsCommand(target, domainName);
			ServerStatus getDomainsStatus = (ServerStatus) getDomainsCommand.doIt();
			if (!getDomainsStatus.isOK())
				return getDomainsStatus;

			String domainId = getDomainsCommand.getDomains().get(0).getGuid();

			URI routesURI = targetURI.resolve("/v2/routes/reserved/domain/" + domainId + "/host/" + hostName);
			GetMethod getRouteMethod = new GetMethod(routesURI.toString());
			HttpUtil.configureHttpMethod(getRouteMethod, target.getCloud());

			getRouteStatus = HttpUtil.executeMethod(getRouteMethod);

			if (!getRouteStatus.isOK()){
				if(getRouteStatus.getHttpCode() == 404 && getRouteStatus.getJsonData().getString("error_code").equals("CF-NotFound"))
					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, null);
				return getRouteStatus;
			}
			JSONObject result = new JSONObject();
			if (getRouteStatus.getMessage().equals("OK")){
				result.append("Route", getRouteStatus.toJSON());
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);	

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
