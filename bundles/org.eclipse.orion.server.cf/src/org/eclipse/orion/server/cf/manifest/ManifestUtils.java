/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common manifest parser utilities.
 */
public class ManifestUtils {
	public static final String MANIFEST_FILE_NAME = "manifest.yml";
	private static final Pattern pattern = Pattern.compile("[^ ]");

	/**
	 * Removes in-line comments form the single input line.
	 */
	public static String removeComments(String input) {
		return input.split("#")[0];
	}

	/**
	 * Determines whether the given line
	 * starts a new sequence item or not.
	 */
	public static boolean isSequenceItem(String input) {
		return input.trim().startsWith("- ");
	}

	/**
	 * Parses <A>:<B> from the input line.
	 */
	public static String[] splitLabel(String input) {
		String[] sLabel = input.split(":");
		if (sLabel[0].startsWith("- "))
			sLabel[0] = sLabel[0].substring(2);

		if (sLabel.length > 1)
			sLabel[1] = sLabel[1].trim();

		return sLabel;
	}

	/**
	 * Determines the input line depth, defined as
	 * the number of prefix spaces (indentation).
	 */
	public static int getDepth(String input) {
		Matcher matcher = pattern.matcher(input);
		if (matcher.find())
			return matcher.start();
		else
			return 0;
	}

	/**
	 * Parses the number of MB from the input
	 * string in form of <Integer>M. Defaults to 128.
	 */
	public static int getMemoryLimit(String input) {
		return (!input.isEmpty()) ? Integer.parseInt(input.split("M")[0]) : 128;
	}

	/**
	 * Parses the number of instances from 
	 * the input string. Defaults to 1.
	 */
	public static int getInstances(String input) {
		return (!input.isEmpty()) ? Integer.parseInt(input) : 1;
	}
}
