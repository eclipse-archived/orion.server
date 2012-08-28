/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import org.osgi.service.prefs.BackingStoreException;

/**
 * Reads authorization data from preferences. Supports migration of
 * preference data formats.
 */
public abstract class AuthorizationReader {
	static AuthorizationReader readerV1 = new AuthorizationReaderV1();
	static AuthorizationReader readerV3 = new AuthorizationReaderV3();
	/**
	 * The current version of the authorization data storage format.
	 */
	private static final int CURRENT_VERSION = 3;

	public static JSONArray getAuthorizationData(String userId, IEclipsePreferences preferences) throws JSONException {
		int version = preferences.getInt(ProtocolConstants.KEY_USER_RIGHTS_VERSION, 1);
		AuthorizationReader reader;
		switch (version) {
			case 1 :
			case 2 :
				//use same reader for v1 and v2 because it recomputes from workspace data
				reader = readerV1;
				break;
			case 3 :
				reader = readerV3;
				break;
			default :
				throw new RuntimeException("Unsupported auth data version: " + version); //$NON-NLS-1$
		}
		JSONArray authInfo = reader.readAuthorizationInfo(userId, preferences);
		try {
			//always update to newest format
			if (version != CURRENT_VERSION)
				saveRights(preferences, authInfo);
		} catch (BackingStoreException e) {
			//don't need to persistent now - if there are changes we will try later
		}
		return authInfo;
	}

	/**
	 * Returns a JSONArray of authorization data. The array entries
	 * are JSON objects providing details on a particular right.
	 */
	abstract JSONArray readAuthorizationInfo(String userId, IEclipsePreferences preferences) throws JSONException;

	static void saveRights(IEclipsePreferences result, JSONArray userRightArray) throws BackingStoreException {
		result.put(ProtocolConstants.KEY_USER_RIGHTS, userRightArray.toString());
		result.putInt(ProtocolConstants.KEY_USER_RIGHTS_VERSION, CURRENT_VERSION);
		result.flush();
	}
}
