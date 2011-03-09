/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace.authorization;

import java.util.*;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.server.core.users.OrionScope;
import org.json.*;
import org.osgi.service.prefs.BackingStoreException;

public class AuthorizationService {

	public static final int POST = 1;
	public static final int PUT = 2;
	public static final int GET = 4;
	public static final int DELETE = 8;

	/**
	 * The current version of the authorization data storage format.
	 */
	private static final int CURRENT_VERSION = 2;

	private static int getMethod(String methodName) {
		if (methodName.equals("POST"))
			return 1;
		if (methodName.equals("PUT"))
			return 2;
		if (methodName.equals("GET"))
			return 4;
		if (methodName.equals("DELETE"))
			return 8;
		return 0;
	}

	public static void addUserRight(String name, String uri) throws CoreException {
		try {
			IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
			IEclipsePreferences result = (IEclipsePreferences) users.node(name);
			JSONArray userRightArray = AuthorizationReader.getAuthorizationData(result);

			// adds all rights for the uri
			JSONObject userRight = new JSONObject();
			userRight.put(ProtocolConstants.KEY_USER_RIGHT_URI, uri);
			userRight.put(ProtocolConstants.KEY_USER_RIGHT_METHOD, POST | PUT | GET | DELETE);
			userRightArray.put(userRight);

			saveRights(result, userRightArray);
		} catch (Exception e) {
			String msg = "Error persisting user rights";
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
	}

	public static void removeUserRight(String name, String uri) throws JSONException, BackingStoreException {
		IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
		IEclipsePreferences result = (IEclipsePreferences) users.node(name);
		JSONArray userRightArray = AuthorizationReader.getAuthorizationData(result);
		for (int i = 0; i < userRightArray.length(); i++) {
			if (uri.equals(((JSONObject) userRightArray.get(i)).get(ProtocolConstants.KEY_USER_RIGHT_URI)))
				userRightArray.remove(i);
		}
		saveRights(result, userRightArray);
	}

	private static void saveRights(IEclipsePreferences result, JSONArray userRightArray) throws BackingStoreException {
		result.put(ProtocolConstants.KEY_USER_RIGHTS, userRightArray.toString());
		result.putInt(ProtocolConstants.KEY_USER_RIGHTS_VERSION, CURRENT_VERSION);
		result.flush();
	}

	/**
	 * Returns a list of all rights granted to the given user.
	 */
	private static List<String> getRights(String name) {
		IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
		IEclipsePreferences result = (IEclipsePreferences) users.node(name);
		try {
			JSONArray userRightArray = AuthorizationReader.getAuthorizationData(result);
			List<String> list = new ArrayList<String>();
			for (int i = 0; i < userRightArray.length(); i++) {
				list.add(((JSONObject) userRightArray.get(i)).getString(ProtocolConstants.KEY_USER_RIGHT_URI));
			}
			return list;
		} catch (JSONException e) {
			return Collections.emptyList();
		}
	}

	/**
	 * Returns all users that have the given rights granted to them.
	 */
	public static List<String> findUserWithRights(String rightToFind) {
		IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
		List<String> matches = new ArrayList<String>();
		String[] usernames;
		try {
			usernames = users.childrenNames();
			for (String user : usernames) {
				for (String right : getRights(user)) {
					if (rightToFind.startsWith(right))
						matches.add(user);
				}
			}
		} catch (BackingStoreException e) {
			return null;
		}
		return matches;
	}

	public static boolean checkRights(String name, String uri, String method) throws JSONException {
		if (uri.equals("/workspace")) //$NON-NLS-1$
			return true;

		// Any user can access their site configurations
		if (uri.startsWith("/site")) //$NON-NLS-1$
			return true;

		// Any user can access their own profile
		if (uri.equals("/users/" + name)) //$NON-NLS-1$
			return true;

		//import/export rights depend on access to the file content
		if (uri.startsWith("/xfer/export/") && uri.endsWith(".zip")) { //$NON-NLS-1$ //$NON-NLS-2$
			uri = "/file/" + uri.substring(13, uri.length() - 4); //$NON-NLS-1$
		} else if (uri.startsWith("/xfer/")) { //$NON-NLS-1$
			uri = "/file/" + uri.substring(6); //$NON-NLS-1$
		}

		String projectWorldReadable = System.getProperty("org.eclipse.orion.server.core.projectsWorldReadable"); //$NON-NLS-1$
		if (getMethod(method) == GET && uri.startsWith("/file/") && "true".equalsIgnoreCase(projectWorldReadable)) //$NON-NLS-1$ //$NON-NLS-2$
			return true;

		IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
		JSONArray userRightArray = AuthorizationReader.getAuthorizationData((IEclipsePreferences) users.node(name));
		for (int i = 0; i < userRightArray.length(); i++) {
			JSONObject userRight = (JSONObject) userRightArray.get(i);

			String uriToMatch = uri.toLowerCase(Locale.ENGLISH);
			String patternToMatch = userRight.getString(ProtocolConstants.KEY_USER_RIGHT_URI).toLowerCase(Locale.ENGLISH);
			int methodToMatch = userRight.getInt(ProtocolConstants.KEY_USER_RIGHT_METHOD);
			if (wildCardMatch(uriToMatch, patternToMatch) && ((getMethod(method) & methodToMatch) == getMethod(method)))
				return true;
		}

		return false;
	}

	private static boolean wildCardMatch(String text, String pattern) {
		String[] cards = pattern.split("\\*");
		if (!pattern.startsWith("*") && !text.startsWith(cards[0])) {
			return false;
		}
		if (!pattern.endsWith("*") && !text.endsWith(cards[cards.length - 1])) {
			return false;
		}

		for (String card : cards) {
			int idex = text.indexOf(card);
			if (idex == -1) {
				return false;
			}
			text = text.substring(idex + card.length());
		}

		return true;
	}
}
