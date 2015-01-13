/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search.grep;

import java.net.URISyntaxException;

import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a result of a search, which is file in the users workspace that matches the search options.
 * 
 * @author Anthony Hunter
 */
public class SearchResult extends SearchScope {

	public SearchResult(IFileStore fileStore, WorkspaceInfo workspace, ProjectInfo project) {
		super(fileStore, workspace, project);
	}

	/**
	 * Provides a JSON representation of the search result.
	 * 
	 * @return a JSON representation of the search result.
	 * @throws URISyntaxException
	 * @throws JSONException
	 * @throws CoreException
	 */
	public JSONObject toJSON() throws URISyntaxException, JSONException, CoreException {
		JSONObject doc = new JSONObject();

		IFileInfo fileInfo = getFileStore().fetchInfo();
		// Set file details
		doc.put(ProtocolConstants.KEY_NAME, fileInfo.getName());
		doc.put(ProtocolConstants.KEY_LENGTH, fileInfo.getLength());
		doc.put(ProtocolConstants.KEY_DIRECTORY, fileInfo.isDirectory());
		doc.put(ProtocolConstants.KEY_LAST_MODIFIED, fileInfo.getLastModified());
		// Prepare project data
		IFileStore projectStore = getProject().getProjectStore();
		int projectLocationLength = projectStore.toURI().toString().length();
		IPath projectLocation = new Path(Activator.LOCATION_FILE_SERVLET).append(getWorkspace().getUniqueId()).append(getProject().getFullName()).addTrailingSeparator();
		String projectRelativePath = getFileStore().toURI().toString().substring(projectLocationLength);
		// Add location to json
		IPath fileLocation = projectLocation.append(projectRelativePath);
		doc.put(ProtocolConstants.KEY_LOCATION, fileLocation.toString());
		String projectName = getProject().getFullName();
		//Projects with no name are due to an old bug where project metadata was not deleted  see bug 367333.
		if (projectName != null)
			doc.put(ProtocolConstants.KEY_PATH, new Path(projectName).append(projectRelativePath));
		return doc;
	}
}
