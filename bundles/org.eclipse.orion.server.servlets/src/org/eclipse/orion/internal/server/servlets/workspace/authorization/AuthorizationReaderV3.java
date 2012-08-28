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
package org.eclipse.orion.internal.server.servlets.workspace.authorization;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Reads version 3 of the authorization data.
 */
public class AuthorizationReaderV3 extends AuthorizationReader {
	@Override
	JSONArray readAuthorizationInfo(String userId, IEclipsePreferences preferences) throws JSONException {
		return new JSONArray(preferences.get(ProtocolConstants.KEY_USER_RIGHTS, "[]")); //$NON-NLS-1$
	}
}
