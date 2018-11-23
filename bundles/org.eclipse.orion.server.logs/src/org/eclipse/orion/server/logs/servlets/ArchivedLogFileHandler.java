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
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.LogUtils;
import org.eclipse.osgi.util.NLS;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;

public class ArchivedLogFileHandler extends AbstractLogHandler {

	public ArchivedLogFileHandler(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, ILogService logService, IPath path)
			throws ServletException {

		String appenderName = path.segment(0);
		String logFileName = path.segment(1);

		RollingFileAppender<ILoggingEvent> appender = logService
				.getRollingFileAppender(appenderName);
		if (appender == null) {
			String msg = NLS.bind("Appender not found: {0}", appenderName);
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_NOT_FOUND, msg, null);
			return statusHandler.handleRequest(request, response, error);
		}

		File logFile = logService.getArchivedLogFile(appender, logFileName);
		if (logFile == null) {
			String msg = NLS.bind("Log file not found: {0}", logFileName);
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_NOT_FOUND, msg, null);
			return statusHandler.handleRequest(request, response, error);
		}

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
}
