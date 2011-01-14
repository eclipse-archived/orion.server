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
package org.eclipse.orion.internal.server.servlets.project;

import org.eclipse.orion.server.core.LogHelper;

import org.eclipse.orion.server.servlets.OrionServlet;

import org.eclipse.orion.internal.server.servlets.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles serialization of {@link WebElement} objects.
 */
public class WebProjectResourceHandler extends WebElementResourceHandler<WebProject> {
	private ServletResourceHandler<IStatus> statusHandler;

	public static JSONObject toJSON(WebProject project, URI parentLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(project);
		try {
			result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(parentLocation, project.getId()).toString());
			URI contentLocation = project.getContentLocation();
			if (!contentLocation.isAbsolute()) {
				//a relative location for project content is resolved relative to the location of the file servlet
				String parentString = parentLocation.toString();
				String serverBase = parentString.substring(0, parentString.length() - Activator.LOCATION_PROJECT_SERVLET.length());
				String fileServletBase = serverBase + Activator.LOCATION_FILE_SERVLET + '/';
				try {
					contentLocation = new URI(fileServletBase).resolve(contentLocation);
				} catch (URISyntaxException e) {
					LogHelper.log(e);
					contentLocation = null;
				}
			}
			if (contentLocation != null) {
				result.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation.toString());
				result.put(ProtocolConstants.KEY_CHILDREN_LOCATION, contentLocation.toString() + "?depth=1"); //$NON-NLS-1$
			}
		} catch (JSONException e) {
			//can't happen because key and value are well-formed
		}
		return result;
	}

	public WebProjectResourceHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	private boolean handleGetProjectMetadata(HttpServletRequest request, HttpServletResponse response, WebProject project) throws IOException {
		//we need the base location for the project servlet. Since this is a GET 
		//on a single project we need to strip off the project id from the request URI
		URI baseLocation = getURI(request);
		baseLocation = baseLocation.resolve("");
		OrionServlet.writeJSONResponse(request, response, toJSON(project, baseLocation));
		return true;
	}

	private boolean handlePutProjectMetadata(HttpServletRequest request, HttpServletResponse response, WebProject project) throws JSONException {
		return false;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, WebProject project) throws ServletException {
		if (project == null)
			return statusHandler.handleRequest(request, response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Project not specified"));
		//we could split and handle different API versions here if needed
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGetProjectMetadata(request, response, project);
				case PUT :
					return handlePutProjectMetadata(request, response, project);
			}
		} catch (IOException e) {
			String msg = NLS.bind("Error handling request against project {0}", project.getId());
			statusHandler.handleRequest(request, response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		}
		return false;
	}

}
