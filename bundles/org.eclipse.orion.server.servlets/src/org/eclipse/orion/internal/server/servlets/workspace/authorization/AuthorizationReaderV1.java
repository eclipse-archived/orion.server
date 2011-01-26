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
package org.eclipse.orion.internal.server.servlets.workspace.authorization;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.*;

/**
 * Reads version 1 of the authorization data format.
 */
public class AuthorizationReaderV1 extends AuthorizationReader {
	@Override
	JSONArray readAuthorizationInfo(IEclipsePreferences preferences) throws JSONException {
		//need to convert to the new format that takes an array of objects.
		JSONArray oldArray = new JSONArray(preferences.get(ProtocolConstants.KEY_USER_RIGHTS, "[]")); //$NON-NLS-1$
		JSONArray newArray = new JSONArray();
		for (int i = 0; i < oldArray.length(); i++) {
			JSONObject right = new JSONObject();
			right.put(ProtocolConstants.KEY_USER_RIGHT_URI, oldArray.getString(i));
			right.put(ProtocolConstants.KEY_USER_RIGHT_METHOD, AuthorizationService.POST | AuthorizationService.PUT | AuthorizationService.GET | AuthorizationService.DELETE);
			newArray.put(right);
		}
		return newArray;
	}
}
