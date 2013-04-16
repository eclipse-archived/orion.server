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
package org.eclipse.orion.internal.server.servlets.workspace;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.*;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * A meta store implementation that is backed by the legacy Orion 1.0 back end
 * storage (Equinox preferences).
 */
public class CompatibilityMetaStore implements IMetaStore {

	public void createUser(UserInfo info) throws CoreException {
		String id = info.getUID();
		if (id == null)
			throw new IllegalArgumentException("User id not provided");
		WebUser webUser = WebUser.fromUserId(id);
		webUser.setUserName(info.getUserName());
		webUser.setName(info.getFullName());
		webUser.setGuest(info.isGuest());
		webUser.save();
	}

	public void createWorkspace(WorkspaceInfo info) throws CoreException {
	}

	public void createProject(ProjectInfo info) throws CoreException {
	}

	public UserInfo readUser(String uid) throws CoreException {
		//convert a legacy WebUser object into a UserInfo
		WebUser webUser = WebUser.fromUserId(uid);
		UserInfo info = new UserInfo();
		info.setUID(webUser.getId());
		info.setFullName(webUser.getName());
		info.setUserName(webUser.getUserName());
		info.setGuest(webUser.isGuest());
		JSONArray workspaces = webUser.getWorkspacesJSON();
		List<String> workspaceIds = new ArrayList<String>(workspaces.length());
		for (int i = 0; i < workspaces.length(); i++) {
			try {
				workspaceIds.add(workspaces.getJSONObject(i).getString(ProtocolConstants.KEY_ID));
			} catch (JSONException e) {
				//ignore malformed metadata
			}
		}
		info.setWorkspaceIds(workspaceIds);
		//todo site configurations

		return info;
	}

	public void updateUser(UserInfo info) throws CoreException {
	}

	public void deleteUser(String uid) throws CoreException {
		WebUser.fromUserId(uid).delete();
	}

}
