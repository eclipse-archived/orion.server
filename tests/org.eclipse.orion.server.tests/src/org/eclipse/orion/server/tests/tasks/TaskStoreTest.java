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
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.core.tasks.TaskDescription;
import org.eclipse.orion.internal.server.core.tasks.TaskService;
import org.eclipse.orion.internal.server.core.tasks.TaskStore;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.tasks.CorruptedTaskException;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import junit.framework.TestCase;

/**
 * Tests for {@link TaskStore}.
 */
public class TaskStoreTest extends TestCase {
	File tempDir;
	IMetaStore metaStore;

	@Test
	public void testRead() {
		TaskStore store = new TaskStore(tempDir, metaStore);
		String task = store.readTask(new TaskDescription("Userdoesnotexist", "Doesnotexist", true));
		assertNull(task);
	}

	@Test
	public void testRoundTrip() throws CorruptedTaskException {
		TaskInfo task = AllTaskTests.createTestTask("test");
		TaskStore store = new TaskStore(tempDir ,metaStore);
		store.writeTask(new TaskDescription(task.getUserId(), task.getId(), true), task.toJSON().toString());

		TaskInfo task2 = TaskInfo.fromJSON(new TaskDescription(task.getUserId(), task.getId(), true), store.readTask(new TaskDescription(task.getUserId(), task.getId(), true)));
		AllTaskTests.assertEqualTasks(task, task2);
	}

	@Test
	public void testDeleteTask() {
		TaskInfo task = AllTaskTests.createTestTask("test");
		task.done(Status.OK_STATUS);
		TaskStore store = new TaskStore(tempDir, metaStore);
		store.writeTask(new TaskDescription(task.getUserId(), task.getId(), true), task.toJSON().toString());
		assertNotNull(store.readTask(new TaskDescription(task.getUserId(), task.getId(), true)));
		assertTrue(store.removeTask(new TaskDescription(task.getUserId(), task.getId(), true)));
		assertNull(store.readTask(new TaskDescription(task.getUserId(), task.getId(), true)));
	}

	@Test
	public void readAllTasksTest() {
		TaskInfo task1 = new TaskInfo("test", "taskid1", true);
		task1.done(Status.OK_STATUS);
		TaskInfo task2 = new TaskInfo("test", "taskid2", true);
		task2.done(Status.OK_STATUS);
		TaskStore store = new TaskStore(tempDir, metaStore);
		store.writeTask(new TaskDescription("test", task1.getId(), true), task1.toJSON().toString());
		assertEquals(1, store.readAllTasks("test"));
		store.writeTask(new TaskDescription("test", task2.getId(), true), task2.toJSON().toString());
		assertEquals(2, store.readAllTasks("test"));
		store.removeTask(new TaskDescription("test", task1.getId(), true));
		assertEquals(1, store.readAllTasks("test"));
	}

	@Before
	public void setUp() throws IOException {
		tempDir = new File(new File(System.getProperty("java.io.tmpdir")), "eclipse.TaskStoreTest");
		tearDown();
		tempDir.mkdir();
		metaStore = OrionConfiguration.getMetaStore();
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

	private class TaskStoreTestJob extends Job {

		private ITaskService taskService;
		private TaskInfo[] taskInfoTable;
		private int jobNum;

		public TaskStoreTestJob(ITaskService taskService, TaskInfo[] taskInfoTable, int jobNum) {
			super("Test task job number " + jobNum);
			this.taskService = taskService;
			this.taskInfoTable = taskInfoTable;
			this.jobNum = jobNum;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			taskInfoTable[jobNum] = taskService.createTask("test", true);
			return Status.OK_STATUS;
		}
	}

	@Test
	public void testUniqueTaskInfo() throws InterruptedException { //see Bug 370729
		final ITaskService taskService = new TaskService(new Path(tempDir.getAbsolutePath()), metaStore);
		int numberOfChecks = 100;
		Job[] jobs = new Job[numberOfChecks];
		final TaskInfo[] taskInfos = new TaskInfo[numberOfChecks];
		for (int i = 0; i < numberOfChecks; i++) {
			jobs[i] = new TaskStoreTestJob(taskService, taskInfos, i);
		}
		for (int i = 0; i < numberOfChecks; i++) {
			jobs[i].schedule(); //start jobs fast, so they run parallelly
		}
		for (int i = 0; i < numberOfChecks; i++) {
			jobs[i].join(); //wait for all jobs to finish
		}
		Set<String> values = new HashSet<String>();
		for (int i = 0; i < numberOfChecks; i++) { //check if task ids are unique 
			String taskInfoId = taskInfos[i].getId();
			assertFalse("Bad value number " + i + " value: " + taskInfoId, values.contains(taskInfoId));
			values.add(taskInfoId);
		}
	}
}
