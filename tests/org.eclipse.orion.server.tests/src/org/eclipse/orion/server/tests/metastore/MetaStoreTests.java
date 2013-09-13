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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

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
 * This class has all the test that tests the "happy" code paths.
 *   
 * @author Anthony Hunter
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class MetaStoreTests {

	/**
	 * Get the {@link IMetaStore} to test. 
	 * 
	 * @return an instance of the IMetaStore to test.
	 */
	public abstract IMetaStore getMetaStore();

	@Test
	public void testCreateProject() throws CoreException {
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
		try {
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testCreateUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// read the user back again
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUniqueId(), userInfo.getUniqueId());
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());

		// make sure the user is in the list of all users
		List<String> allUsers = metaStore.readAllUsers();
		assertTrue(allUsers.contains(userInfo.getUniqueId()));

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());

		// make sure the user is not the list of all users
		allUsers = metaStore.readAllUsers();
		assertFalse(allUsers.contains(userInfo.getUniqueId()));
	}

	@Test
	public void testCreateWorkspace() throws CoreException {
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testDeleteProject() throws CoreException {
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
		String projectName1 = "Orion Project";
		ProjectInfo projectInfo1 = new ProjectInfo();
		projectInfo1.setFullName(projectName1);
		try {
			projectInfo1.setContentLocation(new URI("file://root/folder/orion"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo1.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo1);

		// create another project
		String projectName2 = "Another Project";
		ProjectInfo projectInfo2 = new ProjectInfo();
		projectInfo2.setFullName(projectName2);
		try {
			projectInfo2.setContentLocation(new URI("file://root/folder/another"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo2.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo2);

		// delete the first project
		metaStore.deleteProject(workspaceInfo.getUniqueId(), projectInfo1.getFullName());

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo.getFullName());
		assertFalse(readWorkspaceInfo.getProjectNames().contains(projectInfo1.getFullName()));
		assertTrue(readWorkspaceInfo.getProjectNames().contains(projectInfo2.getFullName()));

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testDeleteUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());

		// make sure the user is not the list of all users
		List<String> allUsers = metaStore.readAllUsers();
		assertFalse(allUsers.contains(userInfo.getUniqueId()));
	}

	@Test
	public void testDeleteWorkspace() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
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

		// delete the first workspace
		metaStore.deleteWorkspace(userInfo.getUniqueId(), workspaceInfo1.getUniqueId());

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());
		assertEquals("Should only be one workspace", 1, readUserInfo.getWorkspaceIds().size());
		assertFalse(readUserInfo.getWorkspaceIds().contains(workspaceInfo1.getUniqueId()));
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo2.getUniqueId()));

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testReadAllUsers() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo1 = new UserInfo();
		userInfo1.setUserName("anthony");
		userInfo1.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo1);

		// create a second user
		UserInfo userInfo2 = new UserInfo();
		userInfo2.setUserName("john");
		userInfo2.setFullName("John Doe");
		metaStore.createUser(userInfo2);

		// both user should be in the users list
		List<String> allUsers = metaStore.readAllUsers();
		assertNotNull(allUsers);
		assertTrue(allUsers.contains(userInfo1.getUniqueId()));
		assertTrue(allUsers.contains(userInfo2.getUniqueId()));

		// delete the first user
		metaStore.deleteUser(userInfo1.getUniqueId());

		// delete the second user
		metaStore.deleteUser(userInfo2.getUniqueId());
	}

	@Test
	public void testReadProject() throws CoreException {
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
		String projectName1 = "Orion Project";
		ProjectInfo projectInfo1 = new ProjectInfo();
		projectInfo1.setFullName(projectName1);
		try {
			projectInfo1.setContentLocation(new URI("file://root/folder/orion"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo1.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo1);

		// create another project
		String projectName2 = "Another Project";
		ProjectInfo projectInfo2 = new ProjectInfo();
		projectInfo2.setFullName(projectName2);
		try {
			projectInfo2.setContentLocation(new URI("file://root/folder/another"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo2.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo2);

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo.getFullName());
		assertEquals(2, readWorkspaceInfo.getProjectNames().size());
		assertTrue(readWorkspaceInfo.getProjectNames().contains(projectInfo1.getFullName()));
		assertTrue(readWorkspaceInfo.getProjectNames().contains(projectInfo2.getFullName()));

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo2.getFullName());
		assertNotNull(readProjectInfo);
		assertEquals(readProjectInfo.getFullName(), projectInfo2.getFullName());

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testReadUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUniqueId(), userInfo.getUniqueId());

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testReadWorkspace() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
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

	@Test
	public void testUpdateProject() throws URISyntaxException, CoreException {
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
		try {
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);

		// update the project
		URI newURI = new URI("file:/workspace/foo");
		projectInfo.setContentLocation(newURI);
		projectInfo.setProperty("New", "Property");

		// update the project
		metaStore.updateProject(projectInfo);

		// read the project back again
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getFullName());
		assertNotNull(readProjectInfo);
		assertTrue(readProjectInfo.getContentLocation().equals(newURI));
		assertEquals(readProjectInfo.getProperty("New"), "Property");

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testUpdateUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// update the UserInfo
		String fullName = "Anthony Hunter";
		userInfo.setProperty("New", "Property");
		userInfo.setFullName(fullName);

		// update the user
		metaStore.updateUser(userInfo);

		// read the user back again
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getFullName(), fullName);
		assertEquals(readUserInfo.getFullName(), userInfo.getFullName());
		assertEquals(readUserInfo.getProperty("New"), "Property");

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
		List<String> allUsers = metaStore.readAllUsers();
		assertFalse(allUsers.contains(userInfo.getUniqueId()));
	}

	@Test
	public void testUpdateWorkspace() throws CoreException {
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

		// update the workspace
		workspaceInfo.setProperty("New", "Property");
		metaStore.updateWorkspace(workspaceInfo);

		// read the workspace back again
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getProperty("New"), "Property");

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}
}
