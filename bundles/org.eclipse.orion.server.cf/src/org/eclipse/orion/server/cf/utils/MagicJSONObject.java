/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.utils;

import org.json.JSONException;
import org.json.JSONObject;

public class MagicJSONObject extends JSONObject {

	public MagicJSONObject(String json) throws JSONException {
		super(json);
	}

	public JSONObject putOnce(String key, Object value) throws JSONException {
		Object storedValue;
		if (key != null && value != null) {
			if ((storedValue = this.opt(key)) != null) {
				if (!storedValue.equals(value)) // Throw if values are different
					throw new JSONException("Duplicate key \"" + key + "\"");
				else
					return this;
			}
			this.put(key, value);
		}
		return this;
	}
}