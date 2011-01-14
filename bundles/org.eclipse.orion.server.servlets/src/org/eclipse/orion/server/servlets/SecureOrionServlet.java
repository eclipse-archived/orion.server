/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;

/**
 * Common base class for servlets that defines convenience API for (de)serialization
 * of requests and responses.
 */
public abstract class SecureOrionServlet extends OrionServlet {

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		try {
			if (!AuthorizationService.checkRights(req.getRemoteUser(), req.getRequestURI().toString())) {
				resp.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		} catch (JSONException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		super.service(req, resp);
	}
}
