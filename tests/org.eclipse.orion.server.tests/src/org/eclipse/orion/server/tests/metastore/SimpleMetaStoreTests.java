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

import java.io.File;

import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleMetaStoreTests extends ExtendedMetaStoreTests {

	public IMetaStore getMetaStore() {
		// use the currently configured metastore if it is an SimpleMetaStore 
		IMetaStore metaStore = OrionConfiguration.getMetaStore();
		if (metaStore instanceof SimpleMetaStore) {
			return metaStore;
		}
		File metaStoreRoot = SimpleMetaStoreUtilTest.createTestMetaStoreFolder();
		IMetaStore simpleLinuxMetaStore = new SimpleMetaStore(metaStoreRoot);
		assertNotNull(simpleLinuxMetaStore);
		return simpleLinuxMetaStore;
	}

}
