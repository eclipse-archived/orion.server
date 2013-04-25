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
 * A structure containing a snapshot of information about a single workspace.
 */
public class WorkspaceInfo extends MetadataInfo {

	private List<String> projectIds = EMPTY;

	/**
	 * Returns the unique id of all the projects in this workspace
	 */
	public List<String> getProjectIds() {
		return projectIds;
	}

	/**
	 * Sets the list of unique project ids associated with this workspace. Note
	 * callers should not use this method to create or delete projects.
	 * @param ids the unique ids of all the projects associated with this workspace
	 */
	public void setProjectIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			this.projectIds = EMPTY;
		} else {
			//copy and wrap read only 
			this.projectIds = Collections.unmodifiableList(new ArrayList<String>(ids));
		}
	}

}
