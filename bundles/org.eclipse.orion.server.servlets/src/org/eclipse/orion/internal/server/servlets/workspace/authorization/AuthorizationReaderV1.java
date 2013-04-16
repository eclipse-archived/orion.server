/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace.authorization;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.json.*;

/**
 * Reads version 1 of the authorization data format.
 */
public class AuthorizationReaderV1 extends AuthorizationReader {
	@Override
	JSONArray readAuthorizationInfo(String userId, IEclipsePreferences preferences) throws JSONException {
		//recompute permissions based on workspace ownership 
		//because file URL structure changed in V3
		JSONArray newPermissions = new JSONArray();
		addPermission(newPermissions, "/users/" + userId); //$NON-NLS-1$
		try {
			UserInfo user = Activator.getDefault().getMetastore().readUser(userId);
			//do for each workspace owned by current user
			for (String workspaceId : user.getWorkspaceIds()) {
				//user has access to their own workspace
				addPermission(newPermissions, Activator.LOCATION_WORKSPACE_SERVLET + '/' + workspaceId);
				addPermission(newPermissions, Activator.LOCATION_WORKSPACE_SERVLET + '/' + workspaceId + "/*"); //$NON-NLS-1$
				//access to project contents
				addPermission(newPermissions, Activator.LOCATION_FILE_SERVLET + '/' + workspaceId);
				addPermission(newPermissions, Activator.LOCATION_FILE_SERVLET + '/' + workspaceId + "/*"); //$NON-NLS-1$
			}
		} catch (CoreException e) {
			//log and continue with no permissions
			LogHelper.log(e);
		}
		return newPermissions;
	}

	private void addPermission(JSONArray permissions, String uri) throws JSONException {
		JSONObject newPermission = AuthorizationService.createUserRight(uri);
		permissions.put(newPermission);
	}
}
