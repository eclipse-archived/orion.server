/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest.v2.utils;

public class ManifestConstants {

	/* application start timeout */
	public static final int MAX_TIMEOUT = 180; /* 3 minutes */

	public static final int DEFAULT_TIMEOUT = 60; /* 1 minute */

	/* manifest file limit */
	public static final long MANIFEST_SIZE_LIMIT = (100 * 1024); /* specified in bytes */

	public static final String MISSING_OR_INVALID_MANIFEST = "Could not read the manifest. Missing or invalid file.";

	public static final String MANIFEST_FILE_SIZE_EXCEEDED = "Refused to read the manifest. Exceeded maximum file size limit.";

	public static final String EMPTY_MANIFEST = "Empty manifest.";

	/* manifest inheritance errors */
	public static final String FORBIDDEN_ACCESS_ERROR = "Forbidden access to parent manifest {0}.";

	public static final String INHERITANCE_CYCLE_ERROR = "Could not parse the manifest. Inheritance cycle detected.";

	/* Manifest error messages */
	public static final String UNEXPECTED_INPUT_ERROR = "Unexpected token around line {0}.";

	public static final String UNSUPPORTED_TOKEN_ERROR = "Unsupported token around line {0}.";

	public static final String ILLEGAL_ITEM_TOKEN = "Unexpected token \"{1}\" around line {0}. Instead, expected a string literal.";

	public static final String ILLEGAL_ITEM_TOKEN_MIX = "Unexpected item token \"{1}\" around line {0}. Instead, expected a string literal mapping, i.e. \"property : value\".";

	public static final String ILLEGAL_MAPPING_TOKEN_MIX = "Unexpected string literal mapping \"{1}\" around line {0}. Instead, expected an item token, i.e. \" - property: value\".";

	public static final String ILLEGAL_MAPPING_TOKEN = "Unexpected token \"{1}\" around line {0}. Instead, expected \":\".";

	public static final String DUPLICATE_MAPPING_TOKEN = "Unexpected token \"{1}\" around line {0}. Instead, expected a string literal or item symbol \"- \".";

	public static final String MISSING_ITEM_ACCESS = "Expected {0} to have at least {1} item members.";

	public static final String MISSING_MEMBER_ACCESS = "Expected {0} to have a member \"{1}\".";

	public static final String MISSING_MAPPING_ACCESS = "Expected {0} to have a value.";

	/* Manifest constants */
	public static final String MANIFEST_FILE_NAME = "manifest.yml"; //$NON-NLS-1$

	public static final String APPLICATIONS = "applications"; //$NON-NLS-1$

	public static final String NAME = "name"; //$NON-NLS-1$

	public static final String HOST = "host"; //$NON-NLS-1$

	public static final String MEMORY = "memory"; //$NON-NLS-1$

	public static final String PATH = "path"; //$NON-NLS-1$

	public static final String INSTANCES = "instances"; //$NON-NLS-1$

	public static final String COMMAND = "command"; //$NON-NLS-1$

	public static final String INHERIT = "inherit"; //$NON-NLS-1$

	public static final String ENV = "env"; //$NON-NLS-1$

	public static final String SERVICES = "services"; //$NON-NLS-1$

	public static final String BUILDPACK = "buildpack"; //$NON-NLS-1$

	public static final String DOMAIN = "domain"; //$NON-NLS-1$

	public static final String TIMEOUT = "timeout"; //$NON-NLS-1$

	public static final String NOROUTE = "no-route"; //$NON-NLS-1$
}
