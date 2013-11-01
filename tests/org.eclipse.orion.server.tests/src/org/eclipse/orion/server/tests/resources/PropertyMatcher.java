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

import org.eclipse.orion.server.core.resources.Property;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class PropertyMatcher extends TypeSafeMatcher<Property> {

	protected final Property property;

	protected PropertyMatcher(final Property property) {
		this.property = property;
	}

	@Override
	public boolean matchesSafely(Property item) {
		return property.getName().equals(item.getName());
	}

	public void describeTo(Description description) {
		description.appendText(" a property named ").appendText(property.getName());
	}

	@Factory
	public static <T> Matcher<Property> isPropertyNameEqual(Property expected) {
		return new PropertyMatcher(expected);
	}
}