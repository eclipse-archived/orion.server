/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest.v2;

import org.json.JSONException;
import org.json.JSONObject;

public class ParserException extends AbstractManifestException {
	private static final long serialVersionUID = 1L;

	private String message;
	private int lineNumber;

	public ParserException(String message, int lineNumber) {
		this.message = message;
		this.lineNumber = lineNumber;
	}

	@Override
	public JSONObject getDetails() {
		try {

			JSONObject details = new JSONObject();
			details.put(ERROR_LINE, lineNumber);
			details.put(ERROR_MESSAGE, getMessage());
			return details;

		} catch (JSONException ex) {
			return null;
		}
	}

	@Override
	public String getMessage() {
		return message;
	}
}
