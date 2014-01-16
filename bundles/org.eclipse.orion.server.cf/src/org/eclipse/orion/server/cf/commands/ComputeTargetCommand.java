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

import java.net.URL;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComputeTargetCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private String userId;
	private JSONObject targetJSON;
	private Target target;

	public ComputeTargetCommand(String userId, JSONObject targetJSON) {
		this.commandName = "Compute Target"; //$NON-NLS-1$
		this.userId = userId;
		this.targetJSON = targetJSON;
	}

	public IStatus doIt() {
		IStatus status = super.doIt();
		if (!status.isOK())
			return status;

		URL targetUrl = null;

		if (targetJSON != null) {
			try {
				targetUrl = new URL(targetJSON.getString(CFProtocolConstants.KEY_URL));
			} catch (Exception e) {
				return null;
			}
		}

		this.target = CFActivator.getDefault().getTargetRegistry().getTarget(userId, targetUrl);
		if (target == null) {
			return new Status(IStatus.ERROR, CFActivator.PI_CF, "Target not set", null);
		}

		IStatus result = new SetOrgCommand(this.target, targetJSON != null ? targetJSON.optString("Org") : null).doIt();
		if (!result.isOK())
			return result;

		result = new SetSpaceCommand(this.target, targetJSON != null ? targetJSON.optString("Space") : null).doIt();
		if (!result.isOK())
			return result;

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	public Target getTarget() {
		return this.target;
	}
}
