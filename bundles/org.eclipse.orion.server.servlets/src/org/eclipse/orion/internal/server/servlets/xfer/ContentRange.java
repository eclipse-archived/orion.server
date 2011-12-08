/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.xfer;

/**
 * Representation of an HTTP Content-Range header.
 */
class ContentRange {
	private int startByte, endByte, length;

	static ContentRange parse(String header) {
		if (header == null)
			throw new IllegalArgumentException();
		//example: "bytes 0-32767/901024"
		ContentRange result = new ContentRange();
		int start = 0;
		while (!Character.isDigit(header.charAt(start)))
			start++;
		int dash = header.indexOf('-');
		int slash = header.indexOf('/');
		if (dash < 0 || slash < 0)
			throw new IllegalArgumentException(header);
		try {
			result.startByte = Integer.parseInt(header.substring(start, dash));
			result.endByte = Integer.parseInt(header.substring(dash + 1, slash));
			result.length = Integer.parseInt(header.substring(slash + 1));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(header);
		}
		return result;
	}

	private ContentRange() {
		//client must user factory method
	}

	int getStartByte() {
		return startByte;
	}

	int getEndByte() {
		return endByte;
	}

	int getLength() {
		return length;
	}

	@Override
	public String toString() {
		return "ContentRange(" + startByte + '-' + endByte + '/' + length + ')'; //$NON-NLS-1$
	}
}
