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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.LogsActivator;
import org.eclipse.orion.server.servlets.OrionServlet;

public class LogServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;
	private ServletResourceHandler<String> logHandler;

	public LogServlet() {
		logHandler = new ServletLogHandler(getStatusHandler());
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		ILogService logService = LogsActivator.getDefault().getLogService();
		if (logService == null) {
			/* not supported functionality */
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		/* supported functionality */
		String pathInfo = request.getPathInfo();
		if (logHandler.handleRequest(request, response, pathInfo))
			return;

		// finally invoke super to return an error for
		// requests we don't know how to handle
		super.doGet(request, response);
	}

	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}

	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}
}
