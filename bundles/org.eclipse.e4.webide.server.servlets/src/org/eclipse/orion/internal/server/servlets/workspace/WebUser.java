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
package org.eclipse.orion.internal.server.servlets.workspace;

import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.core.users.EclipseWebScope;

import org.eclipse.orion.internal.server.servlets.ProtocolConstants;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.json.*;
import org.osgi.service.prefs.BackingStoreException;

/**
 * An Eclipse web user.
 */
public class WebUser extends WebElement {
	public WebUser(IEclipsePreferences store) {
		super(store);
	}

	/**
	 * Creates a web user instance for the given name.
	 */
	public static WebUser fromUserName(String userName) {
		IEclipsePreferences users = new EclipseWebScope().getNode("Users"); //$NON-NLS-1$
		IEclipsePreferences result = (IEclipsePreferences) users.node(userName);
		if (result.get(ProtocolConstants.KEY_NAME, null) == null)
			result.put(ProtocolConstants.KEY_NAME, userName);
		if (result.get(ProtocolConstants.KEY_ID, null) == null)
			result.put(ProtocolConstants.KEY_ID, new UniversalUniqueIdentifier().toBase64String());
		try {
			result.flush();
		} catch (BackingStoreException e) {
			LogHelper.log(e);
		}
		return new WebUser(result);
	}

	public WebWorkspace createWorkspace(String name) throws CoreException {
		String id = WebWorkspace.nextWorkspaceId();
		WebWorkspace workspace = WebWorkspace.fromId(id);
		workspace.setName(name);
		workspace.save();
		//create an object to represent this new workspace associated with this user
		JSONObject newWorkspace = new JSONObject();
		try {
			newWorkspace.put(ProtocolConstants.KEY_ID, id);
			newWorkspace.put(ProtocolConstants.KEY_LAST_MODIFIED, System.currentTimeMillis());
		} catch (JSONException e) {
			//cannot happen as the keys and values are well-formed
		}

		//add the new workspace to the list of workspaces known to this user
		String workspaces = store.get(ProtocolConstants.KEY_WORKSPACES, null);
		JSONArray workspaceArray = null;
		if (workspaces != null) {
			try {
				workspaceArray = new JSONArray(workspaces);
			} catch (JSONException e) {
				//ignore and create a new one
			}
		}
		if (workspaceArray == null)
			workspaceArray = new JSONArray();
		workspaceArray.put(newWorkspace);
		store.put(ProtocolConstants.KEY_WORKSPACES, workspaceArray.toString());
		save();
		return workspace;
	}

	/**
	 * Returns the workspaces used by this user as a JSON array. 
	 */
	public JSONArray getWorkspacesJSON() {
		try {
			String workspaces = store.get(ProtocolConstants.KEY_WORKSPACES, null);
			//just return empty array if there are no workspaces
			if (workspaces != null)
				return new JSONArray(workspaces);
		} catch (JSONException e) {
			//someone has bashed the underlying storage - just fall through below
		}
		return new JSONArray();
	}
}
