/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;

/**
 * Helper class for handling serialization of exception responses in servlets.
 */
public class ServletStatusHandler extends ServletResourceHandler<IStatus> {

	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IStatus error) throws ServletException {
		int httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		ServerStatus serverStatus;
		if (error instanceof ServerStatus) {
			serverStatus = (ServerStatus) error;
			httpCode = serverStatus.getHttpCode();
		} else {
			serverStatus = new ServerStatus(error, httpCode);
		}
		response.setCharacterEncoding("UTF-8");
		//TODO change check for a generic property
		if ("TIAM".equals(PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_NAME, null))) {
			if (httpCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
				httpCode = 599;
			}
			if (httpCode == HttpServletResponse.SC_NOT_FOUND) {
				httpCode = HttpServletResponse.SC_GONE;
			}
		}
		response.setStatus(httpCode);
		response.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
		try {
			response.getWriter().print(serverStatus.toJSON().toString());
		} catch (IOException ioe) {
			//just throw a servlet exception
			throw new ServletException(error.getMessage(), error.getException());
		}
		return true;
	}

}
