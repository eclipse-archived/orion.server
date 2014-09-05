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

import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.LogConstants;
import org.eclipse.orion.server.logs.jobs.ListLoggersJob;
import org.eclipse.orion.server.logs.jobs.LoggerJob;
import org.eclipse.orion.server.logs.objects.LoggerResource;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class LoggerHandler extends AbstractLogHandler {
	public LoggerHandler(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, ILogService logService)
			throws ServletException {

		try {
			return TaskJobHandler.handleTaskJob(request, response,
					new ListLoggersJob(TaskJobHandler.getUserId(request),
							logService, getURI(request)), statusHandler);
		} catch (Exception e) {
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for loggers.", e);

			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}
	}

	@Override
	protected boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, ILogService logService, IPath path)
			throws ServletException {

		String loggerName = path.segment(0);

		try {
			return TaskJobHandler.handleTaskJob(request, response,
					new LoggerJob(TaskJobHandler.getUserId(request),
							logService, getURI(request), loggerName),
					statusHandler);
		} catch (Exception e) {
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for logger.", e);

			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}
	}

	@Override
	protected boolean handlePut(HttpServletRequest request,
			HttpServletResponse response, ILogService logService, IPath path)
			throws ServletException {

		URI baseLocation = getURI(request);

		try {
			String loggerName = path.segment(0);
			JSONObject toPut = OrionServlet.readJSONRequest(request);

			Logger logger = logService.getLogger(loggerName);
			if (logger == null) {
				final String msg = NLS
						.bind("Logger not found: {0}", loggerName);
				final ServerStatus error = new ServerStatus(IStatus.ERROR,
						HttpServletResponse.SC_NOT_FOUND, msg, null);
				return statusHandler.handleRequest(request, response, error);
			}

			String putlevel = toPut.getString(LogConstants.KEY_LOGGER_LEVEL);
			Level level = Level.toLevel(putlevel, logger.getLevel());
			logger.setLevel(level);

			LoggerResource loggerResource = new LoggerResource();
			loggerResource.setBaseLocation(baseLocation);
			loggerResource.setName(logger.getName());
			loggerResource.setLevel(logger.getLevel());
			loggerResource.setEffectiveLevel(logger.getEffectiveLevel());

			JSONObject result = loggerResource.toJSON();
			OrionServlet.writeJSONResponse(request, response, result);
			response.setHeader(ProtocolConstants.HEADER_LOCATION,
					result.getString(ProtocolConstants.KEY_LOCATION));
			return true;

		} catch (Exception e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST, e.getMessage(),
							e));
		}
	}
}
