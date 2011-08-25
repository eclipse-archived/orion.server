/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests;

import java.util.Iterator;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.internal.matchers.TypeSafeMatcher;

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
					if (jo1.length() != jo2.length())
						return false;
					for (Iterator<?> it = jo1.keys(); it.hasNext();) {
						Object k1 = it.next();
						if (k1 instanceof String) {
							String s1 = (String) k1;
							if (!jo2.has(s1))
								return false;
							Object v1 = jo1.get(s1);
							Object v2 = jo2.get(s1);
							if (v1 instanceof String && v2 instanceof String) {
								String sv1 = (String) v1;
								String sv2 = (String) v2;
								if (!sv1.equals(sv2))
									return false;
							} else if (v1 instanceof JSONArray && v2 instanceof JSONArray) {
								JSONArray ja1 = (JSONArray) v1;
								JSONArray ja2 = (JSONArray) v2;
								if (!new IsJSONArrayEqual(ja1).matchesSafely(ja2))
									return false;
							} else if (v1 instanceof Long && v2 instanceof Long) {
								Long l1 = (Long) v1;
								Long l2 = (Long) v2;
								if (!l1.equals(l2))
									return false;
							} else {
								return false;
							}
						}
					}
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
