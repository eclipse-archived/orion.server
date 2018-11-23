/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.ResourceShapeFactory;
import org.junit.Test;

/**
 * Tests for {@link ResourceShape}.
 */
public class ResourceShapeTest {
	@Test
	public void testDefaultResourceShape() throws Exception {
		// when
		ResourceShape resourceShape = ResourceShapeFactory.createResourceShape(TestResource.class, null);

		// then
		Property[] properties = resourceShape.getProperties();
		assertAllPropertiesExists(properties);
	}

	private void assertAllPropertiesExists(Property[] properties) {
		assertEquals(5, properties.length);
		assertHasProperty(properties, TestResource.STRING_PROPERTY);
		assertHasProperty(properties, TestResource.INT_PROPERTY);
		assertHasProperty(properties, TestResource.BOOLEAN_PROPERTY);
		assertHasProperty(properties, TestResource.LOCATION_PROPERTY);
		assertHasProperty(properties, TestResource.RESOURCE_PROPERTY);
	}

	/**
	 * Asserts that a property array contains a property with name matching the given property.
	 */
	private void assertHasProperty(Property[] properties, Property toContain) {
		for (Property testProp : properties) {
			if (toContain.getName().equals(testProp.getName()))
				return;
		}
		fail("Missing expected property: " + toContain.getName());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidRepositoryShapeType() throws Exception {
		// when
		ResourceShapeFactory.createResourceShape(TestResource.class, "invalid");
	}

	@Test
	public void testDefaultResourceShapeForNestedResource() throws Exception {
		// given
		ResourceShape resourceShape = ResourceShapeFactory.createResourceShape(TestResource.class, null);
		Property[] properties = resourceShape.getProperties();
		Property resourceProperty = getPropertyWithName(properties, TestResource.RESOURCE_PROPERTY_NAME);

		// when
		ResourceShape nestedResourceShape = resourceProperty.getResourceShape();

		// then
		Property[] nestedProperties = nestedResourceShape.getProperties();
		assertEquals(1, nestedProperties.length);
		assertHasProperty(nestedProperties, TestResource.LOCATION_PROPERTY);
	}

	private Property getPropertyWithName(Property[] properties, String name) {
		for (Property property : properties) {
			if (property.getName().equals(name))
				return property;
		}
		throw new IllegalArgumentException(name + " not found");
	}
}
