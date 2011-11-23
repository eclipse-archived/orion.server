/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.tasks;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import org.eclipse.orion.internal.server.core.tasks.TaskStore;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link TaskStore}.
 */
public class TaskStoreTest extends TestCase {
	File tempDir;

	@Test
	public void testRead() {
		TaskStore store = new TaskStore(tempDir);
		String task = store.readTask("Userdoesnotexist", "Doesnotexist");
		assertNull(task);
	}

	@Test
	public void testRoundTrip() {
		TaskInfo task = AllTaskTests.createTestTask("test");
		TaskStore store = new TaskStore(tempDir);
		store.writeTask(task.getUserId(), task.getTaskId(), task.toJSON().toString());

		TaskInfo task2 = TaskInfo.fromJSON(store.readTask(task.getUserId(), task.getTaskId()));
		AllTaskTests.assertEqualTasks(task, task2);
	}

	@Before
	public void setUp() throws IOException {
		tempDir = new File(new File(System.getProperty("java.io.tmpdir")), "eclipse.TaskStoreTest");
		tearDown();
		tempDir.mkdir();
	}

	@After
	public void tearDown() {
		File[] children = tempDir.listFiles();
		if (children != null) {
			for (File child : children) {
				if (child.isDirectory()) {
					File[] directoryChildren = child.listFiles();
					for (File grandchild : directoryChildren) {
						grandchild.delete();
					}
				}
				child.delete();
			}
		}
		tempDir.delete();
	}
}
