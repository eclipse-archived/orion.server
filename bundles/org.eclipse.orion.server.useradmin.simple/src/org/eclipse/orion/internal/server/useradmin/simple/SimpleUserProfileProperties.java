/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.useradmin.simple;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Implementation of the child node the represents the profile properties of a
 * user in the simple meta store.
 * 
 * @author Anthony Hunter
 */
public class SimpleUserProfileProperties implements IOrionUserProfileNode {

	private JSONObject profilePropertiesJSON = null;

	public SimpleUserProfileProperties(JSONObject profilePropertiesJSON) {
		this.profilePropertiesJSON = profilePropertiesJSON;
	}

	public String[] childrenNames() {
		throw new UnsupportedOperationException("ChildrenNames is not supported on a SimpleUserProfileProperties.");
	}

	public void flush() throws CoreException {
		throw new UnsupportedOperationException("Flush is not supported on a SimpleUserProfileProperties.");
	}

	public String get(String key, String def) throws CoreException {
		try {
			if (profilePropertiesJSON.has(key)) {
				return profilePropertiesJSON.getString(key);
			}
		} catch (JSONException e) {
			LogHelper.log(e);
		}
		return def;
	}

	public IOrionUserProfileNode getUserProfileNode(String pathName) {
		throw new UnsupportedOperationException("GetUserProfileNode is not supported on a SimpleUserProfileProperties.");
	}

	public String[] keys() {
		try {
			JSONArray names = profilePropertiesJSON.names();
			if (names != null) {
				String[] keys = new String[names.length()];
				for (int i = 0; i < names.length(); i++) {
					keys[i] = names.getString(i);
				}
				return keys;
			}
		} catch (JSONException e) {
			LogHelper.log(e);
		}
		return new String[0];
	}

	public void put(String key, String value, boolean encrypt) throws CoreException {
		try {
			profilePropertiesJSON.put(key, value);
		} catch (JSONException e) {
			LogHelper.log(e);
		}
	}

	public void remove(String key) {
		profilePropertiesJSON.remove(key);
	}

	public void removeUserProfileNode() {
		throw new UnsupportedOperationException("RemoveUserProfileNode is not supported on a SimpleUserProfileProperties.");
	}

	public boolean userProfileNodeExists(String pathName) {
		throw new UnsupportedOperationException("UserProfileNodeExists is not supported on a SimpleUserProfileProperties.");
	}

}
