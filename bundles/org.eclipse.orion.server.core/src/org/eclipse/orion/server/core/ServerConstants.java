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
package org.eclipse.orion.server.core;

/**
 * Public constants available to clients of the orion server core API.
 */
public class ServerConstants {

	/**
	 * The bundle ID of the server core. 
	 */
	public static final String PI_SERVER_CORE = "org.eclipse.orion.server.core"; //$NON-NLS-1$
	
	/**
	 * The preference qualifier for server configuration preferences.
	 */
	public static final String PREFERENCE_SCOPE = "org.eclipse.orion.server.configurator"; //$NON-NLS-1$

	/**
	 * The system property name for the location of the server configuration file.
	 * When this property is not set, the default is a file called "orion.conf" in the
	 * current working directory of the server process.
	 */
	public static final String PROP_CONFIG_FILE_LOCATION = "orion.core.configFile"; //$NON-NLS-1$
}
