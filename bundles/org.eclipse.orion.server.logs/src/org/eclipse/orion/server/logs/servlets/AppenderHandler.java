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

package org.eclipse.orion.server.logs.servlets;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;

public class AppenderHandler extends AbstractLogHandler {
	/* 4KB default buffer size */
	private static final int BUFFERSIZE = 4096;

	public AppenderHandler(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	/*
	 * Handles the download request for a single file appender log file.
	 */
	private boolean downloadLog(HttpServletRequest request, HttpServletResponse response,
			ILogService logService, FileAppender<ILoggingEvent> appender) throws ServletException {

		File logFile = new File(appender.getFile());
		DataInputStream in = null;
		ServletOutputStream output = null;

		try {
			byte[] byteBuffer = new byte[BUFFERSIZE];
			in = new DataInputStream(new FileInputStream(logFile));
			output = response.getOutputStream();

			response.setContentType("text/plain");
			response.setContentLength((int) logFile.length());
			response.setHeader("Content-Disposition", "attachment; filename=\"" + logFile.getName()
					+ "\"");

			int length = 0;
			while (in != null && (length = in.read(byteBuffer)) != -1) {
				output.write(byteBuffer, 0, length);
			}

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when looking for log {0}.", logFile.getName());
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			try {
				if (in != null)
					in.close();

				if (output != null)
					output.close();
			} catch (Exception e) {
				String msg = NLS.bind("An error occured when looking for log {0}.",
						logFile.getName());
				return statusHandler.handleRequest(request, response, new ServerStatus(
						IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			}
		}

		return true;
	}

	/*
	 * Handles a single appender GET request.
	 */
	private boolean handleSingleGet(HttpServletRequest request, HttpServletResponse response,
			ILogService logService, String appenderName, IPath arguments) throws ServletException {

		try {
			FileAppender<ILoggingEvent> appender = logService.getFileAppender(appenderName);
			if (appender == null) {
				String msg = NLS.bind("Appender not found: {0}", appenderName);
				return statusHandler.handleRequest(request, response, new ServerStatus(
						IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}

			if (arguments.segmentCount() == 1 && "download".equals(arguments.segment(0)))
				return downloadLog(request, response, logService, appender);

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

			return statusHandler.handleRequest(request, response, new ServerStatus(
					Status.OK_STATUS, HttpServletResponse.SC_OK, logJSON));

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for an appender.", e));
		}
	}

	@Override
	protected boolean handleGet(HttpServletRequest request, HttpServletResponse response,
			ILogService logService, String pathInfo) throws ServletException {

		IPath path = new Path(pathInfo);
		if (path.segmentCount() > 0)
			return handleSingleGet(request, response, logService, path.segment(0),
					path.removeFirstSegments(1));

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

			return statusHandler.handleRequest(request, response, new ServerStatus(
					Status.OK_STATUS, HttpServletResponse.SC_OK, appendersJSON));

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for appenders.", e));
		}
	}
}
