/*******************************************************************************
 * Copyright (c) 2014, 2016 IBM Corporation and others.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUserPropertyCache;
import org.junit.Test;

/**
 * Tests for a {@link SimpleMetaStoreUserPropertyCache}.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStoreUserPropertyCacheTests {

	@Test
	public void testAddUserProperty() throws CoreException {
		// create a user property cache for a property
		SimpleMetaStoreUserPropertyCache userPropertyCache = new SimpleMetaStoreUserPropertyCache();
		List<String> propertyKeys = new ArrayList<String>();
		propertyKeys.add("property");
		userPropertyCache.register(propertyKeys);
		assertTrue(userPropertyCache.isRegistered("property"));

		// add a property value and ensure it is in the cache
		userPropertyCache.add("property", "value", "user");
		assertEquals("user", userPropertyCache.readUserByProperty("property", "value", false, false));
	}

	@Test
	public void testDeleteUser() throws CoreException {
		// create a user property cache for a property
		SimpleMetaStoreUserPropertyCache userPropertyCache = new SimpleMetaStoreUserPropertyCache();
		List<String> propertyKeys = new ArrayList<String>();
		propertyKeys.add("property");
		userPropertyCache.register(propertyKeys);
		assertTrue(userPropertyCache.isRegistered("property"));

		// add a property value and ensure it is in the cache
		userPropertyCache.add("property", "value", "user");
		assertEquals("user", userPropertyCache.readUserByProperty("property", "value", false, false));

		// delete the user and ensure it is no longer in the cache
		userPropertyCache.deleteUser("user");
		assertNull(userPropertyCache.readUserByProperty("property", "value", false, false));
	}

	@Test
	public void testDeleteUserProperty() throws CoreException {
		// create a user property cache for a property
		SimpleMetaStoreUserPropertyCache userPropertyCache = new SimpleMetaStoreUserPropertyCache();
		List<String> propertyKeys = new ArrayList<String>();
		propertyKeys.add("property");
		userPropertyCache.register(propertyKeys);
		assertTrue(userPropertyCache.isRegistered("property"));

		// add a property value and ensure it is in the cache
		userPropertyCache.add("property", "value", "user");
		assertEquals("user", userPropertyCache.readUserByProperty("property", "value", false, false));

		// delete the property value and ensure it is no longer in the cache
		userPropertyCache.delete("property", "value", "user");
		assertNull(userPropertyCache.readUserByProperty("property", "value", false, false));
	}

	@Test
	public void testNoUserProperty() {
		SimpleMetaStoreUserPropertyCache userPropertyCache = new SimpleMetaStoreUserPropertyCache();
		userPropertyCache.add("property", "value", "user");
		try {
			assertNull(userPropertyCache.readUserByProperty("property", "value", false, false));
		} catch (CoreException e) {
			// we expect a core exception since the property is not registered.
			return;
		}
		fail("We expected a core exception since the property is not registered.");
	}

	@Test
	public void testUpdateUserProperty() throws CoreException {
		// create a user property cache for a property
		SimpleMetaStoreUserPropertyCache userPropertyCache = new SimpleMetaStoreUserPropertyCache();
		List<String> propertyKeys = new ArrayList<String>();
		propertyKeys.add("property");
		userPropertyCache.register(propertyKeys);
		assertTrue(userPropertyCache.isRegistered("property"));

		// add a property value and ensure it is in the cache
		userPropertyCache.add("property", "value", "user");
		assertEquals("user", userPropertyCache.readUserByProperty("property", "value", false, false));

		// update the property value and ensure it is updated in the cache
		userPropertyCache.add("property", "newvalue", "user");
		assertEquals("user", userPropertyCache.readUserByProperty("property", "newvalue", false, false));
		assertNull(userPropertyCache.readUserByProperty("property", "value", false, false));
	}
}
