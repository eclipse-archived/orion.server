/*******************************************************************************
 * Copyright (c) 2010, 2013 IBM Corporation and others 
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
import java.util.Properties;

public class Role implements org.osgi.service.useradmin.Role {

	private int roleType = Role.GROUP;

	private Properties roleProperties = new Properties();

	public void setName(String name) {
		roleProperties.put("name", name);
	}

	public Role() {

	}

	public Role(String name, int roleType) {
		super();
		this.roleType = roleType;
		setName(name);
	}

	public String getName() {
		return roleProperties.getProperty("name");
	}

	public int getType() {
		return roleType;
	}

	public Dictionary<Object, Object> getProperties() {
		return roleProperties;
	}

}
