/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class Slug {

	/**
	 * Decodes a <tt>Slug</tt> header.
	 * @param slug
	 * @see <a href="http://tools.ietf.org/html/rfc5023#section-9.7.1">http://tools.ietf.org/html/rfc5023#section-9.7.1</a>
	 * @return The decoded value of the <tt>Slug</tt>, or <tt>null</tt> if the Slug header was null or was not valid UTF-8.
	 */
	public static String decode(String slug) {
		if (slug == null)
			return null;
		try {
			return URLDecoder.decode(slug.replace("+", "%2B"), "UTF-8");
		} catch (IllegalArgumentException e) {
			// Malformed Slug
		} catch (UnsupportedEncodingException e) {
			// Should not happen
		}
		return null;
	}

	private static char hexDigit(int val) {
		return (val < 10) ? (char) ('0' + val) : (char) ('A' + val - 10);
	}

	/**
	 * Encodes a <tt>Slug</tt> header.
	 * @param value
	 * @return The encoded <tt>Slug</tt>.
	 */
	public static String encode(String value) {
		final byte percent = 0x25;
		try {
			byte bytes[] = value.getBytes("UTF-8");
			StringBuilder buf = new StringBuilder();
			for (int i = 0; i < bytes.length; i++) {
				byte b = bytes[i];
				if (b < 0x20 || b > 0x7e || b == percent) {
					buf.append('%').append(hexDigit((b >> 4) & 0x0f)).append(hexDigit(b & 0x0f));
				} else {
					buf.append((char) b); // ASCII
				}
			}
			return buf.toString();
		} catch (UnsupportedEncodingException e) {
			// Should not happen
			return null;
		}
	}

}
