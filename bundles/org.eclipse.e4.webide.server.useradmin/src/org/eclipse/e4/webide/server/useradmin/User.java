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
package org.eclipse.e4.webide.server.useradmin;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.osgi.service.useradmin.Role;

public class User implements org.osgi.service.useradmin.User {


    private Set<Role> roles = new HashSet<Role>();
    
    private Properties userCredentials = new Properties();
    
    public Set<Role> getRoles() {
		return roles;
	}

	public void addRole(Role role){
    	roles.add(role);
    }
    
    public void removeRole(Role role){
    	roles.remove(role);
    }

	public User() {
    }

    public User(String login, String name, String password) {
        setLogin(login);
        setName(name);
        setPassword(password);
    }

    public String getLogin() {
        return userCredentials.getProperty("login");
    }

    public void setLogin(String login) {
    	userCredentials.setProperty("login", login);
    }

    public String getName() {
        return userCredentials.getProperty("name");
    }

    public void setName(String name) {
        userCredentials.setProperty("name", name);
    }

    public String getPassword() {
        return userCredentials.getProperty("password");
    }

    public void setPassword(String password) {
        userCredentials.setProperty("password", password);
    }

	public int getType() {
		return Role.USER;
	}

	public Dictionary getProperties() {
		return new Properties();
	}

	public Dictionary getCredentials() {
		return userCredentials;
	}

	public boolean hasCredential(String key, Object value) {
		return userCredentials.containsKey(key) ? userCredentials.get(key).equals(value) : false;
	}
}
