/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.server.logs.jobs;

import java.net.URI;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskJob;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.objects.LoggerResource;
import org.json.JSONArray;
import org.json.JSONObject;

import ch.qos.logback.classic.Logger;

public class ListLoggersJob extends TaskJob {
	private final ILogService logService;
	private final URI baseLocation;

	public ListLoggersJob(String userRunningTask, ILogService logService,
			URI baseLocation) {

		super(userRunningTask, false);
		this.logService = logService;
		this.baseLocation = baseLocation;
	}

	@Override
	protected IStatus performJob() {
		try {
			List<Logger> loggers = logService.getLoggers();

			JSONObject loggersJSON = new JSONObject();
			loggersJSON.put(ProtocolConstants.KEY_CHILDREN, new JSONArray());
			for (Logger logger : loggers) {
				LoggerResource loggerResource = new LoggerResource();

				loggerResource.setBaseLocation(baseLocation);
				loggerResource.setName(logger.getName());
				loggerResource.setLevel(logger.getLevel());
				loggerResource.setEffectiveLevel(logger.getEffectiveLevel());

				loggersJSON.append(ProtocolConstants.KEY_CHILDREN,
						loggerResource.toJSON());
			}

			return new ServerStatus(Status.OK_STATUS,
					HttpServletResponse.SC_OK, loggersJSON);

		} catch (Exception e) {
			return new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when listing loggers", e);
		}
	}
}
