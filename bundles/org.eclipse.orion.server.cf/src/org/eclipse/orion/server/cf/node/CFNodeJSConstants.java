package org.eclipse.orion.server.cf.node;

public class CFNodeJSConstants {

	public static final String KEY_NPM_DEPENDENCIES = "dependencies";

	public static final String KEY_CF_LAUNCHER_PACKAGE = "cf-launcher";

	public static final String VALUE_CF_LAUNCHER_VERSION = "0.0.x";

	public static final String KEY_NPM_SCRIPTS = "scripts";

	public static final String KEY_NPM_SCRIPTS_START = "start";

	public static final String PROCESS_TYPE_WEB = "web";

	public static final String PROCFILE_FILE_NAME = "Procfile";

	public static final String PACKAGE_JSON_FILE_NAME = "package.json";

	public static final String SERVER_JS_FILE_NAME = "server.js";

	public static final String START_SERVER_COMMAND = "node {0}";

	/**
	 * MessageFormat string for generating the debug launcher start command. Expected format args:
	 * <ul>
	 * <li><tt>{0}</tt> : password (string, nonempty)</li>
	 * <li><tt>{1}</tt> : desired launcher URL prefix (string, may be empty)</li>
	 * <li><tt>{2}</tt> :  app's original start command (string, nonempty)</li>
	 * </ul>
	 */
	public static final String CF_LAUNCHER_COMMAND_FORMAT = "node_modules/.bin/launcher --password \"{0}\" --urlprefix \"{1}\" -- {2}";

}
