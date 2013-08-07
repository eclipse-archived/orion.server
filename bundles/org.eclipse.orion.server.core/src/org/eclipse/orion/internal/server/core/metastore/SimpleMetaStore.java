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
package org.eclipse.orion.internal.server.core.metastore;

import java.util.List;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.metastore.*;

/**
 * A simple metadata storage implementation that segments metadata by user
 * and stores metadata alongside user's data files.
 */
public class SimpleMetaStore implements IMetaStore {
	//	- workspace/
	//		- prefix/
	//			- userId/
	//				- userId.txt -> User and workspace settings
	//				- projectA/
	//					- 

	public void createProject(ProjectInfo info) throws CoreException {
	}

	public void createUser(UserInfo info) throws CoreException {
	}

	public void createWorkspace(WorkspaceInfo info) throws CoreException {
	}

	public void deleteProject(String userId, String workspaceId, String projectName) throws CoreException {
	}

	public void deleteUser(String userId) throws CoreException {
	}

	public void deleteWorkspace(String userId, String workspaceId) throws CoreException {
	}

	public List<String> readAllUsers() throws CoreException {
		return null;
	}

	public ProjectInfo readProject(ProjectInfo project) throws CoreException {
		return null;
	}

	public ProjectInfo readProject(String userId, String workspaceId, String projectName) throws CoreException {
		return null;
	}

	public UserInfo readUser(String userId) throws CoreException {
		return null;
	}

	public WorkspaceInfo readWorkspace(String userId, String workspaceId) throws CoreException {
		return null;
	}

	public void updateProject(ProjectInfo project) throws CoreException {
	}

	public void updateUser(UserInfo info) throws CoreException {
	}

	public void updateWorkspace(WorkspaceInfo info) throws CoreException {
	}

}
