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

public class Property {

	public final static Property ALL_PROPERTIES = new Property("*"); //$NON-NLS-1$

	private String name;

	private ResourceShape resourceShape;

	public Property(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setResourceShape(ResourceShape resourceShape) {
		this.resourceShape = resourceShape;
	}

	public ResourceShape getResourceShape() {
		return resourceShape;
	}
}
