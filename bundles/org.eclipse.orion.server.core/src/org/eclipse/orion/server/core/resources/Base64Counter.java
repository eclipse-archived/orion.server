/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.resources;

/**
 * A simple counter that can be incremented, and can represent itself as a base-64
 * encoded string.
 */
public class Base64Counter {
	private byte[] counter = new byte[6];

	/**
	 * Constructs a new counter that starts at zero.
	 */
	public Base64Counter() {
		super();
	}

	/**
	 * Constructs a new counter that starts at the given base-64 encoded number.
	 */
	public Base64Counter(String start) {
		if (start.length() > 8)
			throw new IllegalArgumentException("This counter only supports numbers up to 2^48"); //$NON-NLS-1$
		String encoding = start;
		while (encoding.length() < 8)
			encoding = "A" + encoding; //$NON-NLS-1$
		counter = Base64.decode(encoding.getBytes());
		if (counter.length != 6)
			throw new IllegalArgumentException("The input was not a valid base 64 string: " + start); //$NON-NLS-1$
	}

	/**
	 * Increments the counter by one.
	 */
	public void increment() {
		int position = 5;
		while (position >= 0) {
			if (++counter[position] == 0)
				position--;
			else
				break;
		}
	}

	/**
	 * @returns The number of times increment() has
	 */
	public long count() {
		int position = 5;
		long count = 0;
		while (position <= 5) {
			count = (count << 8) + (counter[position++] & 0xffL);
		}
		return count;
	}

	/**
	 * Returns a base 64 string representation of this counter.
	 */
	public String toString() {
		byte[] result = Base64.encode(counter);
		//trim padding characters
		int sigDigit = 0;
		while (sigDigit < 8 && result[sigDigit] == 'A')
			sigDigit++;
		return sigDigit == 8 ? "A" : new String(result, sigDigit, 8 - sigDigit); //$NON-NLS-1$
	}
}
