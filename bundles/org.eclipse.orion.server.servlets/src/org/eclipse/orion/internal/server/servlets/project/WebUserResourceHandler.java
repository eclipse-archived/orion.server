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

import org.eclipse.orion.internal.server.servlets.ProtocolConstants;

import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.URIUtil;
import org.json.*;

/**
 * 
 */
public class WebUserResourceHandler extends WebElementResourceHandler<WebUser> {

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, WebUser object) throws ServletException {
		return false;
	}

	/**
	 * Converts a user to a JSON representation as described by the Eclipse Web
	 * project API protocol.
	 * @param user The user to represent
	 * @param baseLocation The location of the project servlet
	 * @return A JSON representation of the user.
	 */
	public static JSONObject toJSON(WebUser user, URI baseLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(user);
		try {
			result.put(ProtocolConstants.KEY_USER_NAME, user.getName());
			JSONArray projectsJSON = user.getProjectsJSON();
			for (int i = 0; i < projectsJSON.length(); i++) {
				JSONObject project = projectsJSON.getJSONObject(i);
				String projectId = project.getString(ProtocolConstants.KEY_ID);
				project.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(baseLocation, projectId));
				project.put(ProtocolConstants.KEY_NAME, WebProject.fromId(projectId).getName());
			}
			result.put(ProtocolConstants.KEY_PROJECTS, projectsJSON);
		} catch (JSONException e) {
			//should always be valid because we wrote it
			throw new RuntimeException(e);
		}
		return result;
	}
}
