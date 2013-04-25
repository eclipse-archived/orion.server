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

import org.eclipse.orion.server.useradmin.User;

public class LDAPUser extends User {
	private LDAPCredentialsService ldapCredentialsService;

	public LDAPUser(String uid, LDAPCredentialsService ldapCredentialsService){
		super(uid);
		this.ldapCredentialsService = ldapCredentialsService;
	}

	@Override
	public String getUid() {
		// TODO Auto-generated method stub
		return getLogin();
	}

	@Override
	public String getEmail() {
		return "a@wp.pl";
	}

	@Override
	public boolean hasCredential(String key, Object value) {
		return ldapCredentialsService.hasCredentials(getLogin(), value);
	}
}
