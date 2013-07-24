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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.LogConstants;
import org.eclipse.orion.server.logs.jobs.FileAppenderJob;
import org.eclipse.orion.server.logs.objects.FileAppenderResource;
import org.eclipse.osgi.util.NLS;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

public class FileAppenderHandler extends AbstractLogHandler<String> {
	/* 64KB default buffer size */
	private static final int BUFFERSIZE = Integer.parseInt(PreferenceHelper.getString(
			LogConstants.CONFIG_FILE_LOG_BUFFER_SIZE, String.valueOf(64 * 1024)));

	public FileAppenderHandler(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	/*
	 * Handles the download request for a single file appender log file.
	 */
	private boolean downloadLog(HttpServletRequest request, HttpServletResponse response,
			ILogService logService, FileAppenderResource appenderObj) throws ServletException {

		FileAppender<ILoggingEvent> appender = logService.getFileAppender(appenderObj.getName());
		if (appender == null) {
			String msg = NLS.bind("Appender not found: {0}", appenderObj.getName());
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_NOT_FOUND, msg, null);
			return statusHandler.handleRequest(request, response, error);
		}

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
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);

			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		} finally {
			try {
				if (in != null)
					in.close();

				if (output != null)
					output.close();
			} catch (Exception e) {
				String msg = NLS.bind("An error occured when looking for log {0}.",
						logFile.getName());
				final ServerStatus error = new ServerStatus(IStatus.ERROR,
						HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);

				LogHelper.log(error);
				return statusHandler.handleRequest(request, response, new ServerStatus(
						IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			}
		}

		return true;
	}

	@Override
	protected boolean handleGet(HttpServletRequest request, HttpServletResponse response,
			ILogService logService, String pathInfo) throws ServletException {

		IPath path = new Path(pathInfo);
		String appenderName = path.segment(0);

		FileAppenderResource appender = new FileAppenderResource();
		appender.setBaseLocation(getURI(request));
		appender.setName(appenderName);

		IPath arguments = path.removeFirstSegments(1);
		if (arguments.segmentCount() == 1
				&& LogConstants.KEY_APPENDER_DOWNLOAD.equals(arguments.segment(0)))
			return downloadLog(request, response, logService, appender);

		try {
			return TaskJobHandler.handleTaskJob(request, response, new FileAppenderJob(
					TaskJobHandler.getUserId(request), logService, getURI(request), appenderName),
					statusHandler);
		} catch (Exception e) {
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for appenders.", e);

			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}
	}
}
