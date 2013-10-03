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

import java.io.File;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.json.JSONObject;

/**
 * Implementation of the root node the represents the list of users in the simple meta store.
 * The children of the root node and the list of users in the simple meta store.
 * 
 * @author Anthony Hunter
 */
public class SimpleUserProfileRoot implements IOrionUserProfileNode {

	public final static String USER = "user";

	private File rootLocation = null;

	public SimpleUserProfileRoot(File rootLocation) {
		super();
		this.rootLocation = rootLocation;
	}

	public void put(String key, String value, boolean encrypt) throws CoreException {
		throw new UnsupportedOperationException("Put is not supported on a SimpleUserProfileRoot.");
	}

	public String get(String key, String def) throws CoreException {
		throw new UnsupportedOperationException("Get is not supported on a SimpleUserProfileRoot.");
	}

	public void remove(String key) {
		throw new UnsupportedOperationException("Remove is not supported on a SimpleUserProfileRoot.");
	}

	public String[] keys() {
		String[] keys = new String[] { USER };
		return keys;
	}

	public IOrionUserProfileNode getUserProfileNode(String pathName) {
		return new SimpleUserProfileNode(rootLocation, pathName);
	}

	public boolean userProfileNodeExists(String pathName) {
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, pathName);
		if (SimpleMetaStoreUtil.isMetaFile(userMetaFolder, USER)) {
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, USER);
			if (jsonObject.has(UserConstants.KEY_PASSWORD)) {
				return true;
			}
		}
		return false;
	}

	public void removeUserProfileNode() {
		throw new UnsupportedOperationException("RemoveUserProfileNode is not supported on a SimpleUserProfileRoot.");
	}

	public String[] childrenNames() {
		List<String> users = SimpleMetaStoreUtil.listMetaUserFolders(rootLocation);
		String[] userNames = users.toArray(new String[users.size()]);
		return userNames;
	}

	public void flush() throws CoreException {
		throw new UnsupportedOperationException("Flush is not supported on a SimpleUserProfileRoot.");
	}

}