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
package org.eclipse.orion.server.useradmin;

import java.util.Set;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.Role;

public class WebIdeAuthorization implements Authorization {

	public WebIdeAuthorization(User user) {
		super();
		this.user = user;
	}

	private User user;

	public String getName() {
		return user.getName();
	}

	public boolean hasRole(String name) {
		Set<Role> userRoles = user.getRoles();
		for (Role role : userRoles) {
			if (role.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public String[] getRoles() {
		Set<Role> userRoles = user.getRoles();
		String[] roleNames = new String[userRoles.size()];
		int i = 0;
		for (Role role : userRoles) {
			roleNames[i] = role.getName();
			i++;
		}
		return roleNames;
	}

}
