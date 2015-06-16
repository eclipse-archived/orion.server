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

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.methods.PutMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttachRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App application;
	private String routeGUID;

	public AttachRouteCommand(Target target, App app, String routeGUID) {
		super(target);

		String[] bindings = {routeGUID, app.getName(), app.getGuid()};
		this.commandName = NLS.bind("Attach route (guid: {0}) to application {1} (guid: {2})", bindings);

		this.application = app;
		this.routeGUID = routeGUID;
	}

	@Override
	protected ServerStatus _doIt() {
		try {

			/* attach route to application */
			URI targetURI = URIUtil.toURI(target.getUrl());
			PutMethod attachRouteMethod = new PutMethod(targetURI.resolve("/v2/apps/" + application.getGuid() + "/routes/" + routeGUID).toString()); //$NON-NLS-1$//$NON-NLS-2$
			ServerStatus confStatus = HttpUtil.configureHttpMethod(attachRouteMethod, target.getCloud());
			if (!confStatus.isOK())
				return confStatus;
			
			return HttpUtil.executeMethod(attachRouteMethod);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
