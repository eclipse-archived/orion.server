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
package org.eclipse.orion.internal.server.search;

import java.io.File;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;

/**
 * Represents the scope of a search, which is file in the users workspace. The file may be
 * a directory and could be the project directory or a subdirectory in the project.
 * 
 * @author Anthony Hunter
 */
public class SearchScope {

	private WorkspaceInfo workspace;
	private ProjectInfo project;
	private IFileStore fileStore;
	private File file;

	public SearchScope(IFileStore fileStore, WorkspaceInfo workspace, ProjectInfo project) {
		this.fileStore = fileStore;
		this.workspace = workspace;
		this.project = project;
		file = new File(fileStore.toURI());
	}

	public File getFile() {
		return file;
	}

	public IFileStore getFileStore() {
		return fileStore;
	}

	public WorkspaceInfo getWorkspace() {
		return workspace;
	}

	public ProjectInfo getProject() {
		return project;
	}

}
