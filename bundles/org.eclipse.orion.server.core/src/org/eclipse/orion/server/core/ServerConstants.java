/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
	 * The name of a configuration property specifying the name of the authorization
	 * component to be used.
	 */
	public static final String CONFIG_AUTH_NAME= "orion.auth.name"; //$NON-NLS-1$
	
	/**
	 * The name of a configuration property specifying a comma-separated list of users
	 * that are allowed to create accounts. If unspecified, then anonymous users can
	 * create accounts.
	 */
	public static final String CONFIG_AUTH_USER_CREATION= "orion.auth.user.creation"; //$NON-NLS-1$

	/**
	 * The system property name for the secure storage master password.
	 */
	public static final String CONFIG_AUTH_ADMIN_DEFAULT_PASSWORD = "orion.auth.admin.default.password"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying the default source configuration management
	 * system to use for newly created top level folders.
	 */
	public static final String CONFIG_FILE_DEFAULT_SCM = "orion.file.defaultSCM"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying whether anonymous read access
	 * is allowed to files stored on this orion server. The property value is a boolean and
	 * the default is <code>false</code>.
	 */
	public static final String CONFIG_FILE_ANONYMOUS_READ= "orion.file.anonymous.read"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying a comma-separated list of server
	 * file system paths where user content can be written. By default user content
	 * can only appear within the server instance location (workspace).
	 */
	public static final String CONFIG_FILE_ALLOWED_PATHS = "orion.file.allowedPaths"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying the layout format for user data files.
	 */
	public static final String CONFIG_FILE_LAYOUT = "orion.file.layout"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying the virtual hosts to use for
	 * test sites launched by this server. The property value is a comma-separated 
	 * list of host names.
	 */
	public static final String CONFIG_SITE_VIRTUAL_HOSTS= "orion.site.virtualHosts"; //$NON-NLS-1$
	
	/**
	 * The name of configuration property specifying the SMTP host for sending mail
	 */
	public static final String CONFIG_MAIL_SMTP_HOST = "mail.smtp.host"; //$NON-NLS-1$

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
