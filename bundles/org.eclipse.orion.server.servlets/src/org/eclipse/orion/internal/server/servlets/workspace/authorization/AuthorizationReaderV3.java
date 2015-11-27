/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace.authorization;

import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Reads version 3 of the authorization data.
 */
public class AuthorizationReaderV3 extends AuthorizationReader {
	@Override
	JSONArray readAuthorizationInfo(UserInfo user) throws JSONException {
		String property = user.getProperty(ProtocolConstants.KEY_USER_RIGHTS);
		return property == null ? new JSONArray() : new JSONArray(property);
	}
}
