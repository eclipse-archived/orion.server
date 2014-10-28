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

	public static final String USER_NAME = "UserName"; //$NON-NLS-1$

	public static final String FULL_NAME = "FullName"; //$NON-NLS-1$
	
	// TODO: Bug 444864 should be upper case Password
	public static final String PASSWORD = "password"; //$NON-NLS-1$
	
	// TODO: Bug 444864 should be upper case Email
	public static final String EMAIL = "email"; //$NON-NLS-1$
	
	// TODO: Bug 444864 should be upper case OpenId
	public static final String OPENID = "openid"; //$NON-NLS-1$
	
	// TODO: Bug 444864 should be upper case OAuth
	public static final String OAUTH = "oauth"; //$NON-NLS-1$
	
}
