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

import java.net.URI;

import org.eclipse.orion.internal.server.core.metastore.SimpleLinuxMetaStore;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleLinuxMetaStoreTests extends MetaStoreTests {

	public IMetaStore getMetaStore() {
		URI metaStoreRoot = SimpleLinuxMetaStoreUtilTest.createTestMetaStoreFolder();
		IMetaStore simpleLinuxMetaStore = new SimpleLinuxMetaStore(metaStoreRoot);
		assertNotNull(simpleLinuxMetaStore);
		return simpleLinuxMetaStore;
	}

}
