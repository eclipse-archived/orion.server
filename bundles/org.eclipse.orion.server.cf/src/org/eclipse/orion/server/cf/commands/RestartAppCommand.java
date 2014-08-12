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
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestartAppCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App application;

	public RestartAppCommand(Target target, App app) {
		super(target);

		String[] bindings = {app.getName(), app.getGuid()};
		this.commandName = NLS.bind("Restart application {0} (guid: {1})", bindings);

		this.application = app;
	}

	@Override
	protected ServerStatus _doIt() {
		/* multi server status */
		MultiServerStatus status = new MultiServerStatus();

		try {

			/* stop the application */
			StopAppCommand stopApp = new StopAppCommand(target, application);
			ServerStatus jobStatus = (ServerStatus) stopApp.doIt();
			status.add(jobStatus);
			if (!jobStatus.isOK())
				return status;

			/* start again */
			StartAppCommand startApp = new StartAppCommand(target, application);
			jobStatus = (ServerStatus) startApp.doIt();
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
}
