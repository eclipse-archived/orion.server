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
package org.eclipse.orion.server.core.users;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A class containing helper method for working with users.
 */
public class UserUtilities {
	/**
	 * Returns the URL of an image corresponding to the given email address.
	 * Currently this is implemented using gravatar.
	 */
	public static String getImageLink(String emailAddress) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			//without MD5 we can't compute gravatar hashes
			return null;
		}
		digest.update(emailAddress.trim().toLowerCase().getBytes());
		byte[] digestValue = digest.digest();
		StringBuffer result = new StringBuffer("https://www.gravatar.com/avatar/"); //$NON-NLS-1$
		for (int i = 0; i < digestValue.length; i++) {
			String current = Integer.toHexString((digestValue[i] & 0xFF));
			//left pad with zero
			if (current.length() == 1)
				result.append('0');
			result.append(current);
		}
		//Default to "mystery man" icon if the user has no gravatar, and use a 40 pixel image
		result.append("?d=mm"); //$NON-NLS-1$
		return result.toString();
	}

}
