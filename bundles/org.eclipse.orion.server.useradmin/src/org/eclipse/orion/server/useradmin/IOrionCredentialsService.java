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
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.UserAdminEvent;

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
	 * Creates a {@code Role} object with the given name and of the given
	 * type.
	 * 
	 * <p>
	 * If a {@code Role} object was created, a {@code UserAdminEvent}
	 * object of type {@link UserAdminEvent#ROLE_CREATED} is broadcast to any
	 * {@code UserAdminListener} object.
	 * 
	 * @param name The {@code name} of the {@code Role} object to create.
	 * @param type The type of the {@code Role} object to create. Must be
	 *        either a {@link Role#USER} type or {@link Role#GROUP} type.
	 * 
	 * @return The newly created {@code Role} object, or {@code null} if a
	 *         role with the given name already exists.
	 * 
	 * @throws IllegalArgumentException if {@code type} is invalid.
	 * 
	 * @throws SecurityException If a security manager exists and the caller
	 *         does not have the {@code UserAdminPermission} with name
	 *         {@code admin}.
	 */
	public Role createRole(String name, int type);

	/**
	 * Removes the {@code Role} object with the given name from this User
	 * Admin service and all groups it is a member of.
	 * 
	 * <p>
	 * If the {@code Role} object was removed, a {@code UserAdminEvent}
	 * object of type {@link UserAdminEvent#ROLE_REMOVED} is broadcast to any
	 * {@code UserAdminListener} object.
	 * 
	 * @param name The name of the {@code Role} object to remove.
	 * 
	 * @return {@code OK} status if a {@code Role} object with the given name
	 *         is present in this User Admin service and could be removed,
	 *         otherwise error description.
	 * 
	 * @throws SecurityException If a security manager exists and the caller
	 *         does not have the {@code UserAdminPermission} with name
	 *         {@code admin}.
	 */
	public IStatus removeRole(String name);

	/**
	 * Gets the {@code Role} object with the given {@code name} from this
	 * User Admin service.
	 * 
	 * @param name The name of the {@code Role} object to get.
	 * 
	 * @return The requested {@code Role} object, or {@code null} if this
	 *         User Admin service does not have a {@code Role} object with
	 *         the given {@code name}.
	 */
	public Role getRole(String name);

	/**
	 * Gets the {@code Role} objects managed by this User Admin service that
	 * have properties matching the specified LDAP filter criteria. See
	 * {@code org.osgi.framework.Filter} for a description of the filter
	 * syntax. If a {@code null} filter is specified, all Role objects
	 * managed by this User Admin service are returned.
	 * 
	 * @param filter The filter criteria to match.
	 * 
	 * @return The {@code Role} objects managed by this User Admin service
	 *         whose properties match the specified filter criteria, or all
	 *         {@code Role} objects if a {@code null} filter is specified.
	 *         If no roles match the filter, {@code null} will be returned.
	 * @throws InvalidSyntaxException If the filter is not well formed.
	 *  
	 */
	public Role[] getRoles(String filter) throws InvalidSyntaxException;

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
	 * Creates an {@code Authorization} object that encapsulates the
	 * specified {@code User} object and the {@code Role} objects it
	 * possesses. The {@code null} user is interpreted as the anonymous user.
	 * The anonymous user represents a user that has not been authenticated. An
	 * {@code Authorization} object for an anonymous user will be unnamed,
	 * and will only imply groups that user.anyone implies.
	 * 
	 * @param user The {@code User} object to create an
	 *        {@code Authorization} object for, or {@code null} for the
	 *        anonymous user.
	 * 
	 * @return the {@code Authorization} object for the specified
	 *         {@code User} object.
	 */
	public Authorization getAuthorization(User user);
	
	/**
	 * Finds users having given properties.
	 * 
	 * @param key The property key
	 * @param value The property value or regular expression to match
	 * @param regExp <code>true</code> if <code>value</code> should be matched as regural expression.
	 * @param ignoreCase
	 * @return set of users matching criteria
	 */
	public Set<User> getUsersByProperty(String key, String value, boolean regExp, boolean ignoreCase);
}
