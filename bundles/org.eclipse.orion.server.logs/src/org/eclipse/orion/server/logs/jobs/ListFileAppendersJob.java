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
import org.eclipse.orion.server.logs.objects.FileAppenderResource;
import org.eclipse.orion.server.logs.objects.RollingFileAppenderResource;
import org.json.JSONArray;
import org.json.JSONObject;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;

public class ListFileAppendersJob extends TaskJob {
	private final ILogService logService;
	private final URI baseLocation;

	public ListFileAppendersJob(String userRunningTask, ILogService logService,
			URI baseLocation) {

		super(userRunningTask, false);
		this.logService = logService;
		this.baseLocation = baseLocation;
	}

	@Override
	protected IStatus performJob() {
		try {
			List<FileAppender<ILoggingEvent>> appenders = logService
					.getFileAppenders();

			JSONObject appendersJSON = new JSONObject();
			appendersJSON.put(ProtocolConstants.KEY_CHILDREN, new JSONArray());
			for (FileAppender<ILoggingEvent> appender : appenders) {

				FileAppenderResource fileAppender = null;
				if (appender instanceof RollingFileAppender<?>)
					fileAppender = new RollingFileAppenderResource(appender,
							baseLocation);
				else
					fileAppender = new FileAppenderResource(appender,
							baseLocation);

				appendersJSON.append(ProtocolConstants.KEY_CHILDREN,
						fileAppender.toJSON());
			}

			return new ServerStatus(Status.OK_STATUS,
					HttpServletResponse.SC_OK, appendersJSON);

		} catch (Exception e) {
			return new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when listing file appenders", e);
		}
	}
}
