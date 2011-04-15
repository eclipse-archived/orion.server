/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin;

import java.util.Collection;
import org.osgi.service.useradmin.UserAdmin;

public interface IOrionCredentialsService extends UserAdmin {

	public boolean deleteUser(User user);

	/**
	 * @param login
	 * @param user
	 *            cannot be null
	 * @return
	 */
	public boolean updateUser(String login, User user);

	public User createUser(User newUser);

	public abstract String getStoreName();

	/**
	 * Indicates if users' store used by useradmin allows to create new users.
	 * 
	 * @return <code>true</code> if user store allows to create users
	 */
	public abstract boolean canCreateUsers();

	public abstract Collection<User> getUsers();
}
