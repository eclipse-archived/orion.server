/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.user.securestorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.useradmin.EmptyAuthorization;
import org.eclipse.orion.server.useradmin.OrionUserAdmin;
import org.eclipse.orion.server.useradmin.Role;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.WebIdeAuthorization;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Authorization;

/**
 * The implementation of User Service on Equinox Secure Storage
 */
public class SecureStorageUserAdmin extends OrionUserAdmin {

	static final String ORION_SERVER_NODE = "org.eclipse.orion.server";

	static final String USERS = "users";
	static final String USER_LOGIN = "login";
	static final String USER_NAME = "name";
	static final String USER_PASSWORD = "password";
	static final String USER_ROLES = "roles";
	static final String USER_ROLE_NAME = "name";

	public SecureStorageUserAdmin() {
	}

	public Role createRole(String name, int type) {
		throw new UnsupportedOperationException();
	}

	public boolean removeRole(String name) {
		throw new UnsupportedOperationException();
	}

	public Role getRole(String name) {
		throw new UnsupportedOperationException();
	}

	public Role[] getRoles(String filter) throws InvalidSyntaxException {
		throw new UnsupportedOperationException();
	}

	public Collection<User> getUsers() {
		ISecurePreferences prefs = SecurePreferencesFactory.getDefault().node(ORION_SERVER_NODE);
		if (!prefs.nodeExists(USERS)) {
			return null;
		}
		ISecurePreferences usersPrefs = prefs.node(USERS);
		Collection<User> users = null;
		try {
			for (String childName : usersPrefs.childrenNames()) {
				if (users == null)
					users = new ArrayList<User>();
				ISecurePreferences userPrefs = usersPrefs.node(childName);
				users.add(new User(childName, userPrefs.get(USER_NAME, ""), "" /* don't expose the password */));

			}
			return users;
		} catch (StorageException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not get the user", e));
		}
		return null;
	}

	public User getUser(String key, String value) {
		// TODO currently searching only by login, all other searches return nothing
		if (key.equals(USER_LOGIN)) {
			ISecurePreferences prefs = SecurePreferencesFactory.getDefault().node(ORION_SERVER_NODE);
			if (!prefs.nodeExists(USERS + "/" + value)) {
				return null;
			}
			ISecurePreferences userPrefs = prefs.node(USERS + "/" + value);
			try {
				return new User(value, userPrefs.get(USER_NAME, ""), userPrefs.get(USER_PASSWORD, ""));
			} catch (StorageException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not get the user", e));
			}
		}
		return null;
	}

	public User createUser(User user) {
		ISecurePreferences prefs = SecurePreferencesFactory.getDefault().node(ORION_SERVER_NODE);
		if (prefs.nodeExists(USERS + "/" + user.getLogin())) {
			return null;
		}
		try {
			internalCreateOrUpdateUser(prefs.node(USERS + "/" + user.getLogin()), user);
			return user;
		} catch (StorageException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not create the user", e));
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not create the user", e));
		}
		return null;
	}

	public boolean updateUser(String oldLogin, User user) {
		ISecurePreferences prefs = SecurePreferencesFactory.getDefault().node(ORION_SERVER_NODE);
		if (!prefs.nodeExists(USERS + "/" + user.getLogin())) {
			return false;
		}
		try {
			internalCreateOrUpdateUser(prefs.node(USERS + "/" + user.getLogin()), user);
			return true;
		} catch (StorageException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not update the user", e));
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not update the user", e));
		}
		return false;
	}

	private User internalCreateOrUpdateUser(ISecurePreferences userPrefs, User user) throws StorageException, IOException {
		userPrefs.put(USER_NAME, user.getName(), false);
		userPrefs.put(USER_PASSWORD, user.getPassword(), true);
		if (user.getRoles().size() > 0) {
			ISecurePreferences rolesPrefs = userPrefs.node(USER_ROLES);
			for (Iterator i = user.getRoles().iterator(); i.hasNext();) {
				Role role = (Role) i.next();
				rolesPrefs.node(role.getName());
			}
		}
		userPrefs.flush();
		return user;
	}

	public boolean deleteUser(User user) {
		ISecurePreferences prefs = SecurePreferencesFactory.getDefault().node(ORION_SERVER_NODE);
		if (!prefs.nodeExists(USERS + "/" + user.getLogin())) {
			return false;
		}
		ISecurePreferences userPrefs = prefs.node(USERS + "/" + user.getLogin());
		userPrefs.removeNode();
		try {
			userPrefs.flush();
			return true;
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not delete the user", e));
		}
		return false;
	}

	public Authorization getAuthorization(org.osgi.service.useradmin.User user) {
		if (user instanceof User) {
			return new WebIdeAuthorization((User) user);
		}
		return new EmptyAuthorization();
	}

	@Override
	public boolean canCreateUsers() {
		return true;
	}

	@Override
	public String getStoreName() {
		return "Orion Secure Storage";
	}
}
