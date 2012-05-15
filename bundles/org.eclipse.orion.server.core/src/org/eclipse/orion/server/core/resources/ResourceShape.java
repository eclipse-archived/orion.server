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
package org.eclipse.orion.server.core.resources;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ResourceShape {

	public static final String SEPARATOR = ","; //$NON-NLS-1$
	public static final String WILDCARD = "*"; //$NON-NLS-1$

	private final Set<Property> properties = new HashSet<Property>();

	public Property[] getProperties() {
		return properties.toArray(new Property[properties.size()]);
	}

	public void setProperties(final Property[] properties) {
		this.properties.clear();
		if (properties != null) {
			this.properties.addAll(Arrays.asList(properties));
		}
	}

	public void addProperty(final Property property) {
		this.properties.add(property);
	}
}