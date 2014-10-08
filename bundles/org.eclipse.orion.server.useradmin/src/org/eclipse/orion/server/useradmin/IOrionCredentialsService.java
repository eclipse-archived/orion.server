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
import java.util.Set;

import org.eclipse.core.runtime.IStatus;

public interface IOrionCredentialsService {

	public boolean deleteUser(User user);

	/**
	 * @param uid
	 * @param user
	 *            cannot be null
	 * @return
	 */
	public IStatus updateUser(String uid, User user);

	public User createUser(User newUser);

	public abstract String getStoreName();

	/**
	 * Indicates if users' store used by useradmin allows to create new users.
	 * 
	 * @return <code>true</code> if user store allows to create users
	 */
	public abstract boolean canCreateUsers();

	public abstract Collection<User> getUsers();

	/**
	 * Gets the user with the given property {@code key}-{@code value}
	 * pair from the User Admin service database. This is a convenience method
	 * for retrieving a {@code User} object based on a property for which
	 * every {@code User} object is supposed to have a unique value (within
	 * the scope of this User Admin service), such as for example a X.500
	 * distinguished name.
	 * 
	 * @param key The property key to look for.
	 * @param value The property value to compare with.
	 * 
	 * @return A matching user, if <em>exactly</em> one is found. If zero or
	 *         more than one matching users are found, {@code null} is
	 *         returned.
	 */
	public User getUser(String key, String value);

	/**
	 * Finds users having given properties.
	 * 
	 * @param key The property key
	 * @param value The property value or regular expression to match
	 * @param regExp <code>true</code> if <code>value</code> should be matched as regular expression.
	 * @param ignoreCase
	 * @return set of users matching criteria
	 */
	public Set<User> getUsersByProperty(String key, String value, boolean regExp, boolean ignoreCase);
}
