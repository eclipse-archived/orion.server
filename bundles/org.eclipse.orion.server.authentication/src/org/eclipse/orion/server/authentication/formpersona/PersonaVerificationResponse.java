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
package org.eclipse.orion.server.authentication.formpersona;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Encapsulates the result of a call to the Persona Remote Verification API.
 */
public class PersonaVerificationResponse {
	private PersonaVerificationSuccess success;
	private PersonaVerificationFailure failure;

	public PersonaVerificationResponse(String jsonResponse) throws PersonaException {
		try {
			JSONObject jsonObject = new JSONObject(jsonResponse);
			String status = jsonObject.getString(PersonaConstants.KEY_STATUS);
			if ("okay".equals(status)) { //$NON-NLS-1$
				this.success = new PersonaVerificationSuccess(jsonObject.getString(PersonaConstants.KEY_EMAIL), jsonObject.getString(PersonaConstants.KEY_AUDIENCE), jsonObject.getString(PersonaConstants.KEY_ISSUER), jsonObject.getLong(PersonaConstants.KEY_EXPIRES));
			} else if ("failure".equals(status)) { //$NON-NLS-1$
				this.failure = new PersonaVerificationFailure(jsonObject.getString(PersonaConstants.KEY_REASON));
			} else {
				throw new PersonaException("Unknown status: " + status);
			}
		} catch (JSONException e) {
			throw new PersonaException(e);
		}
	}

	public PersonaVerificationSuccess getSuccess() {
		return this.success;
	}

	public PersonaVerificationFailure getFailure() {
		return this.failure;
	}
}
