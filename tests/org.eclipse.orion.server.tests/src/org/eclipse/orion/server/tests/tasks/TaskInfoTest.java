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

import junit.framework.TestCase;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.junit.Test;

/**
 * Tests for {@link TaskInfo}.
 */
public class TaskInfoTest extends TestCase {
	@Test
	public void testBadJSON() {
		TaskInfo task = AllTaskTests.createTestTask();
		String json = task.toJSON();
		json = json.replace('}', ')');
		assertNull(TaskInfo.fromJSON(json));

		//missing task id
		json = "{\"Message\":\"Hello\", \"TaskID\":\"foo\"}";
		assertNull(TaskInfo.fromJSON(json));
	}

	/**
	 * Tests the JSON representation of tasks.
	 */
	@Test
	public void testJSONRoundTrip() {
		TaskInfo info = AllTaskTests.createTestTask();
		TaskInfo task2 = TaskInfo.fromJSON(info.toJSON());
		AllTaskTests.assertEqualTasks(info, task2);
	}

	@Test
	public void testSetMessage() {
		TaskInfo info = new TaskInfo("mytask");
		assertEquals("", info.getMessage());
		info.setMessage("msg");
		assertEquals("msg", info.getMessage());
		info.setMessage(null);
		assertEquals("", info.getMessage());
	}
}
