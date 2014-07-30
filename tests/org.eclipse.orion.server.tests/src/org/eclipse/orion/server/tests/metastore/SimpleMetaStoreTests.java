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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
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
import org.eclipse.orion.server.tests.AbstractServerTest;
import org.junit.Test;

/**
 * Tests for the implementation of a {@link SimpleMetaStore}.
 *   
 * @author Anthony Hunter
 */
public class SimpleMetaStoreTests extends AbstractServerTest {

	public IMetaStore getMetaStore() {
		// use the currently configured metastore if it is an SimpleMetaStore 
		IMetaStore metaStore = null;
		try {
			metaStore = OrionConfiguration.getMetaStore();
		} catch (NullPointerException e) {
			// expected when the workbench is not running
		}
		if (metaStore instanceof SimpleMetaStore) {
			return metaStore;
		}
		fail("Orion Server is not running with a Simple Metadata Storage.");
		return null;
	}

	@Test
	public void testCreateProject() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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

		IFileStore defaultLocation = metaStore.getDefaultContentLocation(projectInfo);
		// Test that the project is linked
		assertFalse(defaultLocation == projectInfo.getProjectStore());
		// Test that no content folder is created
		assertFalse(defaultLocation.fetchInfo().exists());
	}

	@Test
	public void testCreateProjectNamedOrionContent() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create the project named OrionContent
		String projectName = "OrionContent";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		try {
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
			fail("URISyntaxException: " + e.getLocalizedMessage());
		}
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		try {
			metaStore.createProject(projectInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("cannot create a project named \"workspace\""));
		}
	}

	/**
	 * you are allowed to create a project named workspace with the latest SimpleMetaStore 
	 * @throws CoreException
	 */
	@Test
	public void testCreateProjectNamedWorkspace() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create the project named workspace
		String projectName = "workspace";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		try {
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getFullName());
		assertNotNull(readProjectInfo);
		assertEquals(readProjectInfo.getFullName(), projectInfo.getFullName());
	}

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
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create a folder under the user on the filesystem
		String projectName = "Orion Project";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		IFileStore projectFolder = metaStore.getDefaultContentLocation(projectInfo);
		assertFalse(projectFolder.fetchInfo().exists());
		projectFolder.mkdir(EFS.NONE, null);
		assertTrue(projectFolder.fetchInfo().exists() && projectFolder.fetchInfo().isDirectory());

		// read the project, project json will be created
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectName);
		assertNotNull(readProjectInfo);
	}

	@Test
	public void testCreateProjectWithAnInvalidWorkspaceId() {
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
		try {
			metaStore.createProject(projectInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("could not find workspace"));
		}
	}

	@Test
	public void testCreateProjectWithBarInName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create the project with bar in the project name.
		String projectName = "anthony | Orion Project";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		try {
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getFullName());
		assertNotNull(readProjectInfo);
		assertEquals(readProjectInfo.getFullName(), projectInfo.getFullName());
	}

	@Test
	public void testCreateProjectWithEmojiChactersInName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create the project with Emoji characters in the project name.
		// U+1F60A: SMILING FACE WITH SMILING EYES ("\ud83d\ude0a")
		// U+1F431: CAT FACE ("\ud83d\udc31")
		// U+1F435: MONKEY FACE ("\ud83d\udc35")
		String projectName = "Project \ud83d\ude0a\ud83d\udc31\ud83d\udc35";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		try {
			projectInfo.setContentLocation(new URI("file:/home/anthony/orion/project"));
		} catch (URISyntaxException e) {
			// should not get an exception here, simple URI
		}
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getFullName());
		assertNotNull(readProjectInfo);
		assertEquals(projectName, readProjectInfo.getFullName());
		assertEquals(readProjectInfo.getFullName(), projectInfo.getFullName());
	}

	@Test
	public void testCreateProjectWithNoWorkspaceId() {
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
		try {
			metaStore.createProject(projectInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("workspace id is null"));
		}
	}

	@Test
	public void testCreateProjectWithURLAsName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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
		try {
			metaStore.createProject(projectInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("could not create project"));
		}
	}

	/**
	 * You cannot create a second workspace for a user. See Bug 439735
	 * @throws CoreException
	 */
	@Test
	public void testCreateSecondWorkspace() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName1 = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo1 = new WorkspaceInfo();
		workspaceInfo1.setFullName(workspaceName1);
		workspaceInfo1.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo1);

		// create another workspace with the same workspace name
		String workspaceName2 = "Orion Sandbox";
		WorkspaceInfo workspaceInfo2 = new WorkspaceInfo();
		workspaceInfo2.setFullName(workspaceName2);
		workspaceInfo2.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo2);

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo2.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo1.getFullName());
		assertEquals(readWorkspaceInfo.getUniqueId(), workspaceInfo1.getUniqueId());
		assertEquals(readWorkspaceInfo.getUserId(), workspaceInfo1.getUserId());
	}

	@Test
	public void testCreateTwoWorkspacesWithSameName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName1 = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo1 = new WorkspaceInfo();
		workspaceInfo1.setFullName(workspaceName1);
		workspaceInfo1.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo1);

		// create another workspace with the same workspace name
		String workspaceName2 = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo2 = new WorkspaceInfo();
		workspaceInfo2.setFullName(workspaceName2);
		workspaceInfo2.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo2);

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());
		assertEquals(1, readUserInfo.getWorkspaceIds().size());
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo1.getUniqueId()));
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo2.getUniqueId()));

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo2.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo2.getFullName());
	}

	@Test
	public void testCreateUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
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
	public void testCreateUserWithNoUserName() {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user and do not provide a userId
		UserInfo userInfo = new UserInfo();
		userInfo.setFullName(testUserLogin);
		try {
			metaStore.createUser(userInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("could not create user"));
		}
	}

	@Test
	public void testCreateWorkspace() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);
	}

	@Test
	public void testCreateWorkspaceWithAnInvalidUserId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the workspace without specifying an existing userid.
		// the user with id '77' is created at the readUser() API.
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setUserId("77");
		workspaceInfo.setFullName(workspaceName);
		metaStore.createWorkspace(workspaceInfo);
	}

	@Test
	public void testCreateWorkspaceWithNoUserId() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace without specifying a userid.
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		try {
			metaStore.createWorkspace(workspaceInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("user id is null"));
		}
	}

	@Test
	public void testCreateWorkspaceWithNoWorkspaceName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace without specifying a workspace name.
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setUserId(userInfo.getUniqueId());
		try {
			metaStore.createWorkspace(workspaceInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("workspace name is null"));
		}
	}

	@Test
	public void testDeleteProject() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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
	}

	@Test
	public void testDeleteUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
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
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());
		assertEquals("Should be one workspace", 1, readUserInfo.getWorkspaceIds().size());
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo.getUniqueId()));

		// delete the workspace
		metaStore.deleteWorkspace(userInfo.getUniqueId(), workspaceInfo.getUniqueId());

		// read the user
		UserInfo readUserInfo2 = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo2);
		assertEquals(readUserInfo2.getUserName(), userInfo.getUserName());
		assertEquals("Should be zero workspaces", 0, readUserInfo2.getWorkspaceIds().size());
		assertFalse(readUserInfo2.getWorkspaceIds().contains(workspaceInfo.getUniqueId()));
	}

	@Test
	public void testGetDefaultContentLocation() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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

		IFileStore root = OrionConfiguration.getRootLocation();
		IFileStore projectHome = root.getChild("te/testGetDefaultContentLocation/OrionContent").getChild(projectName);
		String correctLocation = projectHome.toLocalFile(EFS.NONE, null).toString();

		assertEquals(correctLocation, location);
	}

	@Test
	public void testGetUserHome() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// get the user home
		IFileStore userHome = getMetaStore().getUserHome(userInfo.getUniqueId());
		String location = userHome.toLocalFile(EFS.NONE, null).toString();

		IFileStore root = OrionConfiguration.getRootLocation();
		IFileStore child = root.getChild("te/testGetUserHome");
		String correctLocation = child.toLocalFile(EFS.NONE, null).toString();

		assertEquals(correctLocation, location);
	}

	@Test
	public void testGetWorkspaceContentLocation() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// get the workspace content location
		IFileStore workspaceHome = getMetaStore().getWorkspaceContentLocation(workspaceInfo.getUniqueId());
		String location = workspaceHome.toLocalFile(EFS.NONE, null).toString();

		IFileStore root = OrionConfiguration.getRootLocation();
		IFileStore childLocation = root.getChild("te/testGetWorkspaceContentLocation/OrionContent");
		String correctLocation = childLocation.toLocalFile(EFS.NONE, null).toString();

		assertEquals(correctLocation, location);
	}

	@Test
	public void testMoveProject() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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

		// create a project directory and file
		IFileStore projectFolder = metaStore.getDefaultContentLocation(projectInfo);
		if (!projectFolder.fetchInfo().exists()) {
			projectFolder.mkdir(EFS.NONE, null);
		}
		assertTrue(projectFolder.fetchInfo().exists() && projectFolder.fetchInfo().isDirectory());
		String fileName = "file.html";
		IFileStore file = projectFolder.getChild(fileName);
		try {
			OutputStream outputStream = file.openOutputStream(EFS.NONE, null);
			outputStream.write("<!doctype html>".getBytes());
			outputStream.close();
		} catch (IOException e) {
			fail("Count not create a test file in the Orion Project:" + e.getLocalizedMessage());
		}
		assertTrue("the file in the project folder should exist.", file.fetchInfo().exists());

		// update the project with the content location
		projectInfo.setContentLocation(projectFolder.toLocalFile(EFS.NONE, null).toURI());
		metaStore.updateProject(projectInfo);

		// move the project by renaming the project by changing the projectName
		String movedProjectName = "Moved Orion Project";
		projectInfo.setFullName(movedProjectName);

		// update the project
		metaStore.updateProject(projectInfo);

		// read the project back again
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getFullName());
		assertNotNull(readProjectInfo);
		assertTrue(readProjectInfo.getFullName().equals(movedProjectName));

		// verify the local project has moved
		IFileStore workspaceFolder = metaStore.getWorkspaceContentLocation(workspaceInfo.getUniqueId());
		projectFolder = workspaceFolder.getChild(projectName);
		assertFalse("the original project folder should not exist.", projectFolder.fetchInfo().exists());
		projectFolder = workspaceFolder.getChild(movedProjectName);
		assertTrue("the new project folder should exist.", projectFolder.fetchInfo().exists() && projectFolder.fetchInfo().isDirectory());
		file = projectFolder.getChild(fileName);
		assertTrue("the file in the project folder should exist.", file.fetchInfo().exists());
		assertEquals("The ContentLocation should have been updated.", projectFolder.toLocalFile(EFS.NONE, null).toURI(), projectInfo.getContentLocation());

		// delete the project contents
		file.delete(EFS.NONE, null);
		assertFalse("the file in the project folder should not exist.", file.fetchInfo().exists());
	}

	@Test
	public void testMoveProjectLinked() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create the project
		String projectName = "Orion Project";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		IFileStore linkedFolder = metaStore.getUserHome(userInfo.getUniqueId()).getChild("Linked Project");
		projectInfo.setContentLocation(linkedFolder.toURI());

		metaStore.createProject(projectInfo);

		// create a project directory and file
		IFileStore projectFolder = projectInfo.getProjectStore();
		if (!projectFolder.fetchInfo().exists()) {
			projectFolder.mkdir(EFS.NONE, null);
		}
		assertTrue(projectFolder.fetchInfo().exists() && projectFolder.fetchInfo().isDirectory());
		String fileName = "file.html";
		IFileStore file = projectFolder.getChild(fileName);
		try {
			OutputStream outputStream = file.openOutputStream(EFS.NONE, null);
			outputStream.write("<!doctype html>".getBytes());
			outputStream.close();
		} catch (IOException e) {
			fail("Count not create a test file in the Orion Project:" + e.getLocalizedMessage());
		}
		assertTrue("the file in the project folder should exist.", file.fetchInfo().exists());

		// move the project by renaming the project by changing the projectName
		String movedProjectName = "Moved Orion Project";
		projectInfo.setFullName(movedProjectName);

		// update the project
		metaStore.updateProject(projectInfo);

		// read the project back again
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getFullName());
		assertNotNull(readProjectInfo);
		assertTrue(readProjectInfo.getFullName().equals(movedProjectName));

		// linked folder hasn't moved
		projectFolder = readProjectInfo.getProjectStore();
		assertTrue("the linked project folder should stay the same", projectFolder.equals(linkedFolder));
		assertTrue("the linked project folder should exist.", projectFolder.fetchInfo().exists());
		file = projectFolder.getChild(fileName);
		assertTrue("the file in the linked project folder should exist.", file.fetchInfo().exists());

		// delete the project contents
		file.delete(EFS.NONE, null);
		assertFalse("the file in the project folder should not exist.", file.fetchInfo().exists());
		// delete the linked project
		projectFolder.delete(EFS.NONE, null);
		assertFalse("the linked project should not exist.", projectFolder.fetchInfo().exists());
	}

	@Test
	public void testMoveProjectWithBarInProjectName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create the project with a bar in the project name
		String projectName = "anthony | Project";
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
		String movedProjectName = "anthony | Moved Orion Project";
		projectInfo.setFullName(movedProjectName);

		// update the project
		metaStore.updateProject(projectInfo);

		// read the project back again
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectInfo.getFullName());
		assertNotNull(readProjectInfo);
		assertTrue(readProjectInfo.getFullName().equals(movedProjectName));
	}

	@Test
	public void testMoveUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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

		// update the content location
		IFileStore projectLocation = metaStore.getDefaultContentLocation(projectInfo);
		projectInfo.setContentLocation(projectLocation.toURI());
		metaStore.updateProject(projectInfo);

		// read the user back again
		userInfo = metaStore.readUser(testUserLogin);
		assertNotNull(userInfo);
		assertTrue(userInfo.getUserName().equals(testUserLogin));

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
	}

	@Test
	public void testReadAllUsers() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo1 = new UserInfo();
		userInfo1.setUserName(testUserLogin);
		userInfo1.setFullName(testUserLogin);
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
	}

	@Test
	public void testReadCorruptedProjectJson() throws IOException, CoreException {
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
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// create a folder and project.json for the bad project on the filesystem
		String projectName = "Bad Project";
		File rootLocation = OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userInfo.getUniqueId(), workspaceName);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userInfo.getUniqueId());
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);

		assertTrue(SimpleMetaStoreUtil.createMetaFolder(workspaceMetaFolder, projectName));
		String corruptedProjectJson = "{\n\"OrionVersion\": 4,";
		File newFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, projectName);
		FileWriter fileWriter = new FileWriter(newFile);
		fileWriter.write(corruptedProjectJson);
		fileWriter.write("\n");
		fileWriter.flush();
		fileWriter.close();

		// read the project, should return null as the project is corrupted on disk
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectName);
		assertNull(readProjectInfo);
	}

	@Test
	public void testReadCorruptedUserJson() throws IOException, CoreException {
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

		// create a folder and user.json for the bad user on the filesystem
		String baduser = testUserLogin;
		File rootLocation = OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null);
		assertTrue(SimpleMetaStoreUtil.createMetaUserFolder(rootLocation, baduser));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, baduser);
		String corruptedUserJson = "{\n\"FullName\": \"Administrator\",\n\"OrionVersion\": 4,";
		File newFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, SimpleMetaStore.USER);
		FileWriter fileWriter = new FileWriter(newFile);
		fileWriter.write(corruptedUserJson);
		fileWriter.write("\n");
		fileWriter.flush();
		fileWriter.close();

		// read the user, should return null as the user is corrupted on disk
		UserInfo readUserInfo = metaStore.readUser(baduser);
		assertNull(readUserInfo);
	}

	@Test
	public void testReadCorruptedWorkspaceJson() throws IOException, CoreException {
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
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create a folder and workspace.json for the bad workspace on the filesystem
		File rootLocation = OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null);
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userInfo.getUniqueId(), workspaceName);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userInfo.getUniqueId());
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(userMetaFolder, encodedWorkspaceName));
		String corruptedWorkspaceJson = "{\n\"OrionVersion\": 4,";
		File newFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, encodedWorkspaceName);
		FileWriter fileWriter = new FileWriter(newFile);
		fileWriter.write(corruptedWorkspaceJson);
		fileWriter.write("\n");
		fileWriter.flush();
		fileWriter.close();

		// read the workspace, should return null as the workspace is corrupted on disk
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceId);
		assertNull(readWorkspaceInfo);
	}

	@Test
	public void testReadProject() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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
	}

	@Test
	public void testReadProjectThatDoesNotExist() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo1 = new WorkspaceInfo();
		workspaceInfo1.setFullName(workspaceName);
		workspaceInfo1.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo1);

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo1.getUniqueId(), "Project Zero");
		assertNull(readProjectInfo);
	}

	@Test
	public void testReadProjectWithWorkspaceThatDoesNotExist() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace id of a workspace that does not exist
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userInfo.getUniqueId(), workspaceName);

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceId, "Project Zero");
		assertNull(readProjectInfo);
	}

	@Test
	public void testReadUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// read the user
		UserInfo readUserInfo = metaStore.readUser(userInfo.getUniqueId());
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUniqueId(), userInfo.getUniqueId());
	}

	@Test
	public void testReadUserThatDoesNotExist() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// read the user, it will be created
		UserInfo userInfo = metaStore.readUser(testUserLogin);
		assertNotNull(userInfo);
		assertEquals("Unnamed User", userInfo.getFullName());
		assertEquals(testUserLogin, userInfo.getUserName());
	}

	@Test
	public void testReadWorkspace() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName1 = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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
		assertEquals(1, readUserInfo.getWorkspaceIds().size());
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo1.getUniqueId()));
		assertTrue(readUserInfo.getWorkspaceIds().contains(workspaceInfo2.getUniqueId()));

		// read the workspace
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceInfo2.getUniqueId());
		assertNotNull(readWorkspaceInfo);
		assertEquals(readWorkspaceInfo.getFullName(), workspaceInfo2.getFullName());
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
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName1 = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo1 = new WorkspaceInfo();
		workspaceInfo1.setFullName(workspaceName1);
		workspaceInfo1.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo1);

		// read the workspace that does not exist
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace("anthony-Workspace77");
		assertNull(readWorkspaceInfo);
	}

	@Test
	public void testUpdateProject() throws URISyntaxException, CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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
	}

	@Test
	public void testUpdateUser() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
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
		userInfo.setUserName(testUserLogin);
		userInfo.setFullName(testUserLogin);
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
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
	}

}