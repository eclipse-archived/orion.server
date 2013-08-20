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
package org.eclipse.orion.server.core.project;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A snapshot of information about a single project.
 */
public class Project {

	private ProjectInfo directory;
	private IFileStore projectInfoFile;
	private String name;
	/**
	 * Name of the file that should be situated in the top level folder to indicate that this folder is a project.
	 * This file contains project metadata in form of {@link JSONObject}.
	 */
	public static final String ORION_PROJECT_FILE = ".orionProject";

	/**
	 * @param project
	 */
	private Project(ProjectInfo directory, IFileStore projectInfoFile) throws CoreException {
		super();
		this.directory = directory;
		this.projectInfoFile = projectInfoFile;
		if(projectInfoFile==null){
			return;
		}
		try {
			String projectInfoContent = IOUtilities.toString(this.projectInfoFile.openInputStream(EFS.NONE, null));
			initFromJson(projectInfoContent.trim().length() > 0 ? new JSONObject(projectInfoContent) : new JSONObject());
		} catch (IOException e) {
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not read project information", e));
		} catch (JSONException e) {
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not parse project information", e));
		}
	}

	public boolean exists() {
		return projectInfoFile != null;
	}

	public ProjectInfo getDirectory() {
		return this.directory;
	}

	public static Project fromProjectInfo(ProjectInfo projectInfo) throws CoreException {
		if (!projectInfo.getProjectStore().fetchInfo().exists()) {
			return new Project(projectInfo, null);
		}
		IFileStore projectInfoFile = projectInfo.getProjectStore().getChild(ORION_PROJECT_FILE);
		IFileInfo childInfo = projectInfoFile.fetchInfo();
		if (childInfo.exists()) {
			return new Project(projectInfo, projectInfoFile);
		}
		return new Project(projectInfo, null);
	}

	private void initFromJson(JSONObject projectInfo) {
		String name = projectInfo.optString("Name");
		this.name = name.length()==0 ? directory.getFullName() : name;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject projectJson = new JSONObject();
		projectJson.put("Name", name);
		return projectJson;
	}
}
