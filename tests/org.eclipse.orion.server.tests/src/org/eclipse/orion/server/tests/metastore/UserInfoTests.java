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

import org.eclipse.orion.server.core.metastore.UserInfo;
import org.junit.Test;

/**
 * Simple class to test out each of the methods on a {@link UserInfo}.
 * 
 * @author Anthony Hunter
 */
public class UserInfoTests {

	@Test
	public void testUniqueId() {
		UserInfo userInfo = new UserInfo();
		String id = "id";
		userInfo.setUniqueId(id);
		assertEquals(id, userInfo.getUniqueId());
	}

	@Test
	public void testFullName() {
		UserInfo userInfo = new UserInfo();
		String fullName = "Test User";
		userInfo.setFullName(fullName);
		assertEquals(fullName, userInfo.getFullName());
	}

	@Test
	public void testUserName() {
		UserInfo userInfo = new UserInfo();
		String userName = "test";
		userInfo.setUserName(userName);
		assertEquals(userName, userInfo.getUserName());
	}

	@Test
	public void testProperties() {
		UserInfo userInfo = new UserInfo();
		String key1 = "key1";
		String value1 = "value1";
		String key2 = "key2";
		String value2 = "value2";
		assertNull(userInfo.getProperty(key1));
		assertNull(userInfo.getProperty(key2));
		assertEquals(0, userInfo.getProperties().size());
		userInfo.setProperty(key1, value1);
		assertEquals(value1, userInfo.getProperty(key1));
		assertEquals(1, userInfo.getProperties().size());
		userInfo.setProperty(key2, value2);
		assertEquals(value1, userInfo.getProperty(key1));
		assertEquals(value2, userInfo.getProperty(key2));
		assertEquals(2, userInfo.getProperties().size());
		userInfo.setProperty(key2, null);
		assertNull(userInfo.getProperty(key2));
		assertEquals(1, userInfo.getProperties().size());
		userInfo.setProperty(key1, null);
		assertNull(userInfo.getProperty(key1));
		assertEquals(0, userInfo.getProperties().size());
	}

	@Test
	public void testWorkspaceIds() {
		UserInfo userInfo = new UserInfo();
		String id1 = "id1";
		String id2 = "id2";
		List<String> ids = new ArrayList<String>();
		assertEquals(0, userInfo.getWorkspaceIds().size());
		ids.add(id1);
		userInfo.setWorkspaceIds(ids);
		assertEquals(1, userInfo.getWorkspaceIds().size());
		assertTrue(userInfo.getWorkspaceIds().contains(id1));
		ids.add(id2);
		userInfo.setWorkspaceIds(ids);
		assertEquals(2, userInfo.getWorkspaceIds().size());
		assertTrue(userInfo.getWorkspaceIds().contains(id1));
		assertTrue(userInfo.getWorkspaceIds().contains(id2));
		ids.remove(id1);
		userInfo.setWorkspaceIds(ids);
		assertFalse(userInfo.getWorkspaceIds().contains(id1));
		assertEquals(1, userInfo.getWorkspaceIds().size());
		ids.remove(id2);
		userInfo.setWorkspaceIds(ids);
		assertFalse(userInfo.getWorkspaceIds().contains(id2));
		assertEquals(0, userInfo.getWorkspaceIds().size());
	}
}
