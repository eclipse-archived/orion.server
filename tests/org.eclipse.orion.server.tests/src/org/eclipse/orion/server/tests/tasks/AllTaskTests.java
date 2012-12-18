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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
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
	public static TaskInfo createTestTask(String userId) {
		TaskInfo info = new TaskInfo(userId, "mytask", true);
		info.done(new Status(IStatus.ERROR, "pluginid", "status message"));
		return info;
	}

	public static void assertEqualTasks(TaskInfo expected, TaskInfo actual) {
		assertEquals(expected.getUserId(), actual.getUserId());
		assertEquals(expected.getStatus(), actual.getStatus());
		assertEquals(expected.getLoaded(), actual.getLoaded());
		assertEquals(expected.isRunning(), actual.isRunning());
		assertEqualStatus(expected.getResult(), actual.getResult());
	}

	private static void assertEqualStatus(IStatus expected, IStatus actual) {
		assertEquals(expected.getSeverity(), actual.getSeverity());
		assertEquals(expected.getMessage(), actual.getMessage());
		assertEquals(expected.getCode(), actual.getCode());
		Throwable expectedException = expected.getException();
		Throwable actualException = actual.getException();
		if (expectedException == null) {
			assertEquals(expectedException, actualException);
		} else {
			assertEquals(expectedException.getMessage(), actualException.getMessage());
		}
	}
}
