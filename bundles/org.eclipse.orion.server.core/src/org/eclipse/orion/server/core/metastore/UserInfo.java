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

import java.util.*;

/**
 * Information about a single user.
 */
public class UserInfo extends MetadataInfo {
	private boolean isGuest;

	private String userName;

	private List<String> workspaceIds;

	/**
	 * Returns the username for this user. A username is a short handle typically
	 * used for login or to allow anonymous interaction between users. This
	 * is distinct from the user's full name as defined by {@link MetadataInfo#getFullName()}.
	 * @return the userName
	 */
	public String getUserName() {
		return userName;
	}

	/**
	 * Returns the globally unique id of all the workspaces owned by this user.
	 */
	public List<String> getWorkspaceIds() {
		return workspaceIds;
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

	/**
	 * @param userName the userName to set
	 */
	public void setUserName(String userName) {
		this.userName = userName;
	}

	public void setWorkspaceIds(List<String> ids) {
		//copy and wrap read only 
		this.workspaceIds = Collections.unmodifiableList(new ArrayList<String>(ids));
	}

}
