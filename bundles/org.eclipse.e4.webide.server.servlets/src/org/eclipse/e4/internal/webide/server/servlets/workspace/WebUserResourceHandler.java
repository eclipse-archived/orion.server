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
package org.eclipse.e4.internal.webide.server.servlets.workspace;

import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.e4.internal.webide.server.servlets.ProtocolConstants;
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
	 * workspace API protocol.
	 * @param user The user to represent
	 * @param baseLocation The location of the workspace servlet
	 * @return A JSON representation of the user.
	 */
	public static JSONObject toJSON(WebUser user, URI baseLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(user);
		try {
			result.put(ProtocolConstants.KEY_USER_NAME, user.getName());
			JSONArray workspacesJSON = user.getWorkspacesJSON();
			for (int i = 0; i < workspacesJSON.length(); i++) {
				JSONObject workspace = workspacesJSON.getJSONObject(i);
				String workspaceId = workspace.getString(ProtocolConstants.KEY_ID);
				workspace.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(baseLocation, workspaceId));
				workspace.put(ProtocolConstants.KEY_NAME, WebWorkspace.fromId(workspaceId).getName());
			}
			result.put(ProtocolConstants.KEY_WORKSPACES, workspacesJSON);
		} catch (JSONException e) {
			//should always be valid because we wrote it
			throw new RuntimeException(e);
		}
		return result;
	}
}
