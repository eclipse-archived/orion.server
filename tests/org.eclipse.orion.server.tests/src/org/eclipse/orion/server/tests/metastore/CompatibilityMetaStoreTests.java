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

import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

/**
 * Test the implementation of an {@link IMetaStore}. 
 * One test fails so this method is overridden.
 *   
 * @author Anthony Hunter
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CompatibilityMetaStoreTests extends MetaStoreTests {

	@Override
	public IMetaStore getMetaStore() {
		//just use the currently configured metastore by default.
		return OrionConfiguration.getMetaStore();
	}
}
