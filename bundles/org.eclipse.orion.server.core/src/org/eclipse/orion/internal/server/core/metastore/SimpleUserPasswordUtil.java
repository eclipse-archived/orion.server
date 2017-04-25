/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.core.metastore;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64;

/**
 * A utility class to encrypt and decrypt passwords. This implementation is a simplification of 
 * the capability that exists in org.eclipse.equinox.security.
 * 
 * @author Anthony Hunter
 */
public class SimpleUserPasswordUtil {

	/**
	 * The system property name for the secure storage master password.
	 */
	public static final String ORION_STORAGE_PASSWORD = "orion.storage.password"; //$NON-NLS-1$

	/**
	 * Separates salt from the encryped password.
	 */
	final static private char SALT_SEPARATOR = ',';
	final static private String CHAR_ENCODING = "UTF-8"; //$NON-NLS-1$
	final static private String ENCRYPTION_ALGORITHM = "PBEWithMD5AndDES";

	public static String encryptPassword(String password) {
		try {
			byte[] salt = generateSalt();
			byte[] encryptedPassword = encryptPassword(password.getBytes(), salt);
			byte[] saltBase64 = Base64.encode(salt);
			byte[] encryptedPasswordBase64 = Base64.encode(encryptedPassword);
			String saltString = new String(saltBase64, CHAR_ENCODING);
			String encryptedPasswordString = new String(encryptedPasswordBase64, CHAR_ENCODING);
			StringBuffer stringBuffer = new StringBuffer();
			stringBuffer.append(saltString);
			stringBuffer.append(SALT_SEPARATOR);
			stringBuffer.append(encryptedPasswordString);
			return stringBuffer.toString();
		} catch (NoSuchAlgorithmException e) {
			LogHelper.log(e);
		} catch (UnsupportedEncodingException e) {
			LogHelper.log(e);
		}
		return null;
	}

	private static byte[] decryptPassword(byte[] password, byte[] salt) {
		try {
			byte[] decryptedPassword = null;
			PBEKeySpec pbeKeySpec = new PBEKeySpec(getPassword(), salt, 1024, 256);
			SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(ENCRYPTION_ALGORITHM);
			SecretKey secretKey = secretKeyFactory.generateSecret(pbeKeySpec);
			PBEParameterSpec pbeParameterSpec = new PBEParameterSpec(salt, 10);
			Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, pbeParameterSpec);
			decryptedPassword = cipher.doFinal(password);
			return decryptedPassword;
		} catch (NoSuchAlgorithmException e) {
			LogHelper.log(e);
		} catch (InvalidKeySpecException e) {
			LogHelper.log(e);
		} catch (InvalidKeyException e) {
			LogHelper.log(e);
		} catch (IllegalBlockSizeException e) {
			LogHelper.log(e);
		} catch (BadPaddingException e) {
			LogHelper.log(e);
		} catch (NoSuchPaddingException e) {
			LogHelper.log(e);
		} catch (InvalidAlgorithmParameterException e) {
			LogHelper.log(e);
		}
		return null;
	}

	private static byte[] encryptPassword(byte[] password, byte[] salt) {
		try {
			byte[] encryptedPassword = null;
			PBEKeySpec pbeKeySpec = new PBEKeySpec(getPassword(), salt, 1024, 256);
			SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(ENCRYPTION_ALGORITHM);
			SecretKey secretKey = secretKeyFactory.generateSecret(pbeKeySpec);
			PBEParameterSpec pbeParameterSpec = new PBEParameterSpec(salt, 10);
			Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, pbeParameterSpec);
			encryptedPassword = cipher.doFinal(password);
			return encryptedPassword;
		} catch (NoSuchAlgorithmException e) {
			LogHelper.log(e);
		} catch (InvalidKeySpecException e) {
			LogHelper.log(e);
		} catch (InvalidKeyException e) {
			LogHelper.log(e);
		} catch (IllegalBlockSizeException e) {
			LogHelper.log(e);
		} catch (BadPaddingException e) {
			LogHelper.log(e);
		} catch (NoSuchPaddingException e) {
			LogHelper.log(e);
		} catch (InvalidAlgorithmParameterException e) {
			LogHelper.log(e);
		}
		return null;
	}

	private static byte[] generateSalt() throws NoSuchAlgorithmException {
		SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
		byte[] salt = new byte[8];
		secureRandom.nextBytes(salt);
		return salt;
	}

	private static boolean verifyPassword(String password, byte[] encryptedPassword, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] checkEncryptedPassword = encryptPassword(password.getBytes(), salt);
		return Arrays.equals(encryptedPassword, checkEncryptedPassword);
	}

	public static String decryptPassword(String encryptedText) {
		try {
			int saltPos = encryptedText.indexOf(SALT_SEPARATOR);
			if (saltPos == -1) {
				throw new RuntimeException("Invalid Data Format");
			}
			byte[] saltBase64 = encryptedText.substring(0, saltPos).getBytes(CHAR_ENCODING);
			byte[] encryptedPasswordBase64 = encryptedText.substring(saltPos + 1).getBytes(CHAR_ENCODING);
			byte[] salt = Base64.decode(saltBase64);
			byte[] encryptedPassword = Base64.decode(encryptedPasswordBase64);
			byte[] decryptedPassword = decryptPassword(encryptedPassword, salt);
			return new String(decryptedPassword);
		} catch (UnsupportedEncodingException e) {
			LogHelper.log(e);
		}
		return null;
	}

	public static boolean verifyPassword(String password, String encryptedText) {
		try {
			int saltPos = encryptedText.indexOf(SALT_SEPARATOR);
			if (saltPos == -1) {
				throw new RuntimeException("Invalid Data Format");
			}
			byte[] saltBase64 = encryptedText.substring(0, saltPos).getBytes(CHAR_ENCODING);
			byte[] encryptedPasswordBase64 = encryptedText.substring(saltPos + 1).getBytes(CHAR_ENCODING);
			byte[] salt = Base64.decode(saltBase64);
			byte[] encryptedPassword = Base64.decode(encryptedPasswordBase64);
			return verifyPassword(password, encryptedPassword, salt);
		} catch (NoSuchAlgorithmException e) {
			LogHelper.log(e);
		} catch (InvalidKeySpecException e) {
			LogHelper.log(e);
		} catch (UnsupportedEncodingException e) {
			LogHelper.log(e);
		}
		return false;
	}

	private static char[] getPassword() {
		String password = System.getProperty(ORION_STORAGE_PASSWORD, "unspecified"); //$NON-NLS-1$
		return password.toCharArray();
	}

	public static void main(String[] args) {
		if (args.length > 2 && "-encode".equals(args[0])) {
			System.out.println(encryptPassword(args[1]));
		} else if (args.length > 2 && "-decode".equals(args[0])) {
			System.out.println(decryptPassword(args[1]));
		} else {
			System.out.println("Usage: java SimpleUserPasswordUtil <-encode || -decode>  value");
		}
	}
}