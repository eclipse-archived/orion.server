/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.junit.Test;

/**
 * Test class to test the encryption and decryption of passwords.
 *  
 * @author Anthony Hunter
 */
public class SimpleUserPasswordUtilTests {
	@Test
	public void testEncryptPassword() {
		String password = "DontUsePasswordAsThePassword";
		String encryptedPassword = SimpleUserPasswordUtil.encryptPassword(password);
		assertTrue(SimpleUserPasswordUtil.verifyPassword(password, encryptedPassword));
	}

	@Test
	public void testDecryptPassword() {
		String password = "DontUsePasswordAsThePassword";
		String encryptedPassword = SimpleUserPasswordUtil.encryptPassword(password);
		String decryptedPassword = SimpleUserPasswordUtil.decryptPassword(encryptedPassword);
		assertEquals(password, decryptedPassword);
	}
}
