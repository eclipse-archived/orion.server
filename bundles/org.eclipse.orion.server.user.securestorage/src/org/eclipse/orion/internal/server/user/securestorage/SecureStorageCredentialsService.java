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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.crypto.spec.PBEKeySpec;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.equinox.security.storage.provider.IProviderHints;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.eclipse.orion.server.useradmin.EmptyAuthorization;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.Role;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.WebIdeAuthorization;
import org.eclipse.orion.server.useradmin.servlets.UserServlet;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.useradmin.Authorization;

/**
 * The implementation of User Service on Equinox Secure Storage
 */
public class SecureStorageCredentialsService implements IOrionCredentialsService {

	static final String ORION_SERVER_NODE = "org.eclipse.orion.server"; //$NON-NLS-1$

	static final String USERS = "users"; //$NON-NLS-1$
	static final String USER_LOGIN = "login"; //$NON-NLS-1$
	static final String USER_UID = "uid"; //$NON-NLS-1$
	static final String USER_NAME = "name"; //$NON-NLS-1$
	static final String USER_PASSWORD = "password"; //$NON-NLS-1$
	static final String USER_ROLES = "roles"; //$NON-NLS-1$
	static final String USER_ROLE_NAME = "name"; //$NON-NLS-1$
	static final String USER_PROPERTIES = "properties"; //$NON-NLS-1$

	static final String ADMIN_LOGIN_VALUE = "admin"; //$NON-NLS-1$
	static final String ADMIN_NAME_VALUE = "Administrator"; //$NON-NLS-1$

	static final String ANONYMOUS_LOGIN_VALUE = "anonymous"; //$NON-NLS-1$
	static final String ANONYMOUS_NAME_VALUE = "Anonymous"; //$NON-NLS-1$

	private static final Base64Counter userCounter = new Base64Counter();

	private ISecurePreferences storage;
	private Map<String, Role> roles = new HashMap<String, Role>();

	public SecureStorageCredentialsService() {
		initSecurePreferences();
		initStorage();
	}

	private String nextUserId() {
		synchronized (userCounter) {
			String candidate;
			do {
				candidate = userCounter.toString();
				userCounter.increment();
			} while (findNode(storage, candidate) != null);
			return candidate;
		}
	}

	private void initStorage() {
		// initialize the admin account
		String adminDefaultPassword = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_ADMIN_DEFAULT_PASSWORD);
		if (adminDefaultPassword != null && getUser(USER_UID, ADMIN_LOGIN_VALUE) == null) {
			createUser(new User(ADMIN_LOGIN_VALUE, ADMIN_LOGIN_VALUE, ADMIN_NAME_VALUE, adminDefaultPassword));
		}

		// TODO: see bug 335699, the user storage should not configure authorization rules
		// it should add Admin role, which will be used during authorization process
		try {
			AuthorizationService.addUserRight(ADMIN_LOGIN_VALUE, UserServlet.USERS_URI);
			AuthorizationService.addUserRight(ADMIN_LOGIN_VALUE, UserServlet.USERS_URI + "/*"); //$NON-NLS-1$
		} catch (CoreException e) {
			LogHelper.log(e);
		}
		//add default roles
		for (String role : new String[] {"admin", "user", "quest"}) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			roles.put(role, new Role(role, org.osgi.service.useradmin.Role.ROLE));
	}

	private void initSecurePreferences() {
		//try to create our own secure storage under the platform instance location
		URL location = getStorageLocation();
		if (location != null) {
			Map<String, Object> options = new HashMap<String, Object>();
			options.put(IProviderHints.PROMPT_USER, Boolean.FALSE);
			String password = System.getProperty(Activator.ORION_STORAGE_PASSWORD, ""); //$NON-NLS-1$
			options.put(IProviderHints.DEFAULT_PASSWORD, new PBEKeySpec(password.toCharArray()));
			try {
				storage = SecurePreferencesFactory.open(location, options);
			} catch (IOException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, "Error initializing user storage location", e)); //$NON-NLS-1$
			}
		} else {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_USER_SECURESTORAGE, "No instance location set. Storing user data in user home directory")); //$NON-NLS-1$
		}
		//fall back to default secure storage location if we failed to create our own
		if (storage == null)
			storage = SecurePreferencesFactory.getDefault().node(ORION_SERVER_NODE);
	}

	/**
	 * Returns the location for user data to be stored.
	 */
	private URL getStorageLocation() {
		BundleContext context = Activator.getContext();
		Collection<ServiceReference<Location>> refs;
		try {
			refs = context.getServiceReferences(Location.class, Location.INSTANCE_FILTER);
		} catch (InvalidSyntaxException e) {
			// we know the instance location filter syntax is valid
			throw new RuntimeException(e);
		}
		if (refs.isEmpty())
			return null;
		ServiceReference<Location> ref = refs.iterator().next();
		Location location = context.getService(ref);
		try {
			try {
				if (location != null)
					return location.getDataArea(Activator.PI_USER_SECURESTORAGE + "/user_store"); //$NON-NLS-1$
			} catch (IOException e) {
				LogHelper.log(e);
			}
		} finally {
			context.ungetService(ref);
		}
		//return null if we are unable to determine instance location.
		return null;
	}

	public Role createRole(String name, int type) {
		throw new UnsupportedOperationException();
	}

	public IStatus removeRole(String name) {
		return new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, "Removing roles not supported");
	}

	public Role getRole(String name) {
		return roles.get(name);
	}

	public Role[] getRoles(String filter) throws InvalidSyntaxException {
		return (Role[]) roles.values().toArray();
	}

	public Collection<User> getUsers() {
		if (!storage.nodeExists(USERS)) {
			return null;
		}
		ISecurePreferences usersPrefs = storage.node(USERS);
		Collection<User> users = null;
		for (String childName : usersPrefs.childrenNames()) {
			if (users == null)
				users = new ArrayList<User>();
			ISecurePreferences userPrefs = usersPrefs.node(childName);
			try {
				User user = new User(childName, userPrefs.get(USER_LOGIN, childName), userPrefs.get(USER_NAME, ""), userPrefs.get(USER_PASSWORD, null) == null ? null : "" /* don't expose the password */); //$NON-NLS-1$ //$NON-NLS-2$

				for (String property : userPrefs.node(USER_PROPERTIES).keys()) {
					user.addProperty(property, userPrefs.node(USER_PROPERTIES).get(property, null));
				}

				for (String roleName : userPrefs.node(USER_ROLES).childrenNames()) {
					user.addRole(getRole(roleName));
				}
				users.add(user);
			} catch (StorageException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Error loading user: " + childName, e)); //$NON-NLS-1$
			}
		}
		return users;
	}

	public User getUser(String key, String value) {
		// TODO currently searching only by uid and login, all other searches return nothing
		if (key.equals(USER_LOGIN)) {

			try {
				ISecurePreferences node = findNodeByLoginIgnoreCase(storage, value);
				return formUser(node);
			} catch (StorageException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not get user: " + value, e)); //$NON-NLS-1$
			}
		} else if (key.equals(USER_UID)) {
			ISecurePreferences node = findNode(storage, value);
			return formUser(node);
		}
		return null;
	}

	public Set<User> getUsersByProperty(String key, String value, boolean regExp) {
		Set<User> ret = new HashSet<User>();
		ISecurePreferences usersPref = storage.node(USERS);
		for (String uid : usersPref.childrenNames()) {
			ISecurePreferences userNode = usersPref.node(uid);
			ISecurePreferences propsNode = userNode.node(USER_PROPERTIES);
			if (propsNode == null) {
				continue;
			}
			try {
				String propertyValue = propsNode.get(key, null);
				if (propertyValue == null) {
					continue;
				}

				if (regExp ? Pattern.matches(value, propertyValue) : propertyValue.equals(value)) {
					ret.add(formUser(userNode));
				}

			} catch (StorageException e) {
				continue;
			}

		}
		return ret;
	}

	public User formUser(ISecurePreferences node) {
		if (node == null)
			return null;
		try {

			User user = new User(node.name(), node.get(USER_LOGIN, node.name()), node.get(USER_NAME, ""), node.get(USER_PASSWORD, null)); //$NON-NLS-1$ //$NON-NLS-2$
			for (String roleName : node.node(USER_ROLES).childrenNames()) {
				user.addRole(getRole(roleName));
			}

			for (String property : node.node(USER_PROPERTIES).keys()) {
				user.addProperty(property, node.node(USER_PROPERTIES).get(property, null));
			}

			return user;
		} catch (StorageException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not get user " + node.name(), e)); //$NON-NLS-1$
		}
		return null;
	}

	public User createUser(User user) {

		try {

			ISecurePreferences node = findNodeByLoginIgnoreCase(storage, user.getLogin());
			if (node != null)
				return null;

			String uid = nextUserId();

			return internalCreateOrUpdateUser(storage.node(USERS + '/' + uid), user);

		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not create user: " + user.getLogin(), e)); //$NON-NLS-1$
		}
		return null;
	}

	private ISecurePreferences findNodeByLoginIgnoreCase(ISecurePreferences storage, String login) throws StorageException {
		if (login == null)
			return null;
		ISecurePreferences usersPref = storage.node(USERS);
		String[] childrenNames = usersPref.childrenNames();
		for (int i = 0; i < childrenNames.length; i++) {
			if (usersPref.node(childrenNames[i]).get(USER_LOGIN, null) == null) {
				//migrate
				usersPref.node(childrenNames[i]).put(USER_LOGIN, usersPref.node(childrenNames[i]).name(), false);

				if (login.equalsIgnoreCase(usersPref.node(childrenNames[i]).name()))
					return usersPref.node(childrenNames[i]);

			} else {
				if (login.equalsIgnoreCase(usersPref.node(childrenNames[i]).get(USER_LOGIN, null))) {
					return usersPref.node(childrenNames[i]);
				}

			}

		}
		return null;
	}

	private ISecurePreferences findNode(ISecurePreferences storage, String uid) {
		if (uid == null)
			return null;
		ISecurePreferences usersPref = storage.node(USERS);
		String[] childrenNames = usersPref.childrenNames();
		for (int i = 0; i < childrenNames.length; i++) {
			if (uid.equals(usersPref.node(childrenNames[i]).name()))
				return usersPref.node(childrenNames[i]);
		}
		return null;
	}

	public IStatus updateUser(String uid, User user) {

		ISecurePreferences node = findNode(storage, uid);
		if (node == null)
			return new ServerStatus(IStatus.ERROR, 404, "User not found: " + uid, null);

		try {
			ISecurePreferences nodeByLogin = findNodeByLoginIgnoreCase(storage, user.getLogin());
			if (nodeByLogin != null && !node.name().equals(nodeByLogin.name())) {
				IStatus status = new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, "User already exists " + user.getLogin());
				LogHelper.log(status); //$NON-NLS-1$
				return status;
			}

			internalCreateOrUpdateUser(node, user);
			return new Status(IStatus.OK, Activator.PI_USER_SECURESTORAGE, "User updated " + user.getLogin());
		} catch (Exception e) {
			IStatus status = new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Can not update user: " + user.getLogin(), e);
			LogHelper.log(status); //$NON-NLS-1$
			return status;
		}
	}

	private User internalCreateOrUpdateUser(ISecurePreferences userPrefs, User user) throws StorageException, IOException {
		if(user.getLogin()!=null)
			userPrefs.put(USER_LOGIN, user.getLogin(), false);
		if(user.getName()!=null)
			userPrefs.put(USER_NAME, user.getName(), false);
		if(user.getPassword()!=null)
			userPrefs.put(USER_PASSWORD, user.getPassword(), true);
		ISecurePreferences rolesPrefs = userPrefs.node(USER_ROLES);
		for (String roleName : rolesPrefs.childrenNames())
			rolesPrefs.node(roleName).removeNode();
		for (org.osgi.service.useradmin.Role role : user.getRoles())
			rolesPrefs.node(((Role) role).getName());
		userPrefs.node(USER_PROPERTIES).clear();
		Enumeration<?> keys = user.getProperties().keys();
		while (keys.hasMoreElements()) {
			String property = (String) keys.nextElement();
			userPrefs.node(USER_PROPERTIES).put(property, (String) user.getProperty(property), false);
		}

		userPrefs.flush();
		return formUser(userPrefs);
	}

	public boolean deleteUser(User user) {
		ISecurePreferences node = findNode(storage, user.getUid());
		if (node == null)
			return false;
		node.clear();
		node.removeNode();
		try {
			node.flush();
			return true;
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, IStatus.ERROR, "Cannot delete user: " + user.getLogin(), e)); //$NON-NLS-1$
		}
		return false;
	}

	public Authorization getAuthorization(User user) {
		if (user instanceof User) {
			return new WebIdeAuthorization((User) user);
		}
		return new EmptyAuthorization();
	}

	public boolean canCreateUsers() {
		return true;
	}

	public String getStoreName() {
		return "Orion";
	}

}
