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

import java.util.Iterator;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class IsJSONObjectEqual extends TypeSafeMatcher<JSONObject> {

	private JSONObject expected;

	public IsJSONObjectEqual(JSONObject expected) {
		this.expected = expected;
	}

	@Override
	public boolean matchesSafely(JSONObject actual) {
		JSONObject jo1 = expected;
		JSONObject jo2 = actual;
		try {
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
					} else if (v1 instanceof Integer && v2 instanceof Integer) {
						Integer l1 = (Integer) v1;
						Integer l2 = (Integer) v2;
						if (!l1.equals(l2))
							return false;
					} else if (v1 instanceof JSONObject && v2 instanceof JSONObject) {
						JSONObject jv1 = (JSONObject) v1;
						JSONObject jv2 = (JSONObject) v2;
						if (!new IsJSONObjectEqual(jv1).matchesSafely(jv2))
							return false;
					} else {
						return false;
					}
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
	public static <T> Matcher<JSONObject> isJSONObjectEqual(JSONObject expected) {
		return new IsJSONObjectEqual(expected);
	}
}
