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

import java.util.Properties;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.eclipse.orion.server.useradmin.User;

public class LDAPUser extends User {
	public LDAPUser(String uid){
		super(uid);
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
		Properties theProps;
		theProps = new Properties();
		theProps.put("java.naming.factory.initial",
				"com.sun.jndi.ldap.LdapCtxFactory");
		theProps.put("java.naming.provider.url", "ldap://vhost1912.site1.compute.ihost.com:389");
		theProps.put("java.naming.ldap.version", "2");
		theProps.put(Context.SECURITY_AUTHENTICATION, "simple");
		theProps.put(Context.SECURITY_PRINCIPAL, "uid=" + getLogin() + ", cn=usergroups, ou=jazzhub, o=ibm, c=us, cn=localhost");
		/** Authenticate with name: distinguishedName */
		theProps.put(Context.SECURITY_CREDENTIALS, value);
		
		DirContext userContext = null;
		
		try 
		{
			userContext = new InitialDirContext(theProps);
			return true;
		} 
		catch (NamingException e) 
		{
			e.printStackTrace();
			return false;
		}
	}
}
