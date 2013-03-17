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
package org.eclipse.orion.internal.server.user.guest;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.useradmin.IOrionGuestCredentialsService;
import org.eclipse.orion.server.useradmin.Role;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserAdminActivator;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Authorization;

/**
 * An implementation of User Service that stores guest users in memory.
 */
@SuppressWarnings("restriction")
public class GuestCredentialsService implements IOrionGuestCredentialsService {

	// TODO can get these be gotten from UserConstants instead?
	static final String USER_UID = "uid"; //$NON-NLS-1$
	static final String USER_LOGIN = "login"; //$NON-NLS-1$
	static final String USER_NAME = "name"; //$NON-NLS-1$
	static final String USER_PASSWORD = "password"; //$NON-NLS-1$
	static final String USER_BLOCKED = "blocked"; //$NON-NLS-1$
	static final String USER_EMAIL = "email"; //$NON-NLS-1$
	static final String USER_EMAIL_CONFIRMATION = "email_confirmation"; //$NON-NLS-1$
	static final String USER_ROLES = "roles"; //$NON-NLS-1$
	static final String USER_ROLE_NAME = "name"; //$NON-NLS-1$
	static final String USER_PROPERTIES = "properties"; //$NON-NLS-1$
	
	private Map<String, User> userTable = new HashMap<String, User>();

	public GuestCredentialsService() {
		
	}

	private User findUser(String uid) {
		synchronized (userTable) {
			return userTable.get(uid);
		}
	}
	
	/**
	 * Creates a user in the storage using fields provided by another User object.
	 * @param user
	 */
	private User internalCreateUser(User user) {
		synchronized (userTable) {
			String uid = user.getUid();
			String login = uid;
			String name = user.getName(); // The only parameter we actually accept
			String password = null;
			User newUser = new GuestUser(uid, login, name, password);

			userTable.put(newUser.getUid(), newUser);
			return findUser(newUser.getUid());
		}
	}

	private void internalUpdateUser(User target, User source) {
		if (source.getLogin() != null)
			target.setLogin(source.getLogin());
		if (source.getName() != null)
			target.setName(source.getName());
		if (source.getPassword() != null)
			target.setPassword(source.getPassword());

		target.setBlocked(source.getBlocked());

		if (source.getEmail() != null) {
			if (source.getEmail().length() > 0 && !source.getEmail().equals(target.getEmail())) {
				source.setConfirmationId();
			}
			target.setEmail(source.getEmail());
		}

		// TODO copy roles
//		ISecurePreferences rolesPrefs = userPrefs.node(USER_ROLES);
//		for (String roleName : rolesPrefs.childrenNames())
//			rolesPrefs.node(roleName).removeNode();
//		for (org.osgi.service.useradmin.Role role : source.getRoles())
//			rolesPrefs.node(((Role) role).getName());

		// Make properties of target match source's
		Dictionary<Object, Object> sourceProperties = source.getProperties();
		Dictionary<Object, Object> targetProperties = target.getProperties();
		Enumeration<?> sourceKeys = sourceProperties.keys();
		while (sourceKeys.hasMoreElements()) {
			String property = (String) sourceKeys.nextElement();
			targetProperties.put(property, sourceProperties.get(property));
		}
		Enumeration<?> targetKeys = targetProperties.keys();
		while (targetKeys.hasMoreElements()) {
			String property = (String) targetKeys.nextElement();
			if (sourceProperties.get(property) == null) {
				targetProperties.remove(property);
			}
		}
	}

	public boolean deleteUser(User user) {
		if (user == null)
			return false;
		synchronized (userTable) {
			return userTable.remove(user.getUid()) != null;
		}
	}

	public IStatus updateUser(String uid, User user) {
		User existingUser = findUser(uid);
		if (existingUser == null)
			return new ServerStatus(IStatus.ERROR, 404, "User not found: " + uid, null);
		try {
			internalUpdateUser(existingUser, user);
			return new Status(IStatus.OK, Activator.PI_USER_GUEST, "User updated " + user.getLogin());
		} catch (Exception e) {
			IStatus status = new Status(IStatus.ERROR, Activator.PI_USER_GUEST, IStatus.ERROR, "Can not update user: " + user.getLogin(), e);
			LogHelper.log(status);
			return status;
		}
	}

	public User createUser(User newUser) {
		try {
			return internalCreateUser(newUser);
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.ERROR, UserAdminActivator.PI_USERADMIN, IStatus.ERROR, "Cannot create user: " + newUser.getName(), e)); //$NON-NLS-1$
		}
		return null;
	}

	public String getStoreName() {
		return "OrionGuestUserStore"; //$NON-NLS-1$
	}

	public boolean canCreateUsers() {
		return true;
	}

	public Collection<User> getUsers() {
		return userTable.values();
	}

	public Role createRole(String name, int type) {
		throw new UnsupportedOperationException();
	}

	public IStatus removeRole(String name) {
		return new Status(IStatus.ERROR, UserAdminActivator.PI_USERADMIN, "Removing roles not supported");
	}

	public Role getRole(String name) {
		return null;
	}

	public Role[] getRoles(String filter) throws InvalidSyntaxException {
		return new Role[0];
	}

	public User getUser(String key, String value) {
		if (USER_UID.equals(key)) {
			return userTable.get(value);
		} else if (USER_LOGIN.equals(key)) {
			for (User user : userTable.values()) {
				if (user.getLogin().equals(value)) {
					return user;
				}
			}
		}
		return null;
	}

	public Authorization getAuthorization(User user) {
		// TODO Auto-generated method stub
		return null;
	}

	public Set<User> getUsersByProperty(String key, String value, boolean regExp, boolean ignoreCase) {
		// TODO Auto-generated method stub
		return null;
	}

}
