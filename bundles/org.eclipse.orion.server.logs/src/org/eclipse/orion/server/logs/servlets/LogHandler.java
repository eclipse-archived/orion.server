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

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.logs.objects.FileAppenderResource;
import org.eclipse.orion.server.logs.objects.LoggerResource;
import org.eclipse.orion.server.logs.objects.RollingFileAppenderResource;

public class LogHandler extends ServletResourceHandler<String> {
	private final ServletResourceHandler<IPath> fileAppenderHandler;
	private final ServletResourceHandler<IPath> rollingFileAppenderHandler;
	private final ServletResourceHandler<IPath> loggerHandler;

	public LogHandler(ServletResourceHandler<IStatus> statusHandler) {
		fileAppenderHandler = new FileAppenderHandler(statusHandler);
		loggerHandler = new LoggerHandler(statusHandler);
		rollingFileAppenderHandler = new RollingFileAppenderHandler(
				statusHandler);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, String pathInfo)
			throws ServletException {

		if (pathInfo == null)
			return false;

		/*
		 * Dispatch the request.
		 */
		IPath path = new Path(pathInfo);
		if (FileAppenderResource.RESOURCE.equals(path.segment(0)))
			return fileAppenderHandler.handleRequest(request, response,
					path.removeFirstSegments(1));

		if (RollingFileAppenderResource.RESOURCE.equals(path.segment(0)))
			return rollingFileAppenderHandler.handleRequest(request, response,
					path.removeFirstSegments(1));

		if (LoggerResource.RESOURCE.equals(path.segment(0)))
			return loggerHandler.handleRequest(request, response,
					path.removeFirstSegments(1));

		/* unsupported request */
		return false;
	}
}
