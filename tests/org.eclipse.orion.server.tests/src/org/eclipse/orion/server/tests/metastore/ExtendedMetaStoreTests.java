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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Abstract class to test an implementation of an {@link IMetaStore}. 
 * This class extends the base and attempts to extend with the "not-happy" code paths.
 *   
 * @author Anthony Hunter
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class ExtendedMetaStoreTests extends MetaStoreTests {

	@Test(expected = CoreException.class)
	public void testCreateProjectWithAnInvalidWorkspaceId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the project, do not specify a valid workspace id
		String projectName = "Orion Project";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		try {
			projectInfo.setContentLocation(new URI("file://test.com"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo.setWorkspaceId("77");
		metaStore.createProject(projectInfo);
	}

	@Test(expected = CoreException.class)
	public void testCreateProjectWithNoWorkspaceId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the project, do not specify a workspace id
		String projectName = "Orion Project";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		try {
			projectInfo.setContentLocation(new URI("file://test.com"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		metaStore.createProject(projectInfo);
	}

	@Test
	public void testCreateTwoWorkspacesWithSameName() throws CoreException {
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
		workspaceInfo1.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo1);

		// create another workspace with the same workspace name
		String workspaceName2 = "Orion Content";
		WorkspaceInfo workspaceInfo2 = new WorkspaceInfo();
		workspaceInfo2.setFullName(workspaceName2);
		workspaceInfo2.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo2);

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());
		assertEquals(2, readUserInfo.getWorkspaceIds().size());
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo1.getUniqueId()));
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo2.getUniqueId()));

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo2.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo2.getFullName());

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test(expected = CoreException.class)
	public void testCreateUserWithNoUserId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user and do not provide a userId
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		fail("Should not be able to create the user without a userId.");
	}

	@Test(expected = CoreException.class)
	public void testCreateUserWithNoUserName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user and do not provide a userId
		UserInfo userInfo = new UserInfo();
		userInfo.setUniqueId("1");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		fail("Should not be able to create the user without a user name.");
	}

	@Test
	public void testCreateWorkspaceWithAnInvalidUserId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the workspace without specifying an existing userid.
		// the user with id '77' is created at the readUser() API.
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setUserId("77");
		workspaceInfo.setFullName(workspaceName);
		metaStore.createWorkspace(workspaceInfo);
	}

	@Test(expected = CoreException.class)
	public void testCreateWorkspaceWithNoUserId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setUniqueId("1");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace without specifying a userid.
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		metaStore.createWorkspace(workspaceInfo);

		fail("Should not be able to create the workspace without a user id.");
	}

	@Test(expected = CoreException.class)
	public void testCreateWorkspaceWithNoWorkspaceName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setUniqueId("1");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace without specifying a workspace name.
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		fail("Should not be able to create the workspace without a workspace name.");
	}

	@Test
	public void testReadProjectThatDoesNotExist() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// read the user from the previous test
		UserInfo userInfo = metaStore.readUser("1");

		// create the workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo1 = new WorkspaceInfo();
		workspaceInfo1.setFullName(workspaceName);
		workspaceInfo1.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo1);

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo1.getUniqueId(), "Project Zero");
		assertNull(readProjectInfo);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testReadUserThatDoesNotExist() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// read the user, it will be created
		UserInfo userInfo = metaStore.readUser("anthony");
		assertNotNull(userInfo);
		assertEquals("Unnamed User", userInfo.getFullName());
		assertEquals("anthony", userInfo.getUserName());

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testReadWorkspaceSpecifyNullWorkspaceId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// read the workspace that does not exist
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(null);
		assertNull(readWorkspaceInfo);
	}

	@Test
	public void testReadWorkspaceThatDoesNotExist() throws CoreException {
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
		workspaceInfo1.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo1);

		// create another workspace
		String workspaceName2 = "Workspace2";
		WorkspaceInfo workspaceInfo2 = new WorkspaceInfo();
		workspaceInfo2.setFullName(workspaceName2);
		workspaceInfo2.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo2);

		// read the workspace that does not exist
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace("anthony-Workspace77");
		assertNull(readWorkspaceInfo);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

}
