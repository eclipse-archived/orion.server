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
import org.eclipse.orion.server.logs.objects.RollingFileAppenderResource;

public class LogHandler extends ServletResourceHandler<String> {
	private ServletResourceHandler<String> logApiHandler;
	private ServletResourceHandler<String> fileAppenderHandler;
	private ServletResourceHandler<String> rollingFileAppenderHandler;

	public LogHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.logApiHandler = new LogApiHandler(statusHandler);
		this.fileAppenderHandler = new FileAppenderHandler(statusHandler);
		this.rollingFileAppenderHandler = new RollingFileAppenderHandler(statusHandler);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response,
			String pathInfo) throws ServletException {

		/*
		 * Dispatch the request.
		 */
		if (pathInfo == null || "/".equals(pathInfo))
			return logApiHandler.handleRequest(request, response, pathInfo);

		IPath path = new Path(pathInfo);
		if (FileAppenderResource.RESOURCE.equals(path.segment(0)))
			return fileAppenderHandler.handleRequest(request, response, path.removeFirstSegments(1)
					.toString());

		if (RollingFileAppenderResource.RESOURCE.equals(path.segment(0)))
			return rollingFileAppenderHandler.handleRequest(request, response, path
					.removeFirstSegments(1).toString());

		/* unsupported request */
		return false;
	}
}
