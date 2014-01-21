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
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogoutCommand implements ICFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private Target target;

	private JSONObject accessToken;

	public LogoutCommand(Target target) {
		this.commandName = "Logout"; //$NON-NLS-1$
		this.target = target;
	}

	public IStatus doIt() {
		target.getCloud().setAccessToken(null);
		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	public JSONObject getOAuthAccessToken() {
		return accessToken;
	}
}
