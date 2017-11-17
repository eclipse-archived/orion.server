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
package org.eclipse.orion.server.tests;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.eclipse.orion.server.core.resources.Base64;
import org.junit.Test;

/**
 * Tests for {@link Base64}
 */
public class Base64Test {
	@Test
	public void testSimpleStrings() {
		List<String> strings = Arrays.asList("hello", "sSDFSDF", "1234SDf", "with spaces", "!@#$%^&*()_+", "a", "john", "John", "jones");
		for (String s : strings) {
			byte[] encoded = Base64.encode(s.getBytes());
			byte[] decoded = Base64.decode(encoded);
			String result = new String(decoded);
			assertEquals(s, result);
		}

	}

}
