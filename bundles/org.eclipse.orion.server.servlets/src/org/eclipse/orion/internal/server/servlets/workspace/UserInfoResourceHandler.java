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
package org.eclipse.orion.internal.server.servlets.workspace;

import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles requests against a single user resource.
 */
public class UserInfoResourceHandler extends MetadataInfoResourceHandler<UserInfo> {

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, UserInfo user) throws ServletException {
		return false;
	}

	/**
	 * Converts a user to a JSON representation as described by the Eclipse Web
	 * workspace API protocol.
	 * @param user The user to represent
	 * @param baseLocation The location of the workspace servlet
	 * @return A JSON representation of the user.
	 */
	public static JSONObject toJSON(UserInfo user, URI baseLocation) {
		JSONObject result = MetadataInfoResourceHandler.toJSON(user);
		try {
			result.put(ProtocolConstants.KEY_USER_NAME, user.getUserName());
			JSONArray workspacesJSON = new JSONArray();
			for (String workspaceId : user.getWorkspaceIds()) {
				WorkspaceInfo workspaceInfo = OrionConfiguration.getMetaStore().readWorkspace(workspaceId);
				JSONObject workspace = new JSONObject();
				workspace.put(ProtocolConstants.KEY_NAME, workspaceInfo.getFullName());
				workspace.put(ProtocolConstants.KEY_ID, workspaceId);
				workspace.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(baseLocation, workspaceId));
				workspacesJSON.put(workspace);
			}
			result.put(ProtocolConstants.KEY_WORKSPACES, workspacesJSON);
		} catch (CoreException e) {
		} catch (JSONException e) {
			//should always be valid because we wrote it
			throw new RuntimeException(e);
		}
		return result;
	}
}
