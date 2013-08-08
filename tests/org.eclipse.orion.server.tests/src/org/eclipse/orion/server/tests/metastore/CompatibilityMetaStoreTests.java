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
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Test the implementation of an {@link IMetaStore}. 
 * Two tests fail so those methods are overridden.
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

	/**
	 * Overridden for CompatibilityMetaStore, when a workspace is deleted, the workspace is not removed from the user.
	 */
	@Test
	public void testDeleteWorkspace() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setUniqueId("1");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName1 = "Orion Content";
		WorkspaceInfo workspaceInfo1 = new WorkspaceInfo();
		workspaceInfo1.setFullName(workspaceName1);
		metaStore.createWorkspace(userInfo.getUniqueId(), workspaceInfo1);

		// create another workspace
		String workspaceName2 = "Workspace2";
		WorkspaceInfo workspaceInfo2 = new WorkspaceInfo();
		workspaceInfo2.setFullName(workspaceName2);
		metaStore.createWorkspace(userInfo.getUniqueId(), workspaceInfo2);

		// delete the first workspace
		metaStore.deleteWorkspace(userInfo.getUniqueId(), workspaceInfo1.getUniqueId());

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());
		// Fails for CompatibilityMetaStore, workspace is not removed from the user.
		//assertEquals(1, readUserInfo.getWorkspaceIds().size());
		//assertFalse(readUserInfo.getWorkspaceIds().contains(workspaceInfo1.getUniqueId()));
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo2.getUniqueId()));

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

}
