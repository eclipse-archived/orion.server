/*******************************************************************************
 *  Copyright (c) 2010 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.filesystem.jackrabbit;

import java.net.URI;

public class PutInfoTest extends org.eclipse.core.tests.filesystem.PutInfoTest {
	protected URI getFileStoreUri() {
		return URI.create("jackrabbit://test");
	}

	protected void fileSystemSetUp() {
		// nothing to do
	}

	protected void fileSystemTearDown() {
		// nothing to do
	}
}
