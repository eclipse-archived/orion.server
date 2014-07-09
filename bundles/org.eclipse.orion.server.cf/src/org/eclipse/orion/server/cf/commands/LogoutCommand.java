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

import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogoutCommand implements ICFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private static final String INVALID_TOKEN = "InvalidToken";

	private String commandName;

	private Target target;

	private boolean invalidate;

	public LogoutCommand(Target target, boolean invalidate) {
		this.commandName = "Log out";
		this.target = target;
		this.invalidate = invalidate;
	}

	public IStatus doIt() {
		try {
			if (invalidate) {
				JSONObject invalidToken = new JSONObject();
				invalidToken.put("access_token", INVALID_TOKEN);
				target.getCloud().setAccessToken(invalidToken);
			} else {
				target.getCloud().setAccessToken(null);
			}
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
		} catch (Exception e) {
			String msg = NLS.bind("An error occurred when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
