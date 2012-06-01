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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;

import org.eclipse.orion.server.core.resources.JSONSerializer;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.ResourceShapeFactory;
import org.eclipse.orion.server.core.resources.Serializer;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Tests for {@link JSONSerializer}.
 */
public class JSONSerializerTest {
	@Test
	public void testSerializeAllProperties() throws Exception {
		// given
		TestResource testResource = new TestResource();
		Serializer<JSONObject> jsonSerializer = new JSONSerializer();
		ResourceShape resourceShape = new ResourceShape();
		resourceShape.setProperties(TestResource.ALL_PROPERTIES);

		// when
		JSONObject jsonObject = jsonSerializer.serialize(testResource, resourceShape);
		String jsonString = jsonObject.toString();
		jsonObject = new JSONObject(jsonString);

		// then
		assertEquals(testResource.getName(), jsonObject.getString(TestResource.STRING_PROPERTY_NAME));
		assertEquals(testResource.getID(), jsonObject.getInt(TestResource.INT_PROPERTY_NAME));
		assertEquals(testResource.isParent(), jsonObject.getBoolean(TestResource.BOOLEAN_PROPERTY_NAME));
		assertEquals(testResource.getLocationProperty().toString(), jsonObject.getString(TestResource.LOCATION_PROPERTY_NAME));
		JSONObject childJson = jsonObject.getJSONObject(TestResource.RESOURCE_PROPERTY_NAME);
		assertEquals(testResource.getChild().getLocationProperty(), URI.create(childJson.getString(TestResource.LOCATION_PROPERTY_NAME)));
		// TODO: org.eclipse.orion.internal.server.servlets.ProtocolConstants.KEY_TYPE
		assertEquals(TestResource.TEST_TYPE, jsonObject.getString("Type"));
	}

	@Test
	public void testSerializeSingleProperty() throws Exception {
		// given
		TestResource testResource = new TestResource();
		Serializer<JSONObject> jsonSerializer = new JSONSerializer();
		ResourceShape resourceShape = new ResourceShape();
		resourceShape.addProperty(TestResource.STRING_PROPERTY);

		// when
		JSONObject jsonObject = jsonSerializer.serialize(testResource, resourceShape);
		String jsonString = jsonObject.toString();
		jsonObject = new JSONObject(jsonString);

		// then
		assertEquals(testResource.getName(), jsonObject.getString(TestResource.STRING_PROPERTY_NAME));
		assertNull(jsonObject.optString(TestResource.INT_PROPERTY_NAME, null));
		// TODO: org.eclipse.orion.internal.server.servlets.ProtocolConstants.KEY_TYPE
		assertEquals(TestResource.TEST_TYPE, jsonObject.getString("Type"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSerializeInvalidProperty() throws Exception {
		// given
		TestResource testResource = new TestResource();
		Serializer<JSONObject> jsonSerializer = new JSONSerializer();
		ResourceShape resourceShape = new ResourceShape();
		resourceShape.addProperty(new Property("invalid"));

		// when
		jsonSerializer.serialize(testResource, resourceShape);
	}

	@Test
	public void testSerializeExtendedTestResource() throws Exception {
		// given
		ExtendedTestResource testResource = new ExtendedTestResource();
		Serializer<JSONObject> jsonSerializer = new JSONSerializer();
		ResourceShape resourceShape = ResourceShapeFactory.createResourceShape(ExtendedTestResource.class, null);

		// when
		JSONObject jsonObject = jsonSerializer.serialize(testResource, resourceShape);
		String jsonString = jsonObject.toString();
		jsonObject = new JSONObject(jsonString);

		// then
		assertEquals(testResource.getName(), jsonObject.getString(TestResource.STRING_PROPERTY_NAME));
		assertEquals(testResource.getID(), jsonObject.getInt(TestResource.INT_PROPERTY_NAME));
		assertEquals(testResource.isParent(), jsonObject.getBoolean(TestResource.BOOLEAN_PROPERTY_NAME));
		assertEquals(testResource.getLocationProperty().toString(), jsonObject.getString(TestResource.LOCATION_PROPERTY_NAME));
		JSONObject childJson = jsonObject.getJSONObject(TestResource.RESOURCE_PROPERTY_NAME);
		assertEquals(testResource.getChild().getLocationProperty(), URI.create(childJson.getString(TestResource.LOCATION_PROPERTY_NAME)));
		assertEquals(testResource.getTime(), jsonObject.getLong(ExtendedTestResource.LONG_PROPERTY_NAME));
		// TODO: org.eclipse.orion.internal.server.servlets.ProtocolConstants.KEY_TYPE
		assertEquals(ExtendedTestResource.EXTENDED_TEST_TYPE, jsonObject.getString("Type"));
	}
}
