/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace.authorization;

import org.eclipse.orion.server.core.users.OrionScope;

import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServerStatus;

import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.osgi.service.prefs.BackingStoreException;

public class AuthorizationService {

	public static void addUserRight(String name, String uri) throws CoreException {
		try {
			IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
			IEclipsePreferences result = (IEclipsePreferences) users.node(name);
			String userRights = result.get(ProtocolConstants.KEY_USER_RIGHTS, null);

			JSONArray userRightArray = (userRights != null ? new JSONArray(userRights) : new JSONArray());

			userRightArray.put(uri);
			result.put(ProtocolConstants.KEY_USER_RIGHTS, userRightArray.toString());
			result.flush();
		} catch (Exception e) {
			String msg = "Error persisting user rights";
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
	}

	public static void removeUserRight(String name, String uri) throws JSONException, BackingStoreException {
		IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
		IEclipsePreferences result = (IEclipsePreferences) users.node(name);
		String userRights = result.get(ProtocolConstants.KEY_USER_RIGHTS, null);

		if (userRights == null)
			return;

		JSONArray userRightArray = new JSONArray(userRights);
		for (int i = 0; i < userRightArray.length(); i++) {
			if (uri.equals(userRightArray.get(i)))
				userRightArray.remove(i);
		}

		result.put(ProtocolConstants.KEY_USER_RIGHTS, userRightArray.toString());
		result.flush();
	}

	public static boolean checkRights(String name, String uri) throws JSONException {
		if (uri.equals("/workspace"))
			return true;

		IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
		IEclipsePreferences result = (IEclipsePreferences) users.node(name);
		String userRights = result.get(ProtocolConstants.KEY_USER_RIGHTS, null);

		if (userRights == null)
			return false;

		JSONArray userRightArray = new JSONArray(userRights);
		for (int i = 0; i < userRightArray.length(); i++) {
			if (uri.startsWith((String) userRightArray.get(i)))
				return true;
		}

		return false;
	}
}
