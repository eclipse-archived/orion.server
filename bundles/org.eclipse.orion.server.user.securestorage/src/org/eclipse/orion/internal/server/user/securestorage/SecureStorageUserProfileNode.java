/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.user.securestorage;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;

public class SecureStorageUserProfileNode implements IOrionUserProfileNode {

	private ISecurePreferences node;

	public SecureStorageUserProfileNode(ISecurePreferences node) {
		this.node = node;
	}

	public void put(String key, String value, boolean encrypt) throws CoreException {
		try {
			node.put(key, value, encrypt);
		} catch (StorageException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, "Can not store the user profile", e));
		}
	}

	public String get(String key, String def) throws CoreException {
		try {
			if (node.isEncrypted(key))
				return "";
			return node.get(key, def);
		} catch (StorageException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, "Can not read the user profile", e));
		}
	}

	public void remove(String key) {
		node.remove(key);
	}
	
	public String[] keys() {
		return node.keys();
	}

	public IOrionUserProfileNode getUserProfileNode(String pathName) {
		return new SecureStorageUserProfileNode(node.node(pathName));
	}

	public boolean userProfileNodeExists(String pathName) {
		return node.nodeExists(pathName);
	}

	public void removeUserProfileNode() {
		node.removeNode();
	}
	
	public String[] childrenNames(){
		return node.childrenNames();
	}

	public void flush() throws CoreException {
		try {
			node.flush();
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, "Can not store the user profile", e));
		}
	}
}
