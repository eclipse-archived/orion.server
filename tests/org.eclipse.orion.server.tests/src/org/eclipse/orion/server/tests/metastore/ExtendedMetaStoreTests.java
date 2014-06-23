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
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreV1;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreV2;
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
	public void testCreateProjectNamedOrionContent() throws CoreException {
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testCreateProjectNamedWorkspace() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		if (metaStore instanceof SimpleMetaStoreV2) {
			// you are allowed to create a project named workspace with SimpleMetaStoreV2 
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

		// create the project named workspace
		String projectName = "workspace";
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
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
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = "Orion Content";
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testCreateProjectWithEmojiChactersInName() throws CoreException {
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
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
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

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
		try {
			metaStore.createProject(projectInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("could not create project"));
		}

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testCreateTwoWorkspacesWithSameName() throws CoreException {
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

	@Test
	public void testCreateUserWithNoUserName() {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user and do not provide a userId
		UserInfo userInfo = new UserInfo();
		userInfo.setFullName("Anthony Hunter");
		try {
			metaStore.createUser(userInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("could not create user"));
		}
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

	@Test
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
		try {
			metaStore.createWorkspace(workspaceInfo);
		} catch (CoreException e) {
			// we expect to get a core exception here
			String message = e.getMessage();
			assertTrue(message.contains("user id is null"));
		}

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testCreateWorkspaceWithNoWorkspaceName() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
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

		IFileStore root = OrionConfiguration.getRootLocation();
		IFileStore projectHome = root.getChild("an/anthony/OrionContent").getChild(projectName);
		String correctLocation = projectHome.toLocalFile(EFS.NONE, null).toString();

		assertEquals(correctLocation, location);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
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

		IFileStore root = OrionConfiguration.getRootLocation();
		IFileStore child = root.getChild("an/anthony");
		String correctLocation = child.toLocalFile(EFS.NONE, null).toString();

		assertEquals(correctLocation, location);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
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

		IFileStore root = OrionConfiguration.getRootLocation();
		IFileStore childLocation = root.getChild("an/anthony/OrionContent");
		String correctLocation = childLocation.toLocalFile(EFS.NONE, null).toString();

		assertEquals(correctLocation, location);

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testMoveProjectLinked() throws CoreException {
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

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
	}

	@Test
	public void testMoveProjectWithBarInProjectName() throws CoreException {
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
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		metaStore.createProject(projectInfo);

		// update the content location
		IFileStore projectLocation = metaStore.getDefaultContentLocation(projectInfo);
		projectInfo.setContentLocation(projectLocation.toURI());
		metaStore.updateProject(projectInfo);

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
		userInfo.setUserName("badproject");
		userInfo.setFullName("Bad Project");
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = "Orion Content";
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
		File newFolder = SimpleMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectName);
		String corruptedProjectJson = "{\n\"OrionVersion\": 4,";
		File newFile;
		if (getMetaStore() instanceof SimpleMetaStoreV1) {
			newFile = SimpleMetaStoreUtil.retrieveMetaFile(workspaceMetaFolder, projectName);
		} else {
			// SimpleMetaStoreV2
			newFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, projectName);
		}
		FileWriter fileWriter = new FileWriter(newFile);
		fileWriter.write(corruptedProjectJson);
		fileWriter.write("\n");
		fileWriter.flush();
		fileWriter.close();

		// read the project, should return null as the project is corrupted on disk
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceInfo.getUniqueId(), projectName);
		assertNull(readProjectInfo);

		// delete a folder and project.json for the bad project on the filesystem 
		newFile.delete();
		newFolder.delete();

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());
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
		String baduser = "baduser";
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

		// delete the folder and user.json for the bad user on the filesystem
		newFile.delete();
		userMetaFolder.delete();
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
		userInfo.setUserName("badworkspace");
		userInfo.setFullName("Bad Workspace");
		metaStore.createUser(userInfo);

		// create a folder and workspace.json for the bad workspace on the filesystem
		File rootLocation = OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null);
		String workspaceName = "Orion Content";
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userInfo.getUniqueId(), workspaceName);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userInfo.getUniqueId());
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(userMetaFolder, encodedWorkspaceName));
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		String corruptedWorkspaceJson = "{\n\"OrionVersion\": 4,";
		File newFile;
		if (getMetaStore() instanceof SimpleMetaStoreV1) {
			newFile = SimpleMetaStoreUtil.retrieveMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE);
		} else {
			// SimpleMetaStoreV2
			newFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, encodedWorkspaceName);
		}
		FileWriter fileWriter = new FileWriter(newFile);
		fileWriter.write(corruptedWorkspaceJson);
		fileWriter.write("\n");
		fileWriter.flush();
		fileWriter.close();

		// read the workspace, should return null as the workspace is corrupted on disk
		WorkspaceInfo readWorkspaceInfo = metaStore.readWorkspace(workspaceId);
		assertNull(readWorkspaceInfo);

		// delete the folder and workspace.json for the bad workspace on the filesystem
		newFile.delete();
		workspaceMetaFolder.delete();

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
	public void testReadProjectWithWorkspaceThatDoesNotExist() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace id of a workspace that does not exist
		String workspaceName = "Orion Content";
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userInfo.getUniqueId(), workspaceName);

		// read the project
		ProjectInfo readProjectInfo = metaStore.readProject(workspaceId, "Project Zero");
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
