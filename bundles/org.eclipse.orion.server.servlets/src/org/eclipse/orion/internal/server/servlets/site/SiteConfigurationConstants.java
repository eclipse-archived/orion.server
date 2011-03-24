package org.eclipse.orion.internal.server.servlets.site;

public class SiteConfigurationConstants {

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
	 * JSON representation key for a site configuration's hosting status. The value's data type is a JSON object.
	 */
	public static final String KEY_HOSTING_STATUS = "HostingStatus"; //$NON-NLS-1$

	/**
	 * JSON representation key for a site configuration's workspace id. The value's data type is a String.
	 */
	public static final String KEY_WORKSPACE = "Workspace"; //$NON-NLS-1$

	/*
	 * Constants below are specific to the HostingStatus object.
	 */
	/**
	 * JSON representation key for a hosting status's status. The value's data type is a String.
	 */
	public static final String KEY_HOSTING_STATUS_STATUS = "Status"; //$NON-NLS-1$

	/**
	 * JSON representation key for a hosting status's URL. The value's data type is a String.
	 */
	public static final String KEY_HOSTING_STATUS_URL = "URL"; //$NON-NLS-1$

}
