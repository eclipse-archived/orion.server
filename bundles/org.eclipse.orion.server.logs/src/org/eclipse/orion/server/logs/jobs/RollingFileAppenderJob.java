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
import org.eclipse.orion.server.logs.LogUtils;
import org.eclipse.orion.server.logs.objects.RollingFileAppenderResource;
import org.eclipse.osgi.util.NLS;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;

public class RollingFileAppenderJob extends TaskJob {
	private final ILogService logService;
	private final URI baseLocation;
	private final String appenderName;

	public RollingFileAppenderJob(String userRunningTask,
			ILogService logService, URI baseLocation, String appenderName) {

		super(userRunningTask, false);
		this.logService = logService;
		this.baseLocation = baseLocation;
		this.appenderName = appenderName;
	}

	@Override
	protected IStatus performJob() {
		try {
			RollingFileAppender<ILoggingEvent> appender = logService
					.getRollingFileAppender(appenderName);

			if (appender == null) {
				String msg = NLS.bind("Rolling appender not found: {0}",
						appenderName);
				return new ServerStatus(IStatus.ERROR,
						HttpServletResponse.SC_NOT_FOUND, msg, null);
			}

			RollingFileAppenderResource rollingFileAppender = new RollingFileAppenderResource(
					appender, baseLocation);

			if (rollingFileAppender.getArchivedLogFiles() == null)
				LogUtils.attachArchivedLogFiles(appender, rollingFileAppender,
						logService);

			return new ServerStatus(Status.OK_STATUS,
					HttpServletResponse.SC_OK, rollingFileAppender.toJSON());

		} catch (Exception e) {
			String msg = NLS.bind(
					"An error occured when looking for rolling appender: {0}",
					appenderName);
			return new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}