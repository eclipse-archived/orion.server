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
package org.eclipse.orion.internal.server.useradmin.xml;

import org.eclipse.orion.server.useradmin.*;

import org.eclipse.orion.server.core.LogHelper;

import java.net.URL;
import java.util.Collection;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;

/**
 * This class is responsible for read/write access to user and roles stored in
 * an XML file.
 */
public class XmlUserAdmin extends OrionUserAdmin {

	private Map<String, org.eclipse.orion.server.useradmin.User> usersMap;
	private Map<String, org.eclipse.orion.server.useradmin.Role> rolesMap;
	private UsersLoader usersLoader;

	public XmlUserAdmin(URL entry) {
		try {
			usersLoader = new UsersLoader(entry);
			usersLoader.buildMaps();
			usersMap = usersLoader.getUsersMap();
			rolesMap = usersLoader.getRolesMap();
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USERADMIN_XML, 1, "An error occured when building user maps", e));
		}
	}

	public Role createRole(String name, int type) {
		org.eclipse.orion.server.useradmin.Role role = new org.eclipse.orion.server.useradmin.Role(name, type);
		rolesMap.put(name, role);
		usersLoader.createRole(name);
		return role;
	}

	public boolean removeRole(String name) {
		if (rolesMap.containsKey(name)) {
			rolesMap.remove(name);
			return usersLoader.removeRole(name);
		}
		return false;
	}

	public Role getRole(String name) {
		if (rolesMap.containsKey(name)) {
			return rolesMap.get(name);
		}
		return usersMap.get(name); // a user is also a role
	}

	public Role[] getRoles(String filter) throws InvalidSyntaxException {
		// TODO implement search by filter
		return rolesMap.values().toArray(new Role[0]);
	}

	public Collection<org.eclipse.orion.server.useradmin.User> getUsers() {
		return usersMap.values();
	}

	public org.eclipse.orion.server.useradmin.User createUser(org.eclipse.orion.server.useradmin.User user) {
		if (usersMap.containsKey(user.getLogin())) {
			return null;
		}
		usersMap.put(user.getLogin(), user);
		usersLoader.createUser(user);
		return user;
	}

	public boolean deleteUser(User user) {
		if (usersMap.containsKey(user.getCredentials().get(UsersLoader.USER_LOGIN))) {
			usersMap.remove(user.getCredentials().get(UsersLoader.USER_LOGIN));
			return usersLoader.deleteUser(user);
		}
		return false;
	}

	public boolean updateUser(String oldLogin, org.eclipse.orion.server.useradmin.User user) {
		if (usersMap.containsKey(oldLogin)) {
			usersMap.remove(oldLogin);
			usersMap.put(user.getLogin(), user);
			return usersLoader.updateUser(oldLogin, user);
		}
		return false;
	}

	public User getUser(String key, String value) {
		if (key.equals(UsersLoader.USER_LOGIN)) {
			return usersMap.get(value);
		}
		return null; // TODO currently searching only by login, all other
						// searches return nothing
	}

	public Authorization getAuthorization(User user) {
		if (user instanceof org.eclipse.orion.server.useradmin.User) {
			return new WebIdeAuthorization((org.eclipse.orion.server.useradmin.User) user);
		}
		return new EmptyAuthorization();
	}

	@Override
	public boolean canCreateUsers() {
		return true;
	}

	@Override
	public String getStoreName() {
		return "EclipseWeb";
	}

}
