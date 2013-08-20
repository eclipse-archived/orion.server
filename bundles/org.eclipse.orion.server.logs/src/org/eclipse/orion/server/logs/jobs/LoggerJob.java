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

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskJob;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.objects.LoggerResource;
import org.eclipse.osgi.util.NLS;

import ch.qos.logback.classic.Logger;

public class LoggerJob extends TaskJob {
	private final ILogService logService;
	private final URI baseLocation;
	private final String loggerName;

	public LoggerJob(String userRunningTask, ILogService logService,
			URI baseLocation, String loggerName) {

		super(userRunningTask, false);
		this.logService = logService;
		this.baseLocation = baseLocation;
		this.loggerName = loggerName;
	}

	@Override
	protected IStatus performJob() {
		try {
			Logger logger = logService.getLogger(loggerName);

			if (logger == null) {
				String msg = NLS.bind("Logger not found: {0}", loggerName);
				return new ServerStatus(IStatus.ERROR,
						HttpServletResponse.SC_NOT_FOUND, msg, null);
			}

			LoggerResource loggerResource = new LoggerResource();

			loggerResource.setBaseLocation(baseLocation);
			loggerResource.setName(logger.getName());
			loggerResource.setLevel(logger.getLevel());
			loggerResource.setEffectiveLevel(logger.getEffectiveLevel());

			return new ServerStatus(Status.OK_STATUS,
					HttpServletResponse.SC_OK, loggerResource.toJSON());
		} catch (Exception e) {
			String msg = NLS
					.bind("An error occured when looking for logger: {0}",
							loggerName);
			return new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
