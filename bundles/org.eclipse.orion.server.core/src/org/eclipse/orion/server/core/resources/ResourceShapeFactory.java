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

import java.lang.reflect.Field;

import org.eclipse.core.runtime.Assert;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;

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
			return getDefaultResourceShape(resourceClass);
		} else {
			String resourceShapeFieldName = parseSelectionQuery(selectionQuery);
			return getResourceShape(resourceClass, resourceShapeFieldName);
		}
	}

	private static ResourceShape getDefaultResourceShape(Class<?> resourceClass) {
		// TODO: don't use hard-coded field name, replace with annontation or enum
		return getResourceShape(resourceClass, "DEFAULT_RESOURCE_SHAPE"); //$NON-NLS-1$
	}

	private static ResourceShape getResourceShape(Class<?> resourceClass, String resourceShapeFieldName) {
		Field field = ReflectionHelper.findFieldForName(resourceClass, resourceShapeFieldName);
		return ReflectionHelper.getValue(field);
	}

	private static String parseSelectionQuery(String selectionQuery) {
		// TODO: find filed name with a resource shape for the given selection query
		return selectionQuery;
	}
}
