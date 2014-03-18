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
package org.eclipse.orion.server.cf.manifest.v2;

import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.osgi.util.NLS;

/**
 * Indicates consumer access to a missing or invalid child member.
 */
public class InvalidAccessException extends Exception {
	private static final long serialVersionUID = 1L;

	private ManifestParseTree node;
	private String expectedChild;
	private int expectedChildNumber;

	public InvalidAccessException(ManifestParseTree node) {
		this.node = node;
		this.expectedChild = null;
		this.expectedChildNumber = -1;
	}

	public InvalidAccessException(ManifestParseTree node, String expectedChild) {
		this.node = node;
		this.expectedChild = expectedChild;
		this.expectedChildNumber = -1;
	}

	public InvalidAccessException(ManifestParseTree node, int expectedChildNumber) {
		this.node = node;
		this.expectedChild = null;
		this.expectedChildNumber = expectedChildNumber;
	}

	@Override
	public String getMessage() {
		if (expectedChildNumber < 0 && expectedChild == null)
			/* invalid mapping access */
			return NLS.bind(ManifestConstants.MISSING_MAPPING_ACCESS, node.getLabel());

		if (expectedChildNumber < 0)
			/* invalid member access */
			return NLS.bind(ManifestConstants.MISSING_MEMBER_ACCESS, node.getLabel(), expectedChild);

		/* invalid item access */
		return NLS.bind(ManifestConstants.MISSING_ITEM_ACCESS, node.getLabel(), String.valueOf(expectedChildNumber));
	}
}
