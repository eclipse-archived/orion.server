/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class IsJSONArrayEqual extends TypeSafeMatcher<JSONArray> {

	private JSONArray expected;

	public IsJSONArrayEqual(JSONArray expected) {
		this.expected = expected;
	}

	public boolean matchesSafely(JSONArray actual) {
		try {
			if (actual.length() != expected.length())
				return false;
			int max = expected.length();
			for (int i = 0; i < max; i++) {
				Object o1 = expected.get(i);
				Object o2 = actual.get(i);

				if (o1 instanceof JSONObject && o2 instanceof JSONObject) {
					JSONObject jo1 = (JSONObject) o1;
					JSONObject jo2 = (JSONObject) o2;
					if (!new IsJSONObjectEqual(jo1).matchesSafely(jo2))
						return false;
				} else {
					return false;
				}
			}
			return true;
		} catch (JSONException e) {
			return false;
		}
	}

	public void describeTo(Description description) {
		description.appendText(expected.toString());
	}

	@Factory
	public static <T> Matcher<JSONArray> isJSONArrayEqual(JSONArray expected) {
		return new IsJSONArrayEqual(expected);
	}
}
