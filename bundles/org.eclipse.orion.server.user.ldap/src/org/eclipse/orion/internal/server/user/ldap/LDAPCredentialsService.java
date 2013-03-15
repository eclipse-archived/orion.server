/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.user.ldap;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.useradmin.EmptyAuthorization;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.Role;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.WebIdeAuthorization;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Authorization;

/**
 * The implementation of User Service on LDAP
 */
public class LDAPCredentialsService implements IOrionCredentialsService {
	
	static final String USER_LOGIN = "login"; //$NON-NLS-1$
	static final String USER_UID = "uid"; //$NON-NLS-1$
	static final String USER_EMAIL = "email"; //$NON-NLS-1$

	public boolean deleteUser(User user) {
		return false;
	}

	public IStatus updateUser(String uid, User user) {
		return null;
	}

	public User createUser(User newUser) {
		return null;
	}

	public String getStoreName() {
		return "Orion on LDAP"; //$NON-NLS-1$
	}

	public boolean canCreateUsers() {
		return false;
	}

	public Collection<User> getUsers() {
		return null;
	}

	public Role createRole(String name, int type) {
		return null;
	}

	public IStatus removeRole(String name) {
		return null;
	}

	public Role getRole(String name) {
		return null;
	}

	public Role[] getRoles(String filter) throws InvalidSyntaxException {
		return null;
	}

	public User getUser(String key, String value) {
		if (key.equals(USER_LOGIN)) {
			return new LDAPUser(value);
		} else if (key.equals(USER_UID)) {
			return new LDAPUser(value);
		}
		return null;
	}

	public Authorization getAuthorization(User user) {
		if (user instanceof User) {
			return new WebIdeAuthorization((User) user);
		}
		return new EmptyAuthorization();
	}

	public Set<User> getUsersByProperty(String key, String value, boolean regExp, boolean ignoreCase) {
		return new HashSet<User>();
	}

}
