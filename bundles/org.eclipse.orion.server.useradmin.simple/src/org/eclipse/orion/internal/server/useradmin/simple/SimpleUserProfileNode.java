/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.useradmin.simple;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Implementation of the user node the represents one user in the simple meta store.
 * 
 * @author Anthony Hunter
 */
public class SimpleUserProfileNode implements IOrionUserProfileNode {

	private JSONObject userJSONObject = null;

	private File rootLocation = null;

	private String userId = null;

	private final static String USER = "user";

	private SimpleUserProfileProperties profileProperties = null;

	public static final String USER_PROPERTIES = "profileProperties"; //$NON-NLS-1$

	public SimpleUserProfileNode(File rootLocation, String userId) {
		super();
		this.rootLocation = rootLocation;
		this.userId = userId;
		init(userId);
	}

	private void init(String userId) {
		try {
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
			if (!SimpleMetaStoreUtil.isMetaFile(userMetaFolder, USER)) {
				// user.json does not exist for this userId, it should have already been created
				// so this profile is "not found", jsonObject is null.
				return;
			}
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, USER);
			if (jsonObject == null) {
				// user.json is somehow empty or corrupt for this userId
				return;
			}
			userJSONObject = jsonObject;
			if (userJSONObject.has(USER_PROPERTIES)) {
				JSONObject profilePropertiesJSON = jsonObject.getJSONObject(USER_PROPERTIES);
				profileProperties = new SimpleUserProfileProperties(profilePropertiesJSON);
			} else {
				JSONObject profilePropertiesJSON = new JSONObject();
				profileProperties = new SimpleUserProfileProperties(profilePropertiesJSON);
			}
		} catch (JSONException e) {
			LogHelper.log(e);
		}
	}

	public SimpleUserProfileNode(String userName, String partId) {
		if (IOrionUserProfileConstants.GENERAL_PROFILE_PART.equals(partId)) {
			init(userName);
		} else {
			throw new RuntimeException("SimpleUserProfileNode: unknown partId " + partId + " for user " + userName);
		}
	}

	public void put(String key, String value, boolean encrypt) throws CoreException {
		try {
			if (key.equals(UserConstants.KEY_NAME)) {
				userJSONObject.put("FullName", value);
			} else if (key.equals(UserConstants.KEY_UID)) {
				throw new RuntimeException("SimpleUserProfileNode.put: cannot reset the user id for " + userJSONObject.getString("UserName"));
			} else if (key.equals(UserConstants.KEY_LOGIN)) {
				if (userJSONObject.has("UserName")) {
					String userName = userJSONObject.getString("UserName");
					if (!userName.equals(value)) {
						// We are changing the login value, which is a user rename 
						rename(userName, value);
					}
				}
			} else {
				String newValue = value;
				if (encrypt) {
					newValue = SimpleUserPasswordUtil.encryptPassword(value);
				}
				userJSONObject.put(key, newValue);
			}
		} catch (JSONException e) {
			LogHelper.log(e);
		}
	}

	private void rename(String oldUserName, String newUserName) {
		try {
			flush();
			// Rename the user by taking advantage of the MetaStore
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUser(oldUserName);
			userInfo.setUserName(newUserName);
			OrionConfiguration.getMetaStore().updateUser(userInfo);
			init(newUserName);
			userId = newUserName;
		} catch (CoreException e) {
			LogHelper.log(e);
		}
	}

	public String get(String key, String defaultValue) throws CoreException {
		if (userJSONObject != null) {
			try {
				if (key.equals(UserConstants.KEY_NAME)) {
					if (userJSONObject.has("FullName")) {
						return userJSONObject.getString("FullName");
					}
				} else if (key.equals(UserConstants.KEY_UID)) {
					if (userJSONObject.has("UniqueId")) {
						return userJSONObject.getString("UniqueId");
					}
				} else if (key.equals(UserConstants.KEY_LOGIN)) {
					if (userJSONObject.has("UserName")) {
						return userJSONObject.getString("UserName");
					}
				} else if (userJSONObject.has(key)) {
					return userJSONObject.getString(key);
				}
			} catch (JSONException e) {
				LogHelper.log(e);
			}
		}
		return defaultValue;
	}

	public void remove(String key) {
		userJSONObject.remove(key);
	}

	public String[] keys() {
		// even though there are many more values, stick with what is in IOrionUserProfileConstants
		// to match the legacy metastore
		return new String[] {IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, IOrionUserProfileConstants.DISK_USAGE, IOrionUserProfileConstants.DISK_USAGE_TIMESTAMP};
	}

	public IOrionUserProfileNode getUserProfileNode(String pathName) {
		if (userJSONObject == null) {
			return null;
		}
		if (pathName.equals(IOrionUserProfileConstants.GENERAL_PROFILE_PART)) {
			return this;
		} else if (pathName.equals(USER_PROPERTIES)) {
			return profileProperties;
		} else {
			throw new UnsupportedOperationException("SimpleUserProfileNode.getUserProfileNode: path not supported: " + pathName);
		}
	}

	public boolean userProfileNodeExists(String pathName) {
		if (userJSONObject == null) {
			return false;
		}
		if (pathName.equals(IOrionUserProfileConstants.GENERAL_PROFILE_PART)) {
			return true;
		} else if (pathName.equals(USER_PROPERTIES)) {
			return true;
		}
		return false;
	}

	public void removeUserProfileNode() {
		throw new UnsupportedOperationException("RemoveUserProfileNode not supported by SimpleUserProfileNode");
	}

	public String[] childrenNames() {
		return new String[] {IOrionUserProfileConstants.GENERAL_PROFILE_PART};
	}

	public void flush() throws CoreException {
		try {
			// Add the properties to the JSON object before saving to disk
			String[] keys = profileProperties.keys();
			if (keys.length > 0) {
				JSONObject properties = new JSONObject();
				for (int i = 0; i < keys.length; i++) {
					String key = keys[i];
					String value = profileProperties.get(key, "");
					properties.put(key, value);
				}
				userJSONObject.put(USER_PROPERTIES, properties);
			}
			// save to disk
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
			if (!SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, USER, userJSONObject)) {
				throw new RuntimeException("SimpleUserProfileNode.flush: could not update user: " + userId);
			}
		} catch (JSONException e) {
			LogHelper.log(e);
		}
	}

}
