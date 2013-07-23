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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleLinuxMetaStore;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleLinuxMetaStoreTests {

	private IMetaStore createMetaStore() throws URISyntaxException {
		URI metaStoreRoot = SimpleLinuxMetaStoreUtilTest.createTestMetaStoreFolder();
		IMetaStore simpleLinuxMetaStore = new SimpleLinuxMetaStore(metaStoreRoot);
		assertNotNull(simpleLinuxMetaStore);
		return simpleLinuxMetaStore;
	}

	private ProjectInfo createProject(IMetaStore metaStore, String workspaceId, String projectName) throws CoreException, URISyntaxException {
		// create the ProjectInfo
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		projectInfo.setContentLocation(new URI("File://test.com"));

		// create the project
		metaStore.createProject(workspaceId, projectInfo);
		return projectInfo;
	}

	private UserInfo createUser(IMetaStore metaStore, String userName) throws CoreException {
		// create the UserInfo
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(userName);
		userInfo.setFullName(userName);

		// create the user
		metaStore.createUser(userInfo);
		return userInfo;
	}

	private WorkspaceInfo createWorkspace(IMetaStore metaStore, String userId, String workspaceName) throws CoreException {
		// create the WorkspaceInfo
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);

		// create the workspace
		metaStore.createWorkspace(userId, workspaceInfo);
		return workspaceInfo;
	}

	private void deleteUser(IMetaStore metaStore, String userId) throws CoreException {
		metaStore.deleteUser(userId);
		List<String> allUsers = metaStore.readAllUsers();
		assertFalse(allUsers.contains(userId));
	}

	private void deleteWorkspace(IMetaStore metaStore, String userId, String workspaceId) throws CoreException {
		metaStore.deleteWorkspace(userId, workspaceId);
		UserInfo userInfo = metaStore.readUser(userId);
		List<String> workspaceIds = userInfo.getWorkspaceIds();
		assertFalse(workspaceIds.contains(workspaceId));
	}

	private void deleteProject(IMetaStore metaStore, String workspaceId, String projectId) throws CoreException {
		metaStore.deleteProject(workspaceId, projectId);
		WorkspaceInfo workspaceInfo = metaStore.readWorkspace(workspaceId);
		List<String> projectIds = workspaceInfo.getProjectNames();
		assertFalse(projectIds.contains(projectId));
	}

	@Test
	public void testCreateProject() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// create the workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName);
		assertNotNull(workspaceInfo);

		// create the project
		String projectName = "Orion Project";
		ProjectInfo projectInfo = createProject(metaStore, workspaceInfo.getUniqueId(), projectName);
		assertNotNull(projectInfo);
	}

	@Test
	public void testCreateUser() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		String uniqueId = userInfo.getUniqueId();
		assertEquals(userInfo.getUserName(), userName);

		// read the user back again
		UserInfo readUserInfo = metaStore.readUser(uniqueId);
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUniqueId(), uniqueId);
		assertEquals(readUserInfo.getUserName(), userName);

		// make sure the user is in the list of all users
		List<String> allUsers = metaStore.readAllUsers();
		assertEquals(allUsers.size(), 1);
		assertTrue(allUsers.contains(uniqueId));

		// delete the user
		deleteUser(metaStore, uniqueId);
	}

	@Test
	public void testCreateWorkspace() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// create the workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName);
		assertNotNull(workspaceInfo);
	}

	@Test
	public void testDeleteProject() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// create a workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName);
		assertNotNull(workspaceInfo);

		// create the project
		String projectName1 = "Orion Project";
		ProjectInfo projectInfo1 = createProject(metaStore, workspaceInfo.getUniqueId(), projectName1);
		assertNotNull(projectInfo1);

		// create another project
		String projectName2 = "Another Project";
		ProjectInfo projectInfo2 = createProject(metaStore, workspaceInfo.getUniqueId(), projectName2);
		assertNotNull(projectInfo2);

		// delete the first project
		deleteProject(metaStore, workspaceInfo.getUniqueId(), projectInfo1.getUniqueId());

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo.getFullName());
		assertEquals(readWorkspaceInfo.getProjectNames().size(), 1);
		assertFalse(readWorkspaceInfo.getProjectNames().contains(projectInfo1.getUniqueId()));
		assertTrue(readWorkspaceInfo.getProjectNames().contains(projectInfo2.getUniqueId()));
	}

	@Test
	public void testDeleteUser() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testDeleteWorkspace() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// create a workspace
		String workspaceName1 = "Orion Content";
		WorkspaceInfo workspaceInfo1 = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName1);
		assertNotNull(workspaceInfo1);

		// create another workspace
		String workspaceName2 = "workspace2";
		WorkspaceInfo workspaceInfo2 = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName2);
		assertNotNull(workspaceInfo2);

		// delete the first workspace
		deleteWorkspace(metaStore, userInfo.getUniqueId(), workspaceInfo1.getUniqueId());

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());
		assertEquals(readUserInfo.getWorkspaceIds().size(), 1);
		assertFalse(readUserInfo.getWorkspaceIds().contains(workspaceInfo1.getUniqueId()));
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo2.getUniqueId()));
	}

	@Test
	public void testReadAllUsers() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userId1 = "anthony";
		UserInfo userInfo1 = createUser(metaStore, userId1);
		assertNotNull(userInfo1);

		// create a second user
		String userId2 = "ahunter";
		UserInfo userInfo2 = createUser(metaStore, userId2);
		assertNotNull(userInfo2);

		List<String> allUsers = metaStore.readAllUsers();
		assertNotNull(allUsers);
		assertTrue(allUsers.contains(userInfo1.getUniqueId()));
		assertTrue(allUsers.contains(userInfo2.getUniqueId()));
	}

	@Test
	public void testReadProject() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// create a workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName);
		assertNotNull(workspaceInfo);

		// create the project
		String projectName1 = "Orion Project";
		ProjectInfo projectInfo1 = createProject(metaStore, workspaceInfo.getUniqueId(), projectName1);
		assertNotNull(projectInfo1);

		// create another project
		String projectName2 = "Another Project";
		ProjectInfo projectInfo2 = createProject(metaStore, workspaceInfo.getUniqueId(), projectName2);
		assertNotNull(projectInfo2);

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo.getFullName());
		assertEquals(readWorkspaceInfo.getProjectNames().size(), 2);
		assertTrue(readWorkspaceInfo.getProjectNames().contains(projectInfo1.getUniqueId()));
		assertTrue(readWorkspaceInfo.getProjectNames().contains(projectInfo2.getUniqueId()));

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo2.getUniqueId());
		assertNotNull(readProjectInfo);
		assertEquals(readProjectInfo.getFullName(), projectInfo2.getFullName());
	}

	@Test
	public void testReadUser() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userId = "anthony";
		UserInfo userInfo = createUser(metaStore, userId);
		assertNotNull(userInfo);

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUniqueId(), userInfo.getUniqueId());
	}

	@Test
	public void testReadWorkspace() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// create a workspace
		String workspaceName1 = "Orion Content";
		WorkspaceInfo workspaceInfo1 = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName1);
		assertNotNull(workspaceInfo1);

		// create another workspace
		String workspaceName2 = "workspace2";
		WorkspaceInfo workspaceInfo2 = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName2);
		assertNotNull(workspaceInfo2);

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());
		assertEquals(readUserInfo.getWorkspaceIds().size(), 2);
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo1.getUniqueId()));
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo2.getUniqueId()));

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo2.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo2.getFullName());
	}

	@Test
	public void testUpdateProject() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// create the workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName);
		assertNotNull(workspaceInfo);

		// create the project
		String projectName = "Orion Project";
		ProjectInfo projectInfo = createProject(metaStore, workspaceInfo.getUniqueId(), projectName);
		assertNotNull(projectInfo);

		// update with a dummy project id
		URI newURI = new URI("file:/workspace/foo");
		projectInfo.setContentLocation(newURI);

		// update the project
		metaStore.updateProject(projectInfo);

		// read the project back again
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getUniqueId());
		assertNotNull(readProjectInfo);
		assertTrue(readProjectInfo.getContentLocation().equals(newURI));
	}

	@Test
	public void testUpdateUser() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userId = "anthony";
		UserInfo userInfo = createUser(metaStore, userId);
		assertNotNull(userInfo);

		// update the UserInfo
		String fullName = "Anthony Hunter";
		userInfo.setFullName(fullName);

		// update the user
		metaStore.updateUser(userInfo);

		// read the user back again
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getFullName(), fullName);
		assertEquals(readUserInfo.getFullName(), userInfo.getFullName());

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
		List<String> allUsers = metaStore.readAllUsers();
		assertEquals(allUsers.size(), 0);
	}

	@Test
	public void testUpdateWorkspace() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = createMetaStore();

		// create the user
		String userName = "anthony";
		UserInfo userInfo = createUser(metaStore, userName);
		assertNotNull(userInfo);

		// create the workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = createWorkspace(metaStore, userInfo.getUniqueId(), workspaceName);
		assertNotNull(workspaceInfo);

		// update with a dummy project id
		String projectId = "new";
		List<String> projectIds = new ArrayList<String>();
		projectIds.add(projectId);
		workspaceInfo.setProjectNames(projectIds);

		// update the user
		metaStore.updateWorkspace(workspaceInfo);

		// read the workspace back again
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertTrue(readWorkspaceInfo.getProjectNames().contains(projectId));
	}
}
