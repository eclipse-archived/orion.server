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
package org.eclipse.orion.server.cf;

public class CFProtocolConstants {

	/**
	 * JSON representation key for a user's list of targets. The value's data type is a
	 * JSON array of JSON objects.
	 */
	public static final String KEY_TARGETS = "Targets"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's default target. The value's data type is a
	 * String.
	 */
	public static final String KEY_DEFAULT_TARGET = "DefaultTarget"; //$NON-NLS-1$

	/**
	 * JSON representation key for a target's name. The value's data type is a String.
	 */
	public static final String KEY_NAME = "Name"; //$NON-NLS-1$

	/**
	 * JSON representation key for a target's URL. The value's data type is a String.
	 */
	public static final String KEY_URL = "Url"; //$NON-NLS-1$

	/**
	 * JSON representation key for a target's user id. The value's data type is a String.
	 */
	public static final String KEY_USER = "User"; //$NON-NLS-1$

	/**
	 * JSON representation key for a target's password. The value's data type is a String.
	 */
	public static final String KEY_PASSWORD = "Password"; //$NON-NLS-1$

	/**
	 * JSON representation key for a target's token. The value's data type is a String.
	 */
	public static final String KEY_TOKEN = "Token"; //$NON-NLS-1$

	/**
	 * JSON representation key for a target's organization. The value's data type is a String.
	 */
	public static final String KEY_ORGANIZATION = "Organization"; //$NON-NLS-1$

	/**
	 * JSON representation key for a target's space. The value's data type is a String.
	 */
	public static final String KEY_SPACE = "Space"; //$NON-NLS-1$
}
