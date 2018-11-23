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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.osgi.util.NLS;

public class ReflectionHelper {
	private static final String METHOD_NAME_GET = "get"; //$NON-NLS-1$
	private static final String METHOD_NAME_IS = "is"; //$NON-NLS-1$

	public static Method findGetterForPropertyName(Class<?> resourceClass, String propertyName) {
		Class<?> currentResourceClass = resourceClass;
		do {
			for (Method method : currentResourceClass.getDeclaredMethods()) {
				if (method.getParameterTypes().length == 0) {
					String methodName = method.getName();
					int methodNameLength = methodName.length();
					if (((methodName.startsWith(METHOD_NAME_GET)) && (methodNameLength > METHOD_NAME_GET.length())) || ((methodName.startsWith(METHOD_NAME_IS)) && (methodNameLength > METHOD_NAME_IS.length()))) {
						PropertyDescription propertyDescriptionAnnotation = ReflectionHelper.getAnnotation(method, PropertyDescription.class);
						if (propertyDescriptionAnnotation != null) {
							String propertyDescriptionName = propertyDescriptionAnnotation.name();
							if (propertyName.equals(propertyDescriptionName)) {
								return method;
							}
						}
					}
				}
			}
			currentResourceClass = currentResourceClass.getSuperclass();
		} while (currentResourceClass != null);
		throw new IllegalArgumentException(NLS.bind("Could not find property named {0} in {1}", new Object[] {propertyName, resourceClass}));
	}

	public static Object callGetter(Object object, Method method) {
		try {
			method.setAccessible(true);
			return method.invoke(object);
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, e.getMessage(), e));
		}
		return null;
	}

	public static <T extends Annotation> T getAnnotation(final Method method, final Class<T> annotationClass) {
		T annotation = method.getAnnotation(annotationClass);
		if (annotation != null)
			return annotation;

		final Class<?> declaringClass = method.getDeclaringClass();
		Class<?> superClass = declaringClass.getSuperclass();

		while (superClass != null) {
			try {
				final Method superClassMethod = superClass.getDeclaredMethod(method.getName(), method.getParameterTypes());
				return getAnnotation(superClassMethod, annotationClass);
			} catch (final Exception exception) {
				// Ignore and exit here
				return null;
			}
		}
		return null;
	}

	public static Field findFieldForName(Class<?> resourceClass, String resourceShapeFieldName) {
		Class<?> currentResourceClass = resourceClass;
		do {
			try {
				return currentResourceClass.getDeclaredField(resourceShapeFieldName);
			} catch (NoSuchFieldException e) {
				// ignore and continue with superclass
			}
			currentResourceClass = currentResourceClass.getSuperclass();
		} while (currentResourceClass != null);
		throw new IllegalArgumentException(NLS.bind("Could not field named {0}", resourceShapeFieldName));
	}

	@SuppressWarnings("unchecked")
	public static <T> T getValue(Field field) {
		try {
			field.setAccessible(true);
			return (T) field.get(null);
		} catch (Exception e) {
			// Ignore and return null
		}
		return null;
	}
}
