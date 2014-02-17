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
package org.eclipse.orion.server.cf.manifest;

public class ManifestConstants {

	/* error messages */
	public static final String PARSE_ERROR_MESSAGE = "Failed to parse the application manifest";
	public static final String PARSE_ERROR_AROUND_LINE = "Manifest syntax error around line {0}";
	public static final String PARSE_ERROR_UNKNOWN_ERROR = "Unknown error (possibly unsupported manifest syntax)";

	public static final String PARSE_ERROR_UNEXPECTED_EMPTY_LINE = "Unexpected empty line";
	public static final String PARSE_ERROR_UNEXPECTED_TOKEN_INDENTATION = "Unexpected token \"{0}\" (possibly invalid indentation)";
	public static final String PARSE_ERROR_UNEXPECTED_TOKEN_MAPPING = "Unexpected token \"{0}\" (expected <key> : <value>)";
	public static final String PARSE_ERROR_UNEXPECTED_TOKEN_GROUP = "Unexpected token \"{0}\" (expected group <key>:)";

	public static final String PARSE_ERROR_MISSING_GROUP_TOKEN = "Cannot find group \"{0}\" within {1} (possibly missing new-line)";
	public static final String PARSE_ERROR_MISSING_TOKEN = "Cannot find property \"{0}\" within {1}";

	public static final String PARSE_ERROR_MISSING_APPLICATION = "No applications listed in the manifest (possibly missing dash \"- \")";
	public static final String PARSE_ERROR_MISSING_APPLICATION_NAME = "The application does not have a \"name\" property";
}
