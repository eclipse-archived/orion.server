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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskJob;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;

public class AppenderJob extends TaskJob {
	private String appenderName;
	private ILogService logService;

	public AppenderJob(String userRunningTask, ILogService logService, String appenderName) {
		super(userRunningTask, false);
		this.logService = logService;
		this.appenderName = appenderName;
	}

	public AppenderJob(String userRunningTask, ILogService logService) {
		super(userRunningTask, false);
		this.logService = logService;
		this.appenderName = null;
	}

	/*
	 * Handles a single appender GET request.
	 */
	private IStatus singleAppender() {
		try {
			FileAppender<ILoggingEvent> appender = logService.getFileAppender(appenderName);
			if (appender == null) {
				String msg = NLS.bind("Appender not found: {0}", appenderName);
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
			}

			JSONObject logJSON = new JSONObject();
			if (appender instanceof RollingFileAppender) {
				RollingFileAppender<ILoggingEvent> rollingAppender = (RollingFileAppender<ILoggingEvent>) appender;
				logJSON.put("type", rollingAppender.getClass().getName());
			} else {
				logJSON.put("type", appender.getClass().getName());
			}

			logJSON.put("file", appender.getFile());
			logJSON.put("isAppend", appender.isAppend());
			logJSON.put("isPrudent", appender.isPrudent());
			logJSON.put("isStarted", appender.isStarted());
			logJSON.put("content", new JSONArray());

			File appenderFile = new File(appender.getFile());
			BufferedReader br = new BufferedReader(new FileReader(appenderFile));

			try {
				String line = br.readLine();
				while (line != null) {
					logJSON.append("content", line);
					line = br.readLine();
				}
			} finally {
				br.close();
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, logJSON);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when looking for an appender: {0}",
					appenderName);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					msg, e);
		}
	}

	/*
	 * Handles an appenders GET request.
	 */
	private IStatus allAppenders() {
		try {
			List<FileAppender<ILoggingEvent>> appenders = logService.getFileAppenders();

			JSONObject appendersJSON = new JSONObject();
			appendersJSON.put("appenders", new JSONArray());

			for (FileAppender<ILoggingEvent> appender : appenders) {
				JSONObject appenderJSON = new JSONObject();

				if (appender instanceof RollingFileAppender) {
					RollingFileAppender<ILoggingEvent> rollingAppender = (RollingFileAppender<ILoggingEvent>) appender;
					appenderJSON.put("type", rollingAppender.getClass().getName());
				} else {
					appenderJSON.put("type", appender.getClass().getName());
				}

				appenderJSON.put("name", appender.getName());
				appenderJSON.put("isAppend", appender.isAppend());
				appenderJSON.put("isPrudent", appender.isPrudent());
				appenderJSON.put("isStarted", appender.isStarted());
				appendersJSON.append("appenders", appenderJSON);
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, appendersJSON);

		} catch (Exception e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for appenders", e);
		}
	}

	@Override
	protected IStatus performJob() {
		if (appenderName != null)
			return singleAppender();

		return allAppenders();
	}
}
