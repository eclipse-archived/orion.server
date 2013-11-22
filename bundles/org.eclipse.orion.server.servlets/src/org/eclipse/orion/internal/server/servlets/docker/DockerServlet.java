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
package org.eclipse.orion.internal.server.servlets.docker;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servlet to handle requests to the docker server.
 *  
 * @author Anthony Hunter
 * @author Bogdan Gheorghe
 */
public class DockerServlet extends OrionServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		JSONObject jsonResult = new JSONObject();
		try {
			jsonResult.put("containerId", "4a88a5dd2f48");
			jsonResult.put("containerAddress", "127.0.0.1");
			OrionServlet.writeJSONResponse(req, resp, jsonResult);
		} catch (JSONException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}
