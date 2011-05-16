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

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.junit.Test;

public class WebElementTest {
	@Test
	public void testWebProject() {
		List<String> ids = new ArrayList<String>();
		for (int i = 0; i < 1000; i++) {
			String candidate = WebProject.nextProjectId();
			for (String id : ids) {
				assertTrue(new java.io.File(id).compareTo(new java.io.File(candidate)) != 0);
			}
			ids.add(candidate);
		}
	}
}
