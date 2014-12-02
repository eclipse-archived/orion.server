/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.events;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.events.IFileChangeListener;
import org.eclipse.orion.server.core.events.IFileChangeNotificationService;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.tests.AbstractServerTest;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for the {@link FileChangeNotificationService}
 * 
 * @author Anthony Hunter
 */
public class FileChangeNotificationServiceTests extends AbstractServerTest {

	public class TestListener implements IFileChangeListener {
		private List<String> fileChangeNotifications = new ArrayList<String>();

		@Override
		public void directoryCreated(IFileStore directory, ProjectInfo projectInfo) {
			String directoryString = getFileString(directory);
			fileChangeNotifications.add("CREATED " + directoryString);
		}

		@Override
		public void directoryDeleted(IFileStore directory, ProjectInfo projectInfo) {
			String directoryString = getFileString(directory);
			fileChangeNotifications.add("DELETED " + directoryString);
		}

		@Override
		public void directoryUpdated(IFileStore directory, ProjectInfo projectInfo) {
			String directoryString = getFileString(directory);
			fileChangeNotifications.add("UPDATED " + directoryString);
		}

		@Override
		public void fileCreated(IFileStore file, ProjectInfo projectInfo) {
			String fileString = getFileString(file);
			fileChangeNotifications.add("CREATED " + fileString);
		}

		@Override
		public void fileDeleted(IFileStore file, ProjectInfo projectInfo) {
			String fileString = getFileString(file);
			fileChangeNotifications.add("DELETED " + fileString);
		}

		@Override
		public void fileUpdated(IFileStore file, ProjectInfo projectInfo) {
			String fileString = getFileString(file);
			fileChangeNotifications.add("UPDATED " + fileString);
		}

		public List<String> getFileChangeNotifications() {
			return fileChangeNotifications;
		}
	}

	public String getFileString(IFileStore file) {
		try {
			return file.toLocalFile(EFS.NONE, null).getAbsolutePath();
		} catch (CoreException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e);
		}
		return file.toString();
	}

	@Test
	public void testFileChangeNotificationService() throws Exception {
		// get the file change notification service reference
		BundleContext context = ServerTestsActivator.getContext();
		ServiceReference<IFileChangeNotificationService> serviceRef = context.getServiceReference(IFileChangeNotificationService.class);
		if (serviceRef == null) {
			fail("file change notification service is not available");
			return;
		}
		IFileChangeNotificationService fileChangeNotificationService = context.getService(serviceRef);
		if (fileChangeNotificationService == null) {
			fail("file change notification service is not available");
			return;
		}

		// register this test with the service
		TestListener listener = new TestListener();
		fileChangeNotificationService.addListener(listener);

		// get the MetaStore
		IMetaStore metaStore = OrionConfiguration.getMetaStore();

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
		String projectName = "project";
		ProjectInfo projectInfo = new ProjectInfo();
		projectInfo.setFullName(projectName);
		projectInfo.setWorkspaceId(workspaceInfo.getUniqueId());
		IFileStore projectFolder = metaStore.getDefaultContentLocation(projectInfo);
		projectInfo.setContentLocation(projectFolder.toURI());
		metaStore.createProject(projectInfo);
		assertTrue(projectFolder.fetchInfo().exists());
		assertTrue(projectFolder.fetchInfo().isDirectory());

		// create a directory in the project
		String folderName = "folder";
		IFileStore folder = projectFolder.getChild(folderName);
		assertFalse(folder.fetchInfo().exists());
		folder.mkdir(EFS.NONE, null);
		assertTrue(folder.fetchInfo().exists());
		assertTrue(folder.fetchInfo().isDirectory());
		String expectedFolderCreatedNotification = "CREATED " + getFileString(folder);

		// create a file in the folder in the project
		String fileName = "file.html";
		IFileStore file = folder.getChild(fileName);
		try {
			OutputStream outputStream = file.openOutputStream(EFS.NONE, null);
			outputStream.write("<!doctype html>\n".getBytes());
			outputStream.close();
		} catch (IOException e) {
			fail("Could not create a test file in the Orion Project:" + e.getLocalizedMessage());
		}
		assertTrue("the file in the project folder should exist.", file.fetchInfo().exists());
		String expectedFileCreatedNotification = "CREATED " + getFileString(file);

		// wait longer than the FileAlterationMonitor polling interval otherwise you miss some events
		Thread.sleep(2000L);

		// update the file in the folder in the project
		try {
			OutputStream outputStream = file.openOutputStream(EFS.NONE, null);
			outputStream.write("<!doctype html>\n<html>\n</html>/n".getBytes());
			outputStream.close();
		} catch (IOException e) {
			fail("Could not update the test file in the Orion Project:" + e.getLocalizedMessage());
		}
		assertTrue("the file in the project folder should exist.", file.fetchInfo().exists());
		String expectedFileUpdatedNotification = "UPDATED " + getFileString(file);

		// wait longer than the FileAlterationMonitor polling interval otherwise you miss some events
		Thread.sleep(2000L);

		// delete the file in the folder in the project
		file.delete(EFS.NONE, null);
		assertFalse("the file in the project folder should not exist.", file.fetchInfo().exists());
		String expectedFileDeletedNotification = "DELETED " + getFileString(file);

		// delete the folder in the project
		folder.delete(EFS.NONE, null);
		assertFalse("the project folder should not exist.", folder.fetchInfo().exists());
		String expectedFolderDeletedNotification = "DELETED " + getFileString(folder);

		// wait a maximum of fifteen seconds for the file change notification service to complete
		long fifteenSecondsFromNow = System.currentTimeMillis() + (1000L * 15);

		while (System.currentTimeMillis() < fifteenSecondsFromNow) {
			if (listener.getFileChangeNotifications().contains(expectedFileCreatedNotification) && //
					listener.getFileChangeNotifications().contains(expectedFolderCreatedNotification) && //
					listener.getFileChangeNotifications().contains(expectedFileUpdatedNotification) && //
					listener.getFileChangeNotifications().contains(expectedFileDeletedNotification) && //
					listener.getFileChangeNotifications().contains(expectedFolderDeletedNotification)) {
				// successfully received the create, update and delete notifications for the file and folder 
				return;
			}
			Thread.sleep(2000L);
		}
		fail("We did not get create notifications for the file and folder");
	}

}
