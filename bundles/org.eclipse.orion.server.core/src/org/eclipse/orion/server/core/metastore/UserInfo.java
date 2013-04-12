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
package org.eclipse.orion.server.core.metastore;

import java.util.List;

/**
 * Information about a single user.
 */
public class UserInfo extends MetadataInfo {
	private String userName;

	/**
	 * @return the userName
	 */
	public String getUserName() {
		return userName;
	}

	/**
	 * @param userName the userName to set
	 */
	public void setUserName(String userName) {
		this.userName = userName;
	}

	/**
	 * @return the fullName
	 */
	public String getFullName() {
		return fullName;
	}

	/**
	 * @param fullName the fullName to set
	 */
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	/**
	 * @return the isGuest
	 *TODO Should this just be a generic property?
	 */
	public boolean isGuest() {
		return isGuest;
	}

	/**
	 * @param isGuest the isGuest to set
	 */
	public void setGuest(boolean isGuest) {
		this.isGuest = isGuest;
	}

	private String fullName;
	private boolean isGuest;

	/**
	 * Returns the globally unique id of all the workspaces owned by this user.
	 */
	public List<String> getWorkspaceIds() {
		return null;
	}

}
