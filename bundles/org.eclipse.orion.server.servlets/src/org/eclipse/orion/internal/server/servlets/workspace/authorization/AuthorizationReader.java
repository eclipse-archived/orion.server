/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace.authorization;

import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.json.JSONArray;
import org.json.JSONException;

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

	public static JSONArray getAuthorizationData(UserInfo user) throws CoreException {
		String versionString = user.getProperty(ProtocolConstants.KEY_USER_RIGHTS_VERSION);
		int version = -1;
		try {
			//assume version 1 if not specified
			version = versionString == null ? 1 : Integer.valueOf(versionString);
		} catch (NumberFormatException e) {
			//ignore and fail below
		}
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
				throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Unsupported auth data version: " + version)); //$NON-NLS-1$
		}
		JSONArray authInfo;
		try {
			authInfo = reader.readAuthorizationInfo(user);
		} catch (JSONException e1) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Failure reading authorization data", e1)); //$NON-NLS-1$
		}
		try {
			//always update to newest format
			if (version != CURRENT_VERSION)
				saveRights(user, authInfo);
		} catch (CoreException e) {
			//don't need to persistent now - if there are changes we will try later
		}
		return authInfo;
	}

	/**
	 * Returns a JSONArray of authorization data. The array entries
	 * are JSON objects providing details on a particular right.
	 */
	abstract JSONArray readAuthorizationInfo(UserInfo user) throws JSONException;

	static void saveRights(UserInfo user, JSONArray userRightArray) throws CoreException {
		user.setProperty(ProtocolConstants.KEY_USER_RIGHTS, userRightArray.toString());
		user.setProperty(ProtocolConstants.KEY_USER_RIGHTS_VERSION, Integer.toString(CURRENT_VERSION));
		OrionConfiguration.getMetaStore().updateUser(user);
	}
}
