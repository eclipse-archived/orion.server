/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A handler for Git Status operation.
 */
public class GitStatusHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

	GitStatusHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, String gitPathInfo)
			throws ServletException {
		try {
			JSONObject result = new JSONObject();
			createStatusRepresentation(request, result, null);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response,
					new ServerStatus(IStatus.ERROR,
							HttpServletResponse.SC_BAD_REQUEST,
							"Syntax error in request", e));
		} catch (Exception e) {
			throw new ServletException("Error creating Git status", e);
		}
	}

	private void createStatusRepresentation(HttpServletRequest request,
			JSONObject representation, String path) throws JSONException {
		representation.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(
				OrionServlet.getURI(request), path == null ? "" : path));

		JSONObject file = new JSONObject();
		createFakeFileRepresentation(request, file);
		representation.put("File", file);

		if (path == null) {
			JSONArray children = new JSONArray();
			generateFakeChildren(request, children);
			representation.put(ProtocolConstants.KEY_CHILDREN, children);
		}
	}

	private void createFakeFileRepresentation(HttpServletRequest request,
			JSONObject representation) throws JSONException {
		representation.put(ProtocolConstants.KEY_NAME, "SomeName");
		representation.put(ProtocolConstants.KEY_LOCATION, "SomeLocation");
	}

	private void generateFakeChildren(HttpServletRequest request,
			JSONArray representation) throws JSONException {
		for (int i = 0; i < 3; i++) {
			JSONObject child = new JSONObject();
			createStatusRepresentation(request, child, "path" + i);
			representation.put(child);
		}
	}
}
