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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.junit.Test;

/**
 * Simple class to test out each of the methods on a {@link WorkspaceInfo}.
 * 
 * @author Anthony Hunter
 */
public class WorkspaceInfoTests {

	@Test
	public void testUniqueId() {
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		String id = "id";
		workspaceInfo.setUniqueId(id);
		assertEquals(id, workspaceInfo.getUniqueId());
	}

	@Test
	public void testFullName() {
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		String fullName = "Test Workspace";
		workspaceInfo.setFullName(fullName);
		assertEquals(fullName, workspaceInfo.getFullName());
	}

	@Test
	public void testProperties() {
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		String key1 = "key1";
		String value1 = "value1";
		String key2 = "key2";
		String value2 = "value2";
		assertNull(workspaceInfo.getProperty(key1));
		assertNull(workspaceInfo.getProperty(key2));
		assertEquals(0, workspaceInfo.getProperties().size());
		workspaceInfo.setProperty(key1, value1);
		assertEquals(value1, workspaceInfo.getProperty(key1));
		assertEquals(1, workspaceInfo.getProperties().size());
		workspaceInfo.setProperty(key2, value2);
		assertEquals(value1, workspaceInfo.getProperty(key1));
		assertEquals(value2, workspaceInfo.getProperty(key2));
		assertEquals(2, workspaceInfo.getProperties().size());
		workspaceInfo.setProperty(key2, null);
		assertNull(workspaceInfo.getProperty(key2));
		assertEquals(1, workspaceInfo.getProperties().size());
		workspaceInfo.setProperty(key1, null);
		assertNull(workspaceInfo.getProperty(key1));
		assertEquals(0, workspaceInfo.getProperties().size());
	}

	@Test
	public void testProjectNames() {
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		String id1 = "id1";
		String id2 = "id2";
		List<String> ids = new ArrayList<String>();
		assertEquals(0, workspaceInfo.getProjectNames().size());
		ids.add(id1);
		workspaceInfo.setProjectNames(ids);
		assertEquals(1, workspaceInfo.getProjectNames().size());
		assertTrue(workspaceInfo.getProjectNames().contains(id1));
		ids.add(id2);
		workspaceInfo.setProjectNames(ids);
		assertEquals(2, workspaceInfo.getProjectNames().size());
		assertTrue(workspaceInfo.getProjectNames().contains(id1));
		assertTrue(workspaceInfo.getProjectNames().contains(id2));
		ids.remove(id1);
		workspaceInfo.setProjectNames(ids);
		assertFalse(workspaceInfo.getProjectNames().contains(id1));
		assertEquals(1, workspaceInfo.getProjectNames().size());
		ids.remove(id2);
		workspaceInfo.setProjectNames(ids);
		assertFalse(workspaceInfo.getProjectNames().contains(id2));
		assertEquals(0, workspaceInfo.getProjectNames().size());
	}

	@Test
	public void testUserId() {
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		String userId = "id";
		workspaceInfo.setUserId(userId);
		assertEquals(userId, workspaceInfo.getUserId());
	}

}
