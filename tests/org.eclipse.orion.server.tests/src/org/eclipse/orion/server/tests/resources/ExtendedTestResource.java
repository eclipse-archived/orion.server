/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.resources;

import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;

@ResourceDescription(type = ExtendedTestResource.EXTENDED_TEST_TYPE)
public class ExtendedTestResource extends TestResource {
	static final String EXTENDED_TEST_TYPE = "ExtendedTestType";

	static final String LONG_PROPERTY_NAME = "Time";

	@PropertyDescription(name = LONG_PROPERTY_NAME)
	public long getTime() {
		return 1l;
	}

	@Override
	@PropertyDescription(name = STRING_PROPERTY_NAME)
	public String getName() {
		return super.getName() + " (extended)";
	}
}
