/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Test the implementation of an {@link IMetaStore}. 
 * One test fails so this method is overridden.
 *   
 * @author Anthony Hunter
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CompatibilityMetaStoreTests extends MetaStoreTests {

	@Override
	public IMetaStore getMetaStore() {
		//just use the currently configured metastore by default.
		return OrionConfiguration.getMetaStore();
	}

	@Test
	public void testGetDefaultContentLocation() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create the project
		String projectName = "Orion Project";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);

		// get the default content location
		IFileStore defaultContentLocation = getMetaStore().getDefaultContentLocation(projectInfo);
		String location = defaultContentLocation.toLocalFile(EFS.NONE, null).toString();

		// get the workspace root 
		IFileStore root = OrionConfiguration.getRootLocation();
		IFileStore projectHome = root.getChild(projectInfo.getUniqueId());
		String correctLocation = projectHome.toLocalFile(EFS.NONE, null).toString();

		assertEquals(correctLocation, location);
	}

	@Test
	public void testGetUserHome() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// get the user home
		IFileStore userHome = getMetaStore().getUserHome(userInfo.getUniqueId());
		String location = userHome.toLocalFile(EFS.NONE, null).toString();

		// get the workspace root 
		IFileStore root = OrionConfiguration.getRootLocation();
		String correctLocation = root.toLocalFile(EFS.NONE, null).toString();

		// by default, projects are under the root, so userHome should match the root
		assertEquals(correctLocation, location);
	}

	@Test
	public void testGetWorkspaceContentLocation() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// get the workspace content location
		IFileStore workspaceHome = getMetaStore().getWorkspaceContentLocation(workspaceInfo.getUniqueId());
		String location = workspaceHome.toLocalFile(EFS.NONE, null).toString();

		// get the workspace root 
		IFileStore root = OrionConfiguration.getRootLocation();
		String correctLocation = root.toLocalFile(EFS.NONE, null).toString();

		// by default, projects are under the root, so userHome should match the root
		assertEquals(correctLocation, location);
	}
}
