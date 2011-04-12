/*******************************************************************************
 * Copyright (c)  2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.tasks;

import static org.junit.Assert.assertEquals;

import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Runs all automated server tests for site configuration/hosting support.
 */
@RunWith(Suite.class)
@SuiteClasses({TaskInfoTest.class, TaskStoreTest.class})
public class AllTaskTests {

	/**
	 * Helper method for creating a sample task for testing purposes.
	 */
	public static TaskInfo createTestTask() {
		TaskInfo info = new TaskInfo("mytask");
		info.setMessage("THIS#)(&$^@)(ISA%20MESSAGE");
		info.setPercentComplete(50);
		return info;
	}

	public static void assertEqualTasks(TaskInfo task1, TaskInfo task2) {
		assertEquals(task1.getTaskId(), task2.getTaskId());
		assertEquals(task1.getMessage(), task2.getMessage());
		assertEquals(task1.getPercentComplete(), task2.getPercentComplete());
		assertEquals(task1.isRunning(), task2.isRunning());
	}
}
