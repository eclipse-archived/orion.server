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
	private String userName;

	private List<String> workspaceIds = EMPTY;

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
	 * @param userName the userName to set
	 */
	public void setUserName(String userName) {
		this.userName = userName;
	}

	/**
	 * Sets the list of unique workspace ids associated with this user. Note
	 * callers should not use this method to create or delete workspaces.
	 * @param ids the unique ids of all the workspaces associated with this user
	 */
	public void setWorkspaceIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			this.workspaceIds = EMPTY;
		} else {
			//copy and wrap read only 
			this.workspaceIds = Collections.unmodifiableList(new ArrayList<String>(ids));
		}
	}

}
