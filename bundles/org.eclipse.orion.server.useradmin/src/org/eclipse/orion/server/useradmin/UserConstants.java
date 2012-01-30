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
package org.eclipse.orion.server.useradmin;

/**
 * User constants
 */
public interface UserConstants {
	public static final String KEY_USERS = "users"; //$NON-NLS-1$
	
	public static final String KEY_LOGIN = "login"; //$NON-NLS-1$
	
	public static final String KEY_UID = "uid"; //$NON-NLS-1$

	public static final String KEY_PASSWORD = "password"; //$NON-NLS-1$
	
	public static final String KEY_EMAIL = "email"; //$NON-NLS-1$
	
	public static final String KEY_EMAIL_CONFIRMED = "emailConfirmed"; //$NON-NLS-1$
	
	public static final String KEY_OLD_PASSWORD = "oldPassword"; //$NON-NLS-1$
	
	public static final String KEY_HAS_PASSWORD = "hasPassword"; //$NON-NLS-1$
	
	public static final String KEY_PROPERTIES = "properties"; //$NON-NLS-1$
	
	public static final String KEY_STORE = "store"; //$NON-NLS-1$
	
	public static final String KEY_PLUGINS = "Plugins"; //$NON-NLS-1$
	
	public static final String KEY_PLUGIN_LOCATION = "Url"; //$NON-NLS-1$

	public static final String KEY_ROLES = "Roles"; //$NON-NLS-1$
	
	public static final String KEY_RESET = "reset"; //$NON-NLS-1$
	
	public static final String KEY_LAST_LOGIN_TIMESTAMP = "LastLogInTimestamp"; //$NON-NLS-1$
	
	public static final String KEY_CONFIRMATION_ID = "confirmationId"; //$NON-NLS-1$
	
	public static final String KEY_PASSWORD_RESET_CONFIRMATION_ID = "passwordResetId"; //$NON-NLS-1$
}
