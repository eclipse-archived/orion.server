/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.useradmin;

import org.eclipse.orion.server.useradmin.*;

import org.eclipse.orion.internal.server.useradmin.xml.XmlUserAdmin;


import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import junit.framework.TestCase;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.junit.Test;
import org.osgi.framework.*;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.Role;

public class XmlUserAdminTest extends TestCase {

	private final URL entry = ServerTestsActivator.bundleContext.getBundle().getEntry("data/users.xml");
	private EclipseWebUserAdmin xua;

	@Override
	protected void setUp() throws Exception {
		BufferedWriter out = null;
		try {
			URL fileURL = FileLocator.toFileURL(entry);
			File file = new File(fileURL.toURI());

			out = new BufferedWriter(new FileWriter(file));
			out.write("<?xml version=\"1.0\"?>\n");
			out.write("<WebIdeUsers>\n");
			out.write("<roles>\n");
			out.write("<role name=\"admin\"/>\n");
			out.write("<role name=\"user\"/>\n");
			out.write("<role name=\"guest\"/>\n");
			out.write("</roles>\n");
			out.write("<users>\n");
			out.write("<user login=\"admin\" name=\"WebIde Admin\" password=\"admin\">\n");
			out.write("\t<roles>\n");
			out.write("\t\t<role name=\"admin\"/>\n");
			out.write("\t</roles>\n");
			out.write("</user>\n");
			out.write("<user login=\"test\" name=\"WebIde User\" password=\"test\">\n");
			out.write("\t<roles>\n");
			out.write("\t\t<role name=\"user\"/>\n");
			out.write("\t</roles>\n");
			out.write("</user>\n");
			out.write("</users>\n");
			out.write("</WebIdeUsers>");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (out != null)
				out.close();
		}

		xua = new XmlUserAdmin(entry);
	}

	private void reset() {
		xua = null;
		xua = new XmlUserAdmin(entry);
	}

	static public void assertEquals(User expected, org.osgi.service.useradmin.User actual) {
		if (expected == null && actual == null)
			return;
		if (expected != null && expected.equals(actual))
			return;
		assertNotNull(actual);
		assertEquals(expected.getName(), actual.getName());
		assertEquals(expected.getLogin(), actual.getCredentials().get("login"));
		assertEquals(expected.getName(), actual.getCredentials().get("name"));
		assertEquals(expected.getPassword(), actual.getCredentials().get("password"));
	}

	@Test
	public void testCreateRole() throws InvalidSyntaxException {
		Role managerRole = xua.createRole("manager", Role.ROLE);
		assertEquals("manager", managerRole.getName());
		assertEquals(Role.ROLE, managerRole.getType());
		assertEquals(4, xua.getRoles(null).length);

		reset();

		assertEquals(4, xua.getRoles(null).length);
	}

	@Test
	public void testRemoveRole() throws InvalidSyntaxException {
		assertEquals(false, xua.removeRole("manager"));

		xua.createRole("manager", Role.ROLE);
		assertEquals(4, xua.getRoles(null).length);

		assertTrue(xua.removeRole("manager"));
		assertEquals(3, xua.getRoles(null).length);

		assertTrue(xua.removeRole("guest"));
		assertEquals(2, xua.getRoles(null).length);

		reset();

		assertEquals(2, xua.getRoles(null).length);
	}

	@Test
	public void testGetRole() {
		Role userRole = xua.getRole("user");
		assertNotNull(userRole);

		Role adminRole = xua.getRole("admin");
		assertNotNull(adminRole);

		Role guestRole = xua.getRole("guest");
		assertNotNull(guestRole);

		Role managerRole = xua.getRole("manager");
		assertNull(managerRole);
	}

	@Test
	public void testGetRoles() throws InvalidSyntaxException {
		assertEquals(3, xua.getRoles(null).length);
	}

	@Test
	public void testGetRolesByFilter() throws InvalidSyntaxException {
		Filter filter = FrameworkUtil.createFilter("(name=user)");
		Role[] roles = xua.getRoles(filter.toString());
		assertEquals(1, roles.length);
		assertEquals("user", roles[0].getName());
	}

	@Test
	public void testGetUsers() {
		Collection<User> users = xua.getUsers();
		assertEquals(2, users.size());
	}

	@Test
	public void testCreateUser() {
		User user = new User("login", "name", "password");
		Role userRole = xua.getRole("user");
		user.addRole(userRole);
		User createdUser = xua.createUser(user);
		assertEquals(user, createdUser);
		assertEquals(3, xua.getUsers().size());

		org.osgi.service.useradmin.User user2 = xua.getUser("login", "login");
		assertEquals(user, user2);
		if (user2 instanceof User) {
			User u = (User) user2;
			assertTrue(u.getRoles().contains(userRole));
		}

		reset();

		org.osgi.service.useradmin.User user3 = xua.getUser("login", "login");
		assertEquals(user, user3);
		if (user3 instanceof User) {
			User u = (User) user2;
			assertTrue(u.getRoles().contains(userRole));
		}
	}

	@Test
	public void testCreateUserWithoutRole() {
		User user = new User("login", "name", "password");
		User createdUser = xua.createUser(user);
		assertEquals(user, createdUser);
		assertEquals(3, xua.getUsers().size());

		reset();

		org.osgi.service.useradmin.User user3 = xua.getUser("login", "login");
		assertEquals(user, user3);
	}

	@Test
	public void testCreateExistingUser() {
		User user = new User("test", "WebIde User", "test");
		User createdUser = xua.createUser(user);
		assertNull(createdUser);
		assertEquals(2, xua.getUsers().size());
	}

	@Test
	public void testDeleteUser() {
		assertFalse(xua.deleteUser(new User()));

		User user = new User("login", "name", "password");
		xua.createUser(user);
		assertEquals(3, xua.getUsers().size());

		assertTrue(xua.deleteUser(user));
		assertEquals(2, xua.getUsers().size());

		org.osgi.service.useradmin.User testUser = xua.getUser("login", "test");
		assertTrue(xua.deleteUser(testUser));
		assertEquals(1, xua.getUsers().size());

		reset();
		assertEquals(1, xua.getUsers().size());
	}

	@Test
	public void testUpdateUser() {
		User user = new User("test", "name2", "password2");
		assertFalse(xua.updateUser("dummy", user));
		// assertFalse(xua.updateUser("test", null));
		assertTrue(xua.updateUser("test", user));

		org.osgi.service.useradmin.User user2 = xua.getUser("login", "test");
		assertEquals("name2", user2.getCredentials().get("name"));
		assertEquals("password2", user2.getCredentials().get("password"));

		reset();

		org.osgi.service.useradmin.User user3 = xua.getUser("login", "test");
		assertEquals("name2", user3.getCredentials().get("name"));
		assertEquals("password2", user3.getCredentials().get("password"));
	}

	@Test
	public void testGetUser() {
		assertNotNull(xua.getUser("login", "test"));

		assertNull(xua.getUser("login", "dummy"));
	}

	@Test
	public void testGetUserByKey() {
		assertNotNull(xua.getUser("name", "WebIde User"));
		assertNotNull(xua.getUser("password", "test"));
	}

	@Test
	public void testGetUserWhereMoreThanOneMatching() {
		User user = new User("test", "name", "password");
		xua.createUser(user);
		// null when more than one matching users are found
		assertNull(xua.getUser("login", "test"));
	}

	@Test
	public void testGetAuthorization() {
		org.osgi.service.useradmin.User testUser = xua.getUser("login", "test");
		Authorization testAuthorization = xua.getAuthorization(testUser);
		assertNotNull(testAuthorization);
		assertTrue(testAuthorization instanceof WebIdeAuthorization);

		Authorization nullAuthorization = xua.getAuthorization(null);
		assertNotNull(nullAuthorization);
		assertTrue(nullAuthorization instanceof EmptyAuthorization);
	}

	@Test
	public void testGetAuthorizationForNonExistingUser() {
		User dummyUser = new User("login", "name", "password");
		Authorization dummyAuthorization = xua.getAuthorization(dummyUser);
		assertNotNull(dummyAuthorization);
		assertTrue(dummyAuthorization instanceof EmptyAuthorization);
	}

	@Override
	protected void tearDown() throws Exception {
		// URL fileURL = FileLocator.toFileURL(entry);
		// File file = new File(fileURL.toURI());
		// file.delete();
	}
}
