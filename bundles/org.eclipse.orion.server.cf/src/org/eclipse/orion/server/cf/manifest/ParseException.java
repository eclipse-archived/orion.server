/*******************************************************************************
 * Copyright (c) 2013-2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest;

import org.eclipse.osgi.util.NLS;

public class ParseException extends Exception {
	private static final long serialVersionUID = 1L;
	private String message;
	private int line;

	public ParseException() {
		this.message = null;
		this.line = 0;
	}

	public ParseException(String message) {
		this.message = message;
		this.line = 0;
	}

	public ParseException(String message, int line) {
		this.message = message;
		this.line = line;
	}

	@Override
	public String getMessage() {
		StringBuilder sb = new StringBuilder();

		if (line < 1)
			/* general parse exception, no additional info available */
			sb.append(ManifestConstants.PARSE_ERROR_MESSAGE);
		else
			sb.append(NLS.bind(ManifestConstants.PARSE_ERROR_AROUND_LINE, String.valueOf(line)));

		sb.append(" : "); //$NON-NLS-1$

		if (message != null)
			sb.append(message);
		else
			sb.append(ManifestConstants.PARSE_ERROR_UNKNOWN_ERROR);

		return sb.toString();
	}
}
