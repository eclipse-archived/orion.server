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

import java.io.File;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.LogUtils;
import org.eclipse.orion.server.logs.jobs.FileAppenderJob;
import org.eclipse.orion.server.logs.jobs.ListFileAppendersJob;
import org.eclipse.osgi.util.NLS;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

public class FileAppenderHandler extends AbstractLogHandler {
	public FileAppenderHandler(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	/*
	 * Handles the download request for a single file appender log file.
	 */
	protected boolean downloadLog(HttpServletRequest request,
			HttpServletResponse response, ILogService logService,
			String appenderName) throws ServletException {

		FileAppender<ILoggingEvent> appender = logService
				.getFileAppender(appenderName);
		if (appender == null) {
			String msg = NLS.bind("Appender not found: {0}", appenderName);
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_NOT_FOUND, msg, null);
			return statusHandler.handleRequest(request, response, error);
		}

		File logFile = new File(appender.getFile());
		try {
			LogUtils.provideLogFile(logFile, response);
		} catch (Exception ex) {
			String msg = NLS.bind("An error occured when looking for log {0}.",
					logFile.getName());
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex);

			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}

		return true;
	}

	@Override
	protected boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, ILogService logService)
			throws ServletException {

		try {
			return TaskJobHandler.handleTaskJob(request, response,
					new ListFileAppendersJob(TaskJobHandler.getUserId(request),
							logService, getURI(request)), statusHandler);
		} catch (Exception e) {
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for file appenders.", e);

			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}
	}

	@Override
	protected boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, ILogService logService, IPath path)
			throws ServletException {

		String appenderName = path.segment(0);
		String parts = request.getParameter("parts"); //$NON-NLS-1$
		boolean metadata = parts != null && "meta".equals(parts); //$NON-NLS-1$

		if (!metadata)
			return downloadLog(request, response, logService, appenderName);

		try {
			return TaskJobHandler.handleTaskJob(request, response,
					new FileAppenderJob(TaskJobHandler.getUserId(request),
							logService, getURI(request), appenderName),
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
