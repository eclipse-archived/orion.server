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

import java.net.URI;

import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;

@ResourceDescription(type = TestResource.TEST_TYPE)
public class TestResource {
	static final String TEST_TYPE = "TestType";

	static final String STRING_PROPERTY_NAME = "Name";
	static final String INT_PROPERTY_NAME = "Id";
	static final String BOOLEAN_PROPERTY_NAME = "Parent";
	static final String LOCATION_PROPERTY_NAME = "Location";
	static final String RESOURCE_PROPERTY_NAME = "Child";

	static final Property STRING_PROPERTY = new Property(TestResource.STRING_PROPERTY_NAME);
	static final Property INT_PROPERTY = new Property(TestResource.INT_PROPERTY_NAME);
	static final Property BOOLEAN_PROPERTY = new Property(TestResource.BOOLEAN_PROPERTY_NAME);
	static final Property LOCATION_PROPERTY = new Property(TestResource.LOCATION_PROPERTY_NAME);
	static final Property RESOURCE_PROPERTY = new Property(TestResource.RESOURCE_PROPERTY_NAME);

	static final Property[] ALL_PROPERTIES = new Property[] {STRING_PROPERTY, INT_PROPERTY, BOOLEAN_PROPERTY, LOCATION_PROPERTY, RESOURCE_PROPERTY};

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	static {
		ResourceShape nestedResourceShape = new ResourceShape();
		nestedResourceShape.addProperty(LOCATION_PROPERTY);
		RESOURCE_PROPERTY.setResourceShape(nestedResourceShape);

		DEFAULT_RESOURCE_SHAPE.setProperties(ALL_PROPERTIES);
	}

	private String name;
	private int id;
	private boolean parent;
	private TestResource child;

	public TestResource() {
		this("Foo", 1, true, new TestResource("Bar", 2, false, null));
	}

	public TestResource(String name, int id, boolean parent, TestResource child) {
		this.name = name;
		this.id = id;
		this.parent = parent;
		this.child = child;
	}

	@PropertyDescription(name = STRING_PROPERTY_NAME)
	public String getName() {
		return name;
	}

	@PropertyDescription(name = INT_PROPERTY_NAME)
	public int getID() {
		return id;
	}

	@PropertyDescription(name = BOOLEAN_PROPERTY_NAME)
	public boolean isParent() {
		return parent;
	}

	@PropertyDescription(name = LOCATION_PROPERTY_NAME)
	public URI getLocationProperty() {
		return URI.create("http://localhost:8080/testResource/" + id);
	}

	@PropertyDescription(name = RESOURCE_PROPERTY_NAME, expandable = true)
	public TestResource getChild() {
		return child;
	}
}