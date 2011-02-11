package org.eclipse.orion.internal.server.servlets.site;

public class SiteConfigurationConstants {

	/**
	 * Non-standard HTTP header for POST requests indicating the action (start/stop) that should be 
	 * taken on a site configuration.
	 */
	public static final String HEADER_ACTION = "X-Action"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's list of site configurations. The value's data type is a
	 * JSON array of JSON objects.
	 */
	public static final String KEY_SITE_CONFIGURATIONS = "SiteConfigurations"; //$NON-NLS-1$

	/**
	 * JSON representation key for a site configuration's mappings. The value's data type is a
	 * JSON array of JSON objects.
	 */
	public static final String KEY_MAPPINGS = "Mappings"; //$NON-NLS-1$

	/**
	 * JSON representation key for a mapping's source. The value's data type is a String giving
	 * the workspace path being mapped.
	 */
	public static final String KEY_SOURCE = "Source"; //$NON-NLS-1$

	/**
	 * JSON representation key for a mapping's target. The value's data type is a String giving
	 * the URL to which the source path will be mapped.
	 */
	public static final String KEY_TARGET = "Target"; //$NON-NLS-1$

	/**
	 * JSON representation key for a site configuration's host hint. The value's data type is a String.
	 */
	public static final String KEY_HOST_HINT = "HostHint"; //$NON-NLS-1$

	/**
	 * TODO javadoc
	 */
	//	public static final String KEY_AUTH_NAME = "AuthName"; //$NON-NLS-1$

	/**
	 * TODO javadoc 
	 */
	public static final String KEY_AUTH_PASSWORD = "AuthPassword"; //$NON-NLS-1$

}
