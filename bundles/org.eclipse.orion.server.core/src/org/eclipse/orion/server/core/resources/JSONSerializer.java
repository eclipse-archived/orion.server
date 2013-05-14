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
package org.eclipse.orion.server.core.resources;

import java.lang.reflect.Method;
import org.eclipse.core.runtime.Assert;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONSerializer implements Serializer<JSONObject> {

	public JSONObject serialize(Object resource, ResourceShape resourceShape) {
		JSONObject result = new JSONObject();
		try {
			for (Property property : resourceShape.getProperties()) {
				Method getter = ReflectionHelper.findGetterForPropertyName(resource.getClass(), property.getName());
				Object value = ReflectionHelper.callGetter(resource, getter);
				PropertyDescription propertyDescriptionAnnotation = ReflectionHelper.getAnnotation(getter, PropertyDescription.class);
				if (propertyDescriptionAnnotation.expandable()) {
					ResourceShape nestedResourceShape = property.getResourceShape();
					Assert.isNotNull(nestedResourceShape, "Could not find resource shape definition for: " + property.getName()); //$NON-NLS-1$
					result.put(property.getName(), serialize(value, nestedResourceShape));
				} else {
					result.put(property.getName(), value);
				}
			}
			final ResourceDescription resourceShapeDescription = resource.getClass().getAnnotation(ResourceDescription.class);
			// TODO: org.eclipse.orion.internal.server.servlets.ProtocolConstants.KEY_TYPE
			result.put("Type", resourceShapeDescription.type()); //$NON-NLS-1$
		} catch (JSONException e) {
			// should never happen
		}
		return result;
	}
}
