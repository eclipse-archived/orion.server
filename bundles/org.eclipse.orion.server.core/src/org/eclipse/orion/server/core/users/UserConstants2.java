/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.users;

/**
 * Orion server core user constants.
 * 
 * @author Anthony Hunter
 * 
 * TODO: This class needs to be renamed to UserConstants once we cleanup the other UserConstants
 */
public class UserConstants2 {

	/**
	 * JSON representation key for a blocked property that blocks user login. The value's data type is a boolean String.
	 */
	// TODO: Bug 444864 should be upper case Blocked
	public static final String BLOCKED = "blocked";

	/**
	 * JSON representation key for a user's disk usage. The value's data type is a String.
	 */
	// TODO: Bug 444864 should be upper case DiskUsage
	public static final String DISK_USAGE = "diskusage";

	/**
	 * JSON representation key for a user's disk usage timestamp. The value's data type is a String.
	 */
	// TODO: Bug 444864 should be upper case DiskUsageTimestamp
	public static final String DISK_USAGE_TIMESTAMP = "diskusagetimestamp";
	
	/**
	 * JSON representation key for a user's email address. The value's data type is a String.
	 */
	// TODO: Bug 444864 should be upper case Email
	public static final String EMAIL = "email"; //$NON-NLS-1$
	/**
	 * JSON representation key for a user's full name. The value's data type is a String.
	 */
	public static final String FULL_NAME = "FullName"; //$NON-NLS-1$
	
	/**
	 * JSON representation key for a user's last login timestamp. The value's data type is a String.
	 */
	// TODO: Bug 444864 should be upper case LastLoginTimestamp
	public static final String LAST_LOGIN_TIMESTAMP = "lastlogintimestamp";

	/**
	 * JSON representation key for a user's OAuth 2.0 Id. The value's data type is a String.
	 */
	// TODO: Bug 444864 should be upper case OAuth
	public static final String OAUTH = "oauth"; //$NON-NLS-1$
	
	/**
	 * JSON representation key for a user's openid. The value's data type is a String.
	 */
	// TODO: Bug 444864 should be upper case OpenId
	public static final String OPENID = "openid"; //$NON-NLS-1$
	
	/**
	 * JSON representation key for a user's password. The value's data type is a String.
	 */
	// TODO: Bug 444864 should be upper case Password
	public static final String PASSWORD = "password"; //$NON-NLS-1$
	
	/**
	 * JSON representation key for a user's name, the login or short account name. The value's data type is a String.
	 */
	public static final String USER_NAME = "UserName"; //$NON-NLS-1$
	

}
