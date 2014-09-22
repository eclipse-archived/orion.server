/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.users;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.junit.Test;

/**
 * Test class to exercise {@link IOrionCredentialsService}. 
 * The goal is to hit all the methods used by the Orion Server.
 * 
 * @author Anthony Hunter
 */
public class OrionCredentialsServiceTest {

	@Test
	public void testDeleteUser() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "delete";
		String name = "Delete User";
		String email = "duser@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		orionCredentialsService.createUser(user);

		// delete the user
		boolean result = orionCredentialsService.deleteUser(user);
		assertTrue(result);

		// delete user pattern is to also delete the user in the metadata store
		try {
			OrionConfiguration.getMetaStore().deleteUser(userInfo.getUniqueId());
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// read the user back
		User readUser = orionCredentialsService.getUser(UserConstants.KEY_UID, userInfo.getUniqueId());
		assertNull(readUser);
	}

	@Test
	public void testUpdateUser() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "update";
		String name = "Update User";
		String email = "uuser@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		User newUser = orionCredentialsService.createUser(user);

		// update the user
		String newEmail = "updateduser@ca.ibm.com";
		newUser.setEmail(newEmail);
		IStatus status = orionCredentialsService.updateUser(newUser.getUid(), newUser);
		assertEquals(IStatus.OK, status.getCode());

		// read the user back
		User readUser = orionCredentialsService.getUser(UserConstants.KEY_UID, userInfo.getUniqueId());
		assertNotNull(readUser);
		assertEquals(login, readUser.getLogin());
		assertEquals(password, readUser.getPassword());
		assertEquals(name, readUser.getName());
		assertEquals(newEmail, readUser.getEmail());

		// update the user with a new property
		String property = "userProperty";
		String propertyValue = "value" + System.currentTimeMillis();
		newUser.getProperties().put(property, propertyValue);
		status = orionCredentialsService.updateUser(newUser.getUid(), newUser);
		assertEquals(IStatus.OK, status.getCode());

		// read the user back and confirm the property
		readUser = orionCredentialsService.getUser(UserConstants.KEY_UID, userInfo.getUniqueId());
		assertEquals(propertyValue, readUser.getProperties().get(property));

		// update the user with a change in the property
		propertyValue = "value" + System.currentTimeMillis();
		newUser.getProperties().put(property, propertyValue);
		status = orionCredentialsService.updateUser(newUser.getUid(), newUser);
		assertEquals(IStatus.OK, status.getCode());

		// read the user back and confirm the property
		readUser = orionCredentialsService.getUser(UserConstants.KEY_UID, userInfo.getUniqueId());
		assertEquals(propertyValue, readUser.getProperties().get(property));

		// update the user to delete the property
		newUser.getProperties().remove(property);
		status = orionCredentialsService.updateUser(newUser.getUid(), newUser);
		assertEquals(IStatus.OK, status.getCode());

		// read the user back and confirm the property
		readUser = orionCredentialsService.getUser(UserConstants.KEY_UID, userInfo.getUniqueId());
		// TODO: See Bug 433452 SimpleUserCredentialsService.updateUser does not delete properties 
		//assertNull(readUser.getProperties().get(property));
	}

	@Test
	public void testCreateUser() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "create";
		String name = "Create User";
		String email = "cuser@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		User createdUser = orionCredentialsService.createUser(user);
		assertNotNull(createdUser);
		assertEquals(login, createdUser.getLogin());
		assertEquals(password, createdUser.getPassword());
		assertEquals(name, createdUser.getName());
		assertEquals(email, createdUser.getEmail());
		assertEquals(userInfo.getUniqueId(), createdUser.getUid());
	}

	@Test
	public void testGetStoreName() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// check if we can create users
		String name = orionCredentialsService.getStoreName();
		assertEquals("Orion", name);
	}

	@Test
	public void testCanCreateUsers() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// check if we can create users
		boolean result = orionCredentialsService.canCreateUsers();
		assertTrue(result);
	}

	@Test
	public void testGetUsers() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "all";
		String name = "All User";
		String email = "auser@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		User createdUser = orionCredentialsService.createUser(user);
		assertNotNull(createdUser);

		// get all the users
		Collection<User> users = orionCredentialsService.getUsers();
		assertTrue(users.size() >= 1);
	}

	@Test
	public void testGetUser() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "get";
		String name = "Get User";
		String email = "guser@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		orionCredentialsService.createUser(user);

		// read the user back
		User getUser = orionCredentialsService.getUser(UserConstants.KEY_UID, userInfo.getUniqueId());
		assertNotNull(getUser);
		assertEquals(login, getUser.getLogin());
		assertEquals(password, getUser.getPassword());
		assertEquals(name, getUser.getName());
		assertEquals(email, getUser.getEmail());
		assertEquals(userInfo.getUniqueId(), getUser.getUid());

		// read the user back with email property
		User emailUser = orionCredentialsService.getUser(UserConstants.KEY_EMAIL, email);
		assertNotNull(emailUser);
		assertEquals(login, emailUser.getLogin());
		assertEquals(password, emailUser.getPassword());
		assertEquals(name, emailUser.getName());
		assertEquals(email, emailUser.getEmail());
		assertEquals(userInfo.getUniqueId(), emailUser.getUid());

		// read the user back with login property
		User loginUser = orionCredentialsService.getUser(UserConstants.KEY_LOGIN, login);
		assertNotNull(loginUser);
		assertEquals(login, loginUser.getLogin());
		assertEquals(password, loginUser.getPassword());
		assertEquals(name, loginUser.getName());
		assertEquals(email, loginUser.getEmail());
		assertEquals(userInfo.getUniqueId(), loginUser.getUid());
	}

	@Test
	public void testGetUsersByProperty() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "property";
		String name = "Property User";
		String email = "property_user@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		String openid = "openid";
		String openidProperty = "\n\nhttps://www.google.com/accounts/o8/id?id=HgTawnmfbTrOADLMe3BIhYTRfYMK99xMTiqACw";
		String id = "HgTawnmfbTrOADLMe3BIhYTRfYMK99xMTiqACw";
		user.getProperties().put(openid, openidProperty);
		orionCredentialsService.createUser(user);

		// read the user back with openid property
		Set<User> users = orionCredentialsService.getUsersByProperty(openid, ".*\\Q" + id + "\\E.*", true, false);
		assertNotNull(users);
		assertEquals(1, users.size());
		for (Iterator<User> i = users.iterator(); i.hasNext();) {
			User nextUser = i.next();
			assertEquals(login, nextUser.getLogin());
			assertEquals(password, nextUser.getPassword());
			assertEquals(name, nextUser.getName());
			assertEquals(email, nextUser.getEmail());
			assertEquals(userInfo.getUniqueId(), nextUser.getUid());
			assertEquals(openidProperty, nextUser.getProperty(openid));
		}

		// try reading a user back with a non existing openid property
		String noId = "HgTawTgTdTrOADLMeyyuuYTRfYMK99xMTiqACw";
		Set<User> noUsers = orionCredentialsService.getUsersByProperty(openid, ".*\\Q" + noId + "\\E.*", true, false);
		assertNotNull(noUsers);
		assertEquals(0, noUsers.size());
	}
}
