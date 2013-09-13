/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.AfterClass;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleMetaStoreTests extends ExtendedMetaStoreTests {

	private static File tempDir = null;

	public IMetaStore getMetaStore() {
		// use the currently configured metastore if it is an SimpleMetaStore 
		IMetaStore metaStore = null;
		try {
			metaStore = OrionConfiguration.getMetaStore();
		} catch (NullPointerException e) {
			// expected when the workbench is not running
		}
		if (metaStore instanceof SimpleMetaStore) {
			return metaStore;
		}
		File metaStoreRoot = getTempDir();
		IMetaStore simpleLinuxMetaStore = new SimpleMetaStore(metaStoreRoot);
		assertNotNull(simpleLinuxMetaStore);
		return simpleLinuxMetaStore;
	}

	private static File getTempDir() {
		if (tempDir == null) {
			tempDir = new File(FileSystemHelper.getRandomLocation(FileSystemHelper.getTempDir()).toOSString());
			tempDir.mkdir();
		}
		return tempDir;
	}

	@AfterClass
	public static void deleteTempDir() {
		// Very last test, delete the temporary folder
		File parent = getTempDir();
		if (parent.exists()) {
			// delete the root
			SimpleMetaStoreUtilTest.deleteFile(parent);
		}
		if (parent.exists()) {
			fail("Could not delete the temporary folder, something is wrong.");
		}
		tempDir = null;
	}
}
