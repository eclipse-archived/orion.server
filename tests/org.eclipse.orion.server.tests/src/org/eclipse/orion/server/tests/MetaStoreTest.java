/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.junit.Test;

public class MetaStoreTest extends AbstractServerTest {
	private WorkspaceInfo createWorkspace() throws CoreException {
		IMetaStore store = getMetaStore();
		UserInfo user = new UserInfo();
		user.setUserName("MetaStoreTestUser");
		store.createUser(user);
		WorkspaceInfo workspace = new WorkspaceInfo();
		workspace.setFullName(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		workspace.setUserId(user.getUniqueId());
		store.createWorkspace(workspace);
		return workspace;
	}

	/**
	 * Returns the metastore to test. Override to test a different metastore implementation.
	 */
	protected IMetaStore getMetaStore() {
		//use currently configured metastore by default
		return OrionConfiguration.getMetaStore();
	}

	@Test
	public void testCreateUser() throws CoreException {
		IMetaStore store = getMetaStore();
		UserInfo user = new UserInfo();
		user.setUserName("testCreateUser");
		store.createUser(user);
		assertNotNull(user.getUniqueId());
	}

	@Test
	public void testUniqueProjectIds() throws CoreException {
		//tests that creating multiple projects will create unique ids
		WorkspaceInfo workspace = createWorkspace();
		IMetaStore metaStore = getMetaStore();
		List<String> ids = new ArrayList<String>();
		for (int i = 0; i < 100; i++) {
			ProjectInfo projectInfo = new ProjectInfo();
			projectInfo.setFullName("Project " + i);
			projectInfo.setWorkspaceId(workspace.getUniqueId());
			IFileStore fileStore = metaStore.getDefaultContentLocation(projectInfo);
			projectInfo.setContentLocation(fileStore.toURI());
			metaStore.createProject(projectInfo);
			String projectId = projectInfo.getUniqueId();
			final java.io.File currentProject = new java.io.File(projectId);
			for (String id : ids) {
				//tests that project id is unique based on current file system case sensitivity
				assertTrue(new java.io.File(id).compareTo(currentProject) != 0);
			}
			ids.add(projectId);
		}
	}

	@Test
	public void testUpdateUser() throws CoreException {
		IMetaStore store = getMetaStore();
		UserInfo user = new UserInfo();
		user.setUserName("testUpdateUser");
		//should not be able to update user that does not exist
		try {
			store.updateUser(user);
			assertTrue("Update should have failed", false);
		} catch (Exception e) {
			//expected
		}
		store.createUser(user);
		//now update should succeed
		try {
			store.updateUser(user);
		} catch (Exception e) {
			assertTrue("Update should have succeeded", false);
		}
		store.deleteUser(user.getUniqueId());
		//should not be able to update user that does not exist
		try {
			store.updateUser(user);
			assertTrue("Update should have failed", false);
		} catch (Exception e) {
			//expected
		}
	}
}
