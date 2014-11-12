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
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreMigration;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.json.JSONObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests to ensure that a SimpleMetaStore can be migrated successfully concurrently from separate threads. Only one thread can 
 * do the migration, the others should be blocked. If one thread completes the migration the other threads should figure out that 
 * migration no longer needs to be completed. See Bugzilla 451012.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStoreLiveMigrationConcurrencyTests extends AbstractSimpleMetaStoreMigrationTests {

	protected static int THREAD_COUNT = 4;

	public final static String USER_NAME = "concurrency";

	protected Thread readUserThread(int number) {
		Runnable runnable = new Runnable() {
			public void run() {
				try {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					IMetaStore metaStore = getMetaStore();

					// read the user
					UserInfo userInfo = metaStore.readUser(USER_NAME);
					if (userInfo == null) {
						logger.debug("Meta File Error, could not read user " + USER_NAME);
					}
				} catch (CoreException e) {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					logger.debug("Meta File Error, cannot read JSON file from disk, reason: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
			}
		};

		Thread thread = new Thread(runnable, "LiveMigrationConcurrencyThread-" + number);
		thread.start();
		return thread;
	}

	protected IMetaStore getMetaStore() {
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

	/**
	 * Tests creating properties in the metadata store in multiple concurrently running threads.
	 * @throws Exception 
	 */
	@Test
	public void testSimpleMetaStoreCreatePropertyConcurrency() throws Exception {
		// create the user on disk at the previous version of the metadata
		testUserId = USER_NAME;
		int version = SimpleMetaStoreMigration.VERSION7;
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;

		// create metadata on disk
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, workspaceName);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		for (int i = 0; i < 50; i++) {
			projectNames.add("Project" + i);
		}

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, workspaceName, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, workspaceName);
		for (int i = 0; i < 50; i++) {
			File defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(i));
			JSONObject newProjectJSON = createProjectJson(version, testUserId, workspaceName, projectNames.get(i), defaultContentLocation);
			createProjectMetaData(version, newProjectJSON, testUserId, workspaceName, projectNames.get(i));
		}

		// add properties to the user in multiple threads
		Thread threads[] = new Thread[THREAD_COUNT];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = readUserThread(i);
		}

		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				// just continue
			}
		}
	}
}
