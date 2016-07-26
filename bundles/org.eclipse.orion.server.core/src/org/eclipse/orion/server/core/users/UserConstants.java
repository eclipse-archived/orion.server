/*******************************************************************************
 * Copyright (c) 2012, 2015 IBM Corporation and others.
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
 */
public class UserConstants {

	/**
	 * JSON representation key for a blocked property that blocks user login. The value's data type is a boolean String.
	 */
	public static final String BLOCKED = "Blocked"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's account creation timestamp. The value's data type is a String.
	 */
	public static final String CREATION_TIMESTAMP = "AccountCreationTimestamp";

	/**
	 * JSON representation key for a user's disk usage. The value's data type is a String.
	 */
	public static final String DISK_USAGE = "DiskUsage"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's disk usage timestamp. The value's data type is a String.
	 */
	public static final String DISK_USAGE_TIMESTAMP = "DiskUsageTimestamp"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's email address. The value's data type is a String.
	 */
	public static final String EMAIL = "Email"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's email address confirmation. The value's data type is a String.
	 */
	public static final String EMAIL_CONFIRMATION_ID = "EmailConfirmationId"; //$NON-NLS-1$

	/**
	 * JSON representation key for whether a user has a confirmed email address. The value's data type is a Boolean.
	 */
	public static final String EMAIL_CONFIRMED = "EmailConfirmed"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's full name. The value's data type is a String.
	 */
	public static final String FULL_NAME = "FullName"; //$NON-NLS-1$

	/**
	 * JSON representation key for whether a user has a password. The value's data type is a Boolean.
	 */
	public static final String HAS_PASSWORD = "HasPassword"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's last login timestamp. The value's data type is a String.
	 */
	public static final String LAST_LOGIN_TIMESTAMP = "LastLoginTimestamp";

	/**
	 * JSON representation key for the URL for a user's profile. The value's data type is a String.
	 */
	public static final String LOCATION = "Location"; //$NON-NLS-1$

	/**
	 * The root location of the users servlet.
	 */
	public static final String LOCATION_USERS_SERVLET = "/users"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's OAuth 2.0 Id. The value's data type is a String.
	 */
	public static final String OAUTH = "OAuth"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's old password. The value's data type is a String.
	 */
	public static final String OLD_PASSWORD = "OldPassword"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's openid. The value's data type is a String.
	 */
	public static final String OPENID = "OpenId"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's password. The value's data type is a String.
	 */
	public static final String PASSWORD = "Password"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's password reset id. The value's data type is a String.
	 */
	public static final String PASSWORD_RESET_ID = "PasswordResetId"; //$NON-NLS-1$

	/**
	 * JSON representation key to request a user's password to be reset. The value's data type is a Boolean.
	 */
	public static final String RESET = "Reset"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's name, the login or short account name. The value's data type is a String.
	 */
	public static final String USER_NAME = "UserName"; //$NON-NLS-1$

	/**
	 * JSON representation key for when a warning e-mail was sent to a user regarding the intention
	 * to delete their workspaces due to inactivity. The value's data type is a String.
	 */
	public static final String WORKSPACEPRUNER_STARTDATE = "PrunerStartTimestamp"; //$NON-NLS-1$
	public static final String WORKSPACEPRUNER_REMINDERSENT = "PrunerReminderSent"; //$NON-NLS-1$
	public static final String WORKSPACEPRUNER_FINALWARNINGSENT = "PrunerFinalWarningSent"; //$NON-NLS-1$
	public static final String WORKSPACEPRUNER_ENDDATE = "PrunerEndTimestamp"; //$NON-NLS-1$
}
