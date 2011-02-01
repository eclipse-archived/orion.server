/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.filesystem.git;

public class UserPasswordCredentials {
	private final String username;
	private final String password;

	/**
	 * @param username
	 * @param password
	 */
	public UserPasswordCredentials(String username, String password) {
		this.username = username;
		this.password = password;
	}

	/**
	 * @return user name
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @return password
	 */
	public String getPassword() {
		return password;
	}

}