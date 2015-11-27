/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Various static helper methods for hash calculations.
 */
public class HashUtilities {
	public static final String SHA_1 = "SHA-1"; //$NON-NLS-1$

	/**
	 * Returns the hash of data read from the given input stream.
	 * 
	 * @param inputStream
	 *            input stream
	 * @param hashFunction
	 *            desired hash function (i.e. SHA-1)
	 * @return text representation of the hash
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	public static String getHash(InputStream inputStream, String hashFunction) throws IOException, NoSuchAlgorithmException {
		return getHash(inputStream, false, hashFunction);
	}

	/**
	 * Returns the hash of data read from the given input stream.
	 * 
	 * @param inputStream
	 *            input stream
	 * @param hashFunction
	 *            desired hash function (i.e. SHA-1)
	 * @param closeIn
	 *            determines if input stream should be closed after reading
	 * @return text representation of the hash
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	public static String getHash(InputStream inputStream, boolean closeIn, String hashFunction) throws IOException, NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance(hashFunction);

		byte[] buffer = new byte[4096];
		int read = 0;
		try {
			while ((read = inputStream.read(buffer)) != -1)
				md.update(buffer, 0, read);
		} finally {
			if (closeIn)
				IOUtilities.safeClose(inputStream);
		}

		byte[] mdbytes = md.digest();

		return bytesToHex(mdbytes);
	}

	/**
	 * Returns the hash of the given input String.
	 * 
	 * @param data
	 *            string to compute hash for
	 * @param hashFunction
	 *            desired hash function (i.e. SHA-1)
	 * @return text representation of the hash
	 * @throws NoSuchAlgorithmException
	 */
	public static String getHash(String data, String hashFunction) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance(hashFunction);

		md.update(data.getBytes());
		byte[] mdbytes = md.digest();

		return bytesToHex(mdbytes);
	}

	// convert the byte to hex format
	private static String bytesToHex(byte[] bytes) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < bytes.length; i++) {
			String hexString = Integer.toHexString(0xFF & bytes[i]);
			while (hexString.length() < 2) {
				hexString = "0" + hexString;
			}
			sb.append(hexString);
		}

		return sb.toString();
	}
}
