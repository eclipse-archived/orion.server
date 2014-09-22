/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.osgi.service.useradmin.Role;

public class User implements org.osgi.service.useradmin.User {

	private Set<Role> roles = new HashSet<Role>();

	private Properties userCredentials = new Properties();

	private Properties properties = new Properties();

	public Set<Role> getRoles() {
		return roles;
	}

	public void addRole(Role role) {
		roles.add(role);
	}

	public void removeRole(Role role) {
		roles.remove(role);
	}

	public User() {
	}

	public User(String uid, String login, String name, String password) {
		userCredentials.setProperty(UserConstants.KEY_UID, uid);
		setLogin(login);
		if (name != null)
			setName(name);
		if (password != null)
			setPassword(password);
	}

	public User(String login, String name, String password) {
		setLogin(login);
		if (name != null)
			setName(name);
		if (password != null)
			setPassword(password);
	}

	public User(String login) {
		setLogin(login);
	}

	public String getUid() {
		return userCredentials.getProperty(UserConstants.KEY_UID);
	}

	public String getLogin() {
		return userCredentials.getProperty(UserConstants.KEY_LOGIN);
	}

	public void setLogin(String login) {
		userCredentials.setProperty(UserConstants.KEY_LOGIN, login);
	}

	public String getName() {
		return userCredentials.getProperty(UserConstants.KEY_NAME);
	}

	public void setName(String name) {
		userCredentials.setProperty(UserConstants.KEY_NAME, name);
	}

	public void setUid(String uid) {
		userCredentials.setProperty(UserConstants.KEY_UID, uid);
	}

	public String getPassword() {
		return userCredentials.getProperty(UserConstants.KEY_PASSWORD);
	}

	public void setPassword(String password) {
		userCredentials.setProperty(UserConstants.KEY_PASSWORD, password);
	}

	public void setBlocked(boolean blocked) {
		if (blocked) {
			userCredentials.setProperty(UserConstants.KEY_BLOCKED, "true");
		} else {
			userCredentials.remove(UserConstants.KEY_BLOCKED);
		}
	}

	public boolean getBlocked() {
		return userCredentials.getProperty(UserConstants.KEY_BLOCKED, "false").equalsIgnoreCase("true");
	}

	public int getType() {
		return Role.USER;
	}

	public Dictionary<Object, Object> getProperties() {
		return properties;
	}

	public Dictionary<Object, Object> getCredentials() {
		return userCredentials;
	}

	public void addProperty(String key, String value) {
		properties.put(key, value);
	}

	public Object getProperty(String key) {
		return properties.get(key);
	}

	public void removeProperty(String key) {
		properties.remove(key);
	}

	public boolean hasCredential(String key, Object value) {
		return userCredentials.containsKey(key) ? userCredentials.get(key).equals(value) : false;
	}

	public String getLocation() {
		return '/' + UserConstants.KEY_USERS + '/' + getUid();
	}

	public static String getUniqueEmailConfirmationId() {
		return System.currentTimeMillis() + "-" + Math.random();
	}

	public void setEmail(String email) {
		userCredentials.setProperty(UserConstants.KEY_EMAIL, email);
	}

	public String getEmail() {
		return userCredentials.getProperty(UserConstants.KEY_EMAIL);
	}

	public String getConfirmationId() {
		return userCredentials.getProperty(UserConstants.KEY_EMAIL_CONFIRMATION);
	}

	public void setConfirmationId(String confirmationId) {
		userCredentials.setProperty(UserConstants.KEY_EMAIL_CONFIRMATION, confirmationId);
	}

	public void confirmEmail() {
		userCredentials.remove(UserConstants.KEY_EMAIL_CONFIRMATION);
		this.setBlocked(false);
	}

	public void setConfirmationId() {
		userCredentials.setProperty(UserConstants.KEY_EMAIL_CONFIRMATION, getUniqueEmailConfirmationId());
	}

	public boolean isEmailConfirmed() {
		String email = getEmail();
		return (email != null && email.length() > 0) ? userCredentials.getProperty(UserConstants.KEY_EMAIL_CONFIRMATION) == null : false;
	}
}
