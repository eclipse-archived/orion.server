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
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.objects.*;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnmapRouteCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App app;
	private Route route;

	public UnmapRouteCommand(Target target, App app, Route route) {
		super(target);

		String[] bindings = {route.getGuid(), app.getName(), app.getGuid()};
		this.commandName = NLS.bind("Unmap route (guid: {0}) from application {1} (guid: {2})", bindings);

		this.app = app;
		this.route = route;
	}

	@Override
	protected ServerStatus _doIt() {
		try {

			/* unmap route from application */
			URI targetURI = URIUtil.toURI(target.getUrl());
			DeleteMethod unmapRouteMethod = new DeleteMethod(targetURI.resolve("/v2/apps/" + app.getGuid() + "/routes/" + route.getGuid()).toString()); //$NON-NLS-1$//$NON-NLS-2$
			HttpUtil.configureHttpMethod(unmapRouteMethod, target);
			return HttpUtil.executeMethod(unmapRouteMethod);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
