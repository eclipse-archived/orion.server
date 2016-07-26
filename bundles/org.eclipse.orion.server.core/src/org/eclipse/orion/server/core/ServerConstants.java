/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
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
	 * The name of configuration property specifying if access logs in NCSA format should be enabled on the Jetty server
	 * (see Bug 429063)
	 */
	public static final String CONFIG_ACCESS_LOGS_ENABLED = "orion.jetty.access.logs.enable"; //$NON-NLS-1$

	/**
	 * The system property name for the secure storage master password.
	 */
	public static final String CONFIG_AUTH_ADMIN_DEFAULT_PASSWORD = "orion.auth.admin.default.password"; //$NON-NLS-1$

	/**
	 * The name of the configuration property to disable the default username and password verification rules. By
	 * default a username is between three and twenty characters long. By default passwords must be eight characters
	 * long and must contain at least one alpha character and one non alpha character. If this preference is true these
	 * rules are ignored and any password may be used.
	 */
	public static final String CONFIG_AUTH_DISABLE_ACCOUNT_RULES = "orion.auth.disable.account.rules"; //$NON-NLS-1$

	/**
	 * The name of the configuration property that tells us what server to use for authentication purposes. In a basic
	 * server configuration this will be undefined, and the direct Orion server will be treated as the authentication
	 * host. If the Orion server is sitting behind a proxy, the server administrator will typically need to set the
	 * value of this property to be the proxy host. Refer to the Orion server administration guide for more details.
	 */
	public static final String CONFIG_AUTH_HOST = "orion.auth.host"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying a comma-separated list of users that are allowed to access the
	 * logs service. If unspecified, then no users can access the logs service.
	 */
	public static final String CONFIG_AUTH_LOG_SERVICE = "orion.auth.log.service"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying the name of the authorization component to be used.
	 */
	public static final String CONFIG_AUTH_NAME = "orion.auth.name"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying an alternate URI to handle registrations for accounts. If this
	 * variable is set AND the site does not allow for direct registrations the Register Button will be visible and this
	 * URI will be opened taking the user off site.
	 */
	public static final String CONFIG_AUTH_REGISTRATION_URI = "orion.auth.registration.uri"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying a comma-separated list of users that are allowed to create
	 * accounts. If unspecified, then anonymous users can create accounts.
	 */
	public static final String CONFIG_AUTH_USER_CREATION = "orion.auth.user.creation"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying is user email is mandatory while user creation. If
	 * <code>true</code> user will be forced to add email while creating account. Account will be blocked until user
	 * email is confirmed.
	 */
	public static final String CONFIG_AUTH_USER_CREATION_FORCE_EMAIL = "orion.auth.user.creation.force.email"; //$NON-NLS-1$

	/**
	 * The name of a configuration property to enable the cf liveupdate feature.
	 */
	public static final String CONFIG_CF_LIVEUPDATE_ENABLED = "orion.cf.liveupdate.enabled"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying the context path to use for the server (e.g. /code).
	 */
	public static final String CONFIG_CONTEXT_PATH = "orion.context.path";

	public static final String CONFIG_EVENT_CLIENT_ID = "orion.events.clientId"; //$NON-NLS-1$

	/**
	 * The name of configuration property specifying the password for the MQTT message broker
	 */
	public static final String CONFIG_EVENT_PASSWORD = "orion.events.password"; //$NON-NLS-1$

	public static final String CONFIG_EVENT_TRUST_STORE = "orion.events.trustStore";

	/**
	 * The name of configuration property specifying the username for the MQTT message broker
	 */
	public static final String CONFIG_EVENT_USERNAME = "orion.events.username"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying a comma-separated list of server file system paths where user
	 * content can be written. By default user content can only appear within the server instance location (workspace).
	 */
	public static final String CONFIG_FILE_ALLOWED_PATHS = "orion.file.allowedPaths"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying whether anonymous read access is allowed to files stored on this
	 * orion server. The property value is a boolean and the default is <code>false</code>.
	 */
	public static final String CONFIG_FILE_ANONYMOUS_READ = "orion.file.anonymous.read"; //$NON-NLS-1$

	/**
	 * The name of the configuration property specifying that process file locking should be used. Helps with multiple
	 * Orion servers running against the same user content or search indices. Values are <code>true</code> or
	 * <code>false</code>. Default is <code>false</code>.
	 */
	public static final String CONFIG_FILE_CONTENT_LOCKING = "orion.file.content.locking"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying the default source configuration management system to use for
	 * newly created top level folders.
	 */
	public static final String CONFIG_FILE_DEFAULT_SCM = "orion.file.defaultSCM"; //$NON-NLS-1$

	/**
	 * The name of the configuration property specifying the root location to use for all Orion content. Must be an
	 * absolute path on the server file system.
	 */
	public static final String CONFIG_FILE_USER_CONTENT = "orion.file.content.location"; //$NON-NLS-1$

	/**
	 * The name of configuration property specifying the SMTP host for sending mail
	 */
	public static final String CONFIG_MAIL_SMTP_HOST = "mail.smtp.host"; //$NON-NLS-1$

	/**
	 * The name of configuration property specifying if TLS should be enabled
	 */
	public static final String CONFIG_MAIL_SMTP_STARTTLS = "mail.smtp.starttls.enable"; //$NON-NLS-1$

	/**
	 * The name of a configuration property specifying the virtual hosts to use for test sites launched by this server.
	 * The property value is a comma-separated list of host names.
	 */
	public static final String CONFIG_SITE_VIRTUAL_HOSTS = "orion.site.virtualHosts"; //$NON-NLS-1$

	/**
	 * The names of configuration properties for the workspace pruning support. When the CONFIG_WORKSPACEPRUNER_ENABLED
	 * property is set to true the workspacePrunerJob will run periodically.
	 */
	public static final String CONFIG_WORKSPACEPRUNER_ENABLED = "orion.workspacePruner.enabled"; //$NON-NLS-1$
	public static final String CONFIG_WORKSPACEPRUNER_DAYCOUNT_INITIALNOTIFICATION = "orion.workspacePruner.daycount.initialNotification"; //$NON-NLS-1$
	public static final String CONFIG_WORKSPACEPRUNER_DAYCOUNT_DELETIONAFTERNOTIFICATION = "orion.workspacePruner.daycount.deletionAfterNotification"; //$NON-NLS-1$
	public static final String CONFIG_WORKSPACEPRUNER_INSTALLATION_URL = "orion.workspacePruner.installUrl"; //$NON-NLS-1$

	/**
	 * The name of a configuration property to enable cross-site request forgery protection (XSRF - default is false).
	 */
	public static final String CONFIG_XSRF_PROTECTION_ENABLED = "orion.XSRFPreventionFilterEnabled"; //$NON-NLS-1$

	/**
	 * The bundle ID of the server core.
	 */
	public static final String PI_SERVER_CORE = "org.eclipse.orion.server.core"; //$NON-NLS-1$

	/**
	 * The preference qualifier for server configuration preferences.
	 */
	public static final String PREFERENCE_SCOPE = "org.eclipse.orion.server.core"; //$NON-NLS-1$

	/**
	 * The system property name for the location of the server configuration file. When this property is not set, the
	 * default is a file called "orion.conf" in the current working directory of the server process.
	 */
	public static final String PROP_CONFIG_FILE_LOCATION = "orion.core.configFile"; //$NON-NLS-1$
}
