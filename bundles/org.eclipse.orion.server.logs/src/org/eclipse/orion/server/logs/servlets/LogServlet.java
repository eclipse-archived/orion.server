/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.servlets.OrionServlet;

public class LogServlet extends OrionServlet {
	public static final String LOGAPI_URI = "/logapi"; //$NON-NLS-1$

	private static final long serialVersionUID = 1L;
	private List<String> authorizedUsers;
	private ServletResourceHandler<String> logHandler;

	@Override
	public void init() throws ServletException {
		logHandler = new LogHandler(getStatusHandler());
		String users = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_LOG_SERVICE, null);
		if (users != null) {
			authorizedUsers = new ArrayList<String>();
			authorizedUsers.addAll(Arrays.asList(users.split(","))); //$NON-NLS-1$
		} else {
			// TODO: remove workaround when orion.auth.log.service property is published and AuthorizedUserFilter capability updated
			users = "ahunterhunter,anthonyh,mbendkowski,sbrandys,johna";
			authorizedUsers = new ArrayList<String>();
			authorizedUsers.addAll(Arrays.asList(users.split(","))); //$NON-NLS-1$
			
		}
	}

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

		if (authorizedUsers == null) {
			// no users are authorized to use the log service
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		
		String login = request.getRemoteUser();
		if (login == null || ! authorizedUsers.contains(login)) {
			// the user is not authorized to use the log service
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		
		String pathInfo = request.getPathInfo();
		if (logHandler.handleRequest(request, response, pathInfo))
			return;

		/*
		 * finally invoke super to return an error for requests we don't know
		 * how to handle
		 */
		super.doGet(request, response);
	}

	@Override
	protected void doPut(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	@Override
	protected void doDelete(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
}
