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

	public static String UNEXPECTED_INPUT_ERROR = "Unexpected token around line {0}.";

	public static String UNSUPPORTED_TOKEN_ERROR = "Unsupported token around line {0}.";

	public static String ILLEGAL_ITEM_TOKEN = "Unexpected token \"{1}\" around line {0}. Instead, expected a string literal.";

	public static String ILLEGAL_MAPPING_TOKEN = "Unexpected token \"{1}\" around line {0}. Instead, expected \":\".";

	public static String MISSING_ITEM_ACCESS = "Expected {0} to have at least {1} item members.";

	public static String MISSING_MEMBER_ACCESS = "Expected {0} to have a member \"{1}\".";
}
