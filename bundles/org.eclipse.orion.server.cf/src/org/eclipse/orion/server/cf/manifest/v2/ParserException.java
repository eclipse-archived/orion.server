/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest.v2;

import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

public class ParserException extends AbstractManifestException {
	private static final long serialVersionUID = 1L;

	private String message;
	private Token token;

	public ParserException(String message, Token token) {
		this.message = message;
		this.token = token;
	}

	@Override
	public JSONObject getDetails() {
		try {

			JSONObject details = new JSONObject();
			details.put(ERROR_LINE, token.getLineNumber());
			details.put(ERROR_MESSAGE, getMessage());
			return details;

		} catch (JSONException ex) {
			return null;
		}
	}

	@Override
	public String getMessage() {
		return NLS.bind(message, token.getLineNumber(), token.getContent());
	}
}
