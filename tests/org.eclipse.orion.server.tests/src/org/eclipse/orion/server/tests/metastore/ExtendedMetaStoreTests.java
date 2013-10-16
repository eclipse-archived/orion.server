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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.server.core.OrionConfiguration;
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

	@Test
	public void testCreateProjectUsingFileAPI() throws CoreException {
		IMetaStore metaStore = null;
		try {
			metaStore = OrionConfiguration.getMetaStore();
		} catch (NullPointerException e) {
			// expected when the workbench is not running
		}
		if (!(metaStore instanceof SimpleMetaStore)) {
			// the workbench is not running, just pass the test
			return;
		}

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

		// create a folder under the user on the filesystem
		IFileStore userHome = OrionConfiguration.getUserHome(userInfo.getUniqueId());
		String workspaceFolder = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceInfo.getUniqueId());
		String projectName = "Orion Project";
		IFileStore projectFolder = userHome.getChild(workspaceFolder).getChild(projectName);
		assertFalse(projectFolder.fetchInfo().exists());
		projectFolder.mkdir(EFS.NONE, null);
		assertTrue(projectFolder.fetchInfo().exists() && projectFolder.fetchInfo().isDirectory());

		// read the project, project json will be created
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectName);
		assertNotNull(readProjectInfo);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test(expected = CoreException.class)
	public void testCreateProjectWithAnInvalidWorkspaceId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the project, do not specify a valid workspace id
		String projectName = "Orion Project";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		try {
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
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
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		metaStore.createProject(projectInfo);
	}

	@Test(expected = CoreException.class)
	public void testCreateProjectWithURLAsName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// read the user from the previous test
		UserInfo userInfo = metaStore.readUser("anthony");

		// create the workspace
		String workspaceName = "Orion Content";
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create the project, specify a URL as the name, which is not a valid project name.
		String badProjectName = "http://orion.eclipse.org/";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(badProjectName);
		try {
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);
	}

	@Test
	public void testCreateTwoWorkspacesWithSameName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// read the user from the previous test
		UserInfo userInfo = metaStore.readUser("anthony");
		assertEquals(1, userInfo.getWorkspaceIds().size());
		String workspaceId1 = userInfo.getWorkspaceIds().get(0);

		// read the workspace from the previous test
		String workspaceName1 = "Orion Content";
		WorkspaceInfo workspaceInfo1 = metaStore.readWorkspace(workspaceId1);
		assertNotNull(workspaceInfo1);
		assertEquals(workspaceName1, workspaceInfo1.getFullName());

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
	public void testCreateUserWithNoUserName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user and do not provide a userId
		UserInfo userInfo = new UserInfo();
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

		// delete the user
		metaStore.deleteUser(workspaceInfo.getUserId());
	}

	@Test(expected = CoreException.class)
	public void testCreateWorkspaceWithNoUserId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
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

		// read the user from the previous test
		UserInfo userInfo = metaStore.readUser("anthony");

		// create the workspace without specifying a workspace name.
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		fail("Should not be able to create the workspace without a workspace name.");
	}

	@Test
	public void testMoveProject() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// read the user from the previous test
		UserInfo userInfo = metaStore.readUser("anthony");

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

		// move the project by renaming the project by changing the projectName
		String movedProjectName = "Moved Orion Project";
		projectInfo.setFullName(movedProjectName);

		// update the project
		metaStore.updateProject(projectInfo);

		// read the project back again
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getFullName());
		assertNotNull(readProjectInfo);
		assertTrue(readProjectInfo.getFullName().equals(movedProjectName));

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testMoveUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		String userName = "anthony";
		userInfo.setUserName(userName);
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
			if (metaStore instanceof SimpleMetaStore) {
				String projectLocation = ((SimpleMetaStore) metaStore).getRootLocation().toURI().toString() + "/an/anthony/OrionContent/Orion%20Project";
				projectInfo.setContentLocation(new URI(projectLocation));
			} else {
				projectInfo.setContentLocation(new URI("file:/net/external/anthony/project"));
			}
		} catch (URISyntaxException e) {
			fail("URISyntaxException with the project content location.");
		}
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);

		// read the user back again
		userInfo = metaStore.readUser(userName);
		assertNotNull(userInfo);
		assertTrue(userInfo.getUserName().equals(userName));

		// move the user by changing the userName
		String newUserName = "ahunter";
		userInfo.setUserName(newUserName);

		// update the user
		metaStore.updateUser(userInfo);

		// read the user back again
		UserInfo readUserInfo = metaStore.readUser(newUserName);
		assertNotNull(readUserInfo);
		assertTrue(readUserInfo.getUserName().equals(newUserName));

		// read the moved workspace
		List<String> workspaceIds = userInfo.getWorkspaceIds();
		assertNotNull(workspaceIds);
		assertEquals(1, workspaceIds.size());
		String readWorkspaceId = workspaceIds.get(0);
		assertTrue(readWorkspaceId.startsWith(newUserName));
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(readWorkspaceId);
		assertNotNull(readWorkspaceInfo);
		assertEquals(1, readWorkspaceInfo.getProjectNames().size());

		// read the moved project
		String readProjectName = readWorkspaceInfo.getProjectNames().get(0);
		assertEquals(readProjectName, projectName);
		ProjectInfo readProjectInfo = metaStore.readProject(readWorkspaceId, readProjectName);
		assertNotNull(readProjectInfo);
		assertEquals(readWorkspaceInfo.getUniqueId(), readProjectInfo.getWorkspaceId());

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testReadProjectThatDoesNotExist() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

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
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName1 = "Orion Content";
		WorkspaceInfo workspaceInfo1 = new WorkspaceInfo();
		workspaceInfo1.setFullName(workspaceName1);
		workspaceInfo1.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo1);

		// read the workspace that does not exist
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace("anthony-Workspace77");
		assertNull(readWorkspaceInfo);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

}
