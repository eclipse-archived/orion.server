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
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.objects.Route;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private Route route;

	public DeleteRouteCommand(Target target, Route route) {
		super(target);
		this.commandName = "Delete the route";
		this.route = route;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());

			/* delete the route */
			URI routeURI = targetURI.resolve("/v2/routes/" + route.getGuid()); //$NON-NLS-1$

			DeleteMethod deleteRouteMethod = new DeleteMethod(routeURI.toString());
			HttpUtil.configureHttpMethod(deleteRouteMethod, target);
			deleteRouteMethod.setQueryString("recursive=true"); //$NON-NLS-1$

			ServerStatus status = HttpUtil.executeMethod(deleteRouteMethod);
			return status;

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName);
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	@Override
	protected IStatus validateParams() {
		return Status.OK_STATUS;
	}
}
