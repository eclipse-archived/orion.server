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
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.LogsActivator;

public abstract class AbstractLogHandler extends ServletResourceHandler<IPath> {
	protected final ServletResourceHandler<IStatus> statusHandler;

	public AbstractLogHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, IPath path) throws ServletException {

		ILogService logService = LogsActivator.getDefault().getLogService();
		if (logService == null)
			/* unsupported functionality */
			return false;

		switch (getMethod(request)) {
		case GET:
			if (path.isEmpty())
				return handleGet(request, response, logService);

			return handleGet(request, response, logService, path);
		case POST:
			return handlePost(request, response, logService, path);
		case PUT:
			return handlePut(request, response, logService, path);
		case DELETE:
			return handleDelete(request, response, logService, path);
		default:
			return false;
		}
	}

	protected boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, ILogService logService, IPath path)
			throws ServletException {
		return false;
	}

	protected boolean handleGet(HttpServletRequest request,
			HttpServletResponse response, ILogService logService)
			throws ServletException {
		return false;
	}

	protected boolean handlePost(HttpServletRequest request,
			HttpServletResponse response, ILogService logService, IPath path)
			throws ServletException {
		return false;
	}

	protected boolean handlePut(HttpServletRequest request,
			HttpServletResponse response, ILogService logService, IPath path)
			throws ServletException {
		return false;
	}

	protected boolean handleDelete(HttpServletRequest request,
			HttpServletResponse response, ILogService logService, IPath path)
			throws ServletException {
		return false;
	}
}
