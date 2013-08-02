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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.jobs.ListFileAppendersJob;

public class LogApiHandler extends AbstractLogHandler<String> {

	public LogApiHandler(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(HttpServletRequest request, HttpServletResponse response,
			ILogService logService, String pathInfo) throws ServletException {

		try {
			return TaskJobHandler.handleTaskJob(request, response, new ListFileAppendersJob(
					TaskJobHandler.getUserId(request), logService, getURI(request)), statusHandler);
		} catch (Exception e) {
			final ServerStatus error = new ServerStatus(IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when looking for appenders.", e);

			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}
	}
}
