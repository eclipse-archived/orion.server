/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtils {

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static Object callMethod(Object object, String name, Object args[]) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Class types[] = new Class[args.length];
		for (int i = 0; i < args.length; i++) {
			types[i] = args[i].getClass();
		}
		Method method = null;
		Class clazz = object.getClass();
		NoSuchMethodException ex = null;
		while (method == null && clazz != null) {
			try {
				method = clazz.getDeclaredMethod(name, types);
			} catch (NoSuchMethodException e) {
				if (ex == null) {
					ex = e;
				}
				clazz = clazz.getSuperclass();
			}
		}
		if (method == null) {
			if (ex != null) {
				throw ex;
			}
		}
		Object ret = null;
		if (method != null) {
			method.setAccessible(true);
			ret = method.invoke(object, args);
		}
		return ret;
	}

	@SuppressWarnings("rawtypes")
	public static Object callConstructor(Class clazz, Object args[]) throws IllegalAccessException, InvocationTargetException, InstantiationException {
		Class types[] = new Class[args.length];
		for (int i = 0; i < args.length; i++) {
			types[i] = args[i].getClass();
		}
		IllegalArgumentException ex = null;
		Object newInstance = null;
		while (newInstance == null && clazz != null) {
			Constructor[] constructors = clazz.getDeclaredConstructors();
			for (Constructor c : constructors) {
				c.setAccessible(true);
				try {
					newInstance = c.newInstance(args);
				} catch (IllegalArgumentException e) {
					if (ex == null) {
						ex = e;
					}
				}
			}
			clazz = clazz.getSuperclass();
		}
		if (newInstance == null) {
			if (ex != null) {
				throw ex;
			}
		}
		return newInstance;
	}

	public static Object getField(Object object, String name) throws IllegalArgumentException, IllegalAccessException, SecurityException, NoSuchFieldException {
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		Object ret = field.get(object);
		return ret;
	}

}
