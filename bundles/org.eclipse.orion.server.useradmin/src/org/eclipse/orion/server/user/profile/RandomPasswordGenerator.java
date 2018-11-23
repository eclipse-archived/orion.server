/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.user.profile;

/**
 * Generates passwords.
 *
 */
public class RandomPasswordGenerator {

	private static String randomstring(int lo, int hi) {
		int n = rand(lo, hi);
		byte b[] = new byte[n];
		for (int i = 0; i < n; i++)
			b[i] = (byte) rand('1', 'Z');
		return new String(b);
	}

	private static int rand(int lo, int hi) {
		java.util.Random rn = new java.util.Random();
		int n = hi - lo + 1;
		int i = rn.nextInt(n);
		if (i < 0)
			i = -i;
		return lo + i;
	}

	/**
	 * Generates random string that may be used as auto-generated password after user reset.
	 * @return
	 */
	public static String getRandomPassword() {
		return randomstring(5, 25);
	}

}
