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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.eclipse.core.runtime.Assert;
import org.eclipse.orion.server.core.resources.ResourceShapeParser.parse_return;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.osgi.util.NLS;

public class ResourceShapeFactory {
	/**
	 * Creates an instance of {@link ResourceShape} for the given resource class and selection query.
	 *
	 * @param resourceClass resource class
	 * @param selectionQuery selection query
	 * @return {@link this}
	 */
	public static ResourceShape createResourceShape(final Class<?> resourceClass, String selectionQuery) {
		final ResourceDescription resourceShapeDescription = resourceClass.getAnnotation(ResourceDescription.class);
		Assert.isNotNull(resourceShapeDescription);
		if (selectionQuery == null) {
			ResourceShape resourceShape = new ResourceShape();
			List<Property> properties = findAllProperties(resourceClass);
			resourceShape.setProperties(properties.toArray(new Property[properties.size()]));
			return resourceShape;
		} else {
			ResourceShape resourceShape = parseSelectionQuery(selectionQuery);
			resourceShape = expandWildcards(resourceClass, resourceShape);
			validateGetMethodExist(resourceClass, resourceShape);
			return resourceShape;
		}
	}

	private static List<Property> findAllProperties(Class<?> resourceClass) {
		Method[] getters = ReflectionHelper.findAllGetters(resourceClass);
		List<Property> properties = new ArrayList<Property>(getters.length);
		for (Method method : getters) {
			final PropertyDescription propertyDescriptionAnnotation = ReflectionHelper.getAnnotation(method, PropertyDescription.class);
			Property property = new Property(propertyDescriptionAnnotation.name());
			addLocationIfExpandable(resourceClass, propertyDescriptionAnnotation, property);
			properties.add(property);
		}
		return properties;
	}

	private static void addLocationIfExpandable(Class<?> resourceClass, PropertyDescription propertyDescriptionAnnotation, Property property) {
		if (propertyDescriptionAnnotation.expandable()) {
			ResourceShape nestedResourceShape = new ResourceShape();
			Method nestedResourceGetter = ReflectionHelper.findGetterForPropertyName(resourceClass, property.getName());
			Class<?> nestedResourceClass = nestedResourceGetter.getReturnType();
			Method locationGetter = ReflectionHelper.findGetterForPropertyName(nestedResourceClass, "Location");
			if (locationGetter == null)
				throw new IllegalArgumentException(NLS.bind("Could not find property named {0} in {1}", new Object[] {"Location", nestedResourceClass}));
			nestedResourceShape.addProperty(new Property("Location"));
			property.setResourceShape(nestedResourceShape);
		}
	}

	/**
	 * Reads property names from the given selection query.
	 *
	 * @param selectionQuery selection query
	 * @return property names from the selection query
	 */
	private static ResourceShape parseSelectionQuery(String selectionQuery) {
		ANTLRStringStream in = new ANTLRStringStream(selectionQuery);
		ResourceShapeLexer lexer = new ResourceShapeLexer(in);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ResourceShapeParser parser = new ResourceShapeParser(tokens);
		try {
			parse_return parsed = parser.parse();
			CommonTree tree = parsed.tree;
			return convertToResourceShape(tree);
		} catch (RecognitionException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static ResourceShape convertToResourceShape(CommonTree tree) {
		List<Property> properties = new ArrayList<Property>();
		@SuppressWarnings("unchecked")
		List<CommonTree> children = (List<CommonTree>) tree.getChildren();
		for (CommonTree child : children) {
			int type = child.getType();
			switch (type) {
				case ResourceShapeParser.PROPERTY_NAME :
					properties.add(new Property(child.getText()));
					break;
				case ResourceShapeParser.NESTED_PROPERTIES :
					String propertyName = child.getChild(0).getText();
					ResourceShape nestedResourceShape = convertToResourceShape((CommonTree)child.getChild(1));
					Property property = new Property(propertyName);
					property.setResourceShape(nestedResourceShape);
					properties.add(property);
					break;
				case ResourceShapeParser.WILDCARD :
					properties.add(Property.ALL_PROPERTIES);
					break;
			}
		}
		ResourceShape resourceShape = new ResourceShape();
		resourceShape.setProperties(properties.toArray(new Property[properties.size()]));
		return resourceShape;
	}

	private static void validateGetMethodExist(Class<?> resourceClass, ResourceShape resourceShape) {
		for (Property property : resourceShape.getProperties()) {
			// TODO: validate nested ResourceShapes
			ReflectionHelper.findGetterForPropertyName(resourceClass, property.getName());
		}
	}

	private static ResourceShape expandWildcards(Class<?> resourceClass, ResourceShape resourceShape) {
		for (Property property : resourceShape.getProperties()) {
			if (property.getResourceShape() != null) {
				Method getter = ReflectionHelper.findGetterForPropertyName(resourceClass, property.getName());
				Class<?> nestedResourceClass = getter.getReturnType();
				property.setResourceShape(expandWildcards(nestedResourceClass, property.getResourceShape()));
			} else if (property.equals(Property.ALL_PROPERTIES)) {
				List<Property> properties = findAllProperties(resourceClass);
				resourceShape.setProperties(properties.toArray(new Property[properties.size()]));
			}
		}
		return resourceShape;
	}
}
