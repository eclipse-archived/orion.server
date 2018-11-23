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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A structure containing a snapshot of information about a single workspace.
 */
public class WorkspaceInfo extends MetadataInfo {

	private List<String> projectNames = EMPTY;
	private String userId;

	/**
	 * Returns the names of all the projects in this workspace
	 */
	public List<String> getProjectNames() {
		return projectNames;
	}

	/**
	 * Returns the user id of the user that owns this workspace.
	 * @return the user id.
	 */
	public String getUserId() {
		return userId;
	}

	/**
	 * Sets the list of project names associated with this workspace. Note
	 * callers should not use this method to create or delete projects.
	 * @param names the unique ids of all the projects associated with this workspace
	 */
	public void setProjectNames(List<String> names) {
		if (names == null || names.isEmpty()) {
			this.projectNames = EMPTY;
		} else {
			//copy and wrap read only 
			this.projectNames = Collections.unmodifiableList(new ArrayList<String>(names));
		}
	}

	/**
	 * Sets the user id of the user that owns this workspace.
	 * @param userId the user id.
	 */
	public void setUserId(String userId) {
		this.userId = userId;
	}

}
