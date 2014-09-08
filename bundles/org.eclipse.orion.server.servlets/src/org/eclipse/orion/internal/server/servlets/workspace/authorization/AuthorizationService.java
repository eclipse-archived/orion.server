/*******************************************************************************
 * Copyright (c) 2010, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace.authorization;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles access and persistence of user authorization information.
 */
public class AuthorizationService {

	public static final int POST = 1;
	public static final int PUT = 2;
	public static final int GET = 4;
	public static final int DELETE = 8;
	private static final String PREFIX_EXPORT = "/xfer/export/"; //$NON-NLS-1$
	private static final String PREFIX_IMPORT = "/xfer/import/"; //$NON-NLS-1$
	private static final String ANONYMOUS_LOGIN_VALUE = "Anonymous"; //$NON-NLS-1$

	/**
	 * Adds the right for the given user to put, post, get, or delete the given URI.
	 * @param userId The user name
	 * @param uri The URI to grant access to
	 * @throws CoreException If an error occurred persisting user rights.
	 */
	public static void addUserRight(String userId, String uri) throws CoreException {
		try {
			//TODO probably want caller to pass in UserInfo for performance
			UserInfo user = OrionConfiguration.getMetaStore().readUser(userId);
			JSONArray userRightArray = AuthorizationReader.getAuthorizationData(user);

			// adds all rights for the uri
			JSONObject userRight = createUserRight(uri);

			//check if we already have this right
			for (int i = 0; i < userRightArray.length(); i++) {
				if (userRight.toString().equals(userRightArray.get(i).toString()))
					return;
			}

			//add the new right
			userRightArray.put(userRight);

			AuthorizationReader.saveRights(user, userRightArray);
		} catch (Exception e) {
			String msg = "Error persisting user rights";
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
	}

	public static boolean checkRights(String userId, String uri, String method) throws CoreException {
		if (uri.equals(Activator.LOCATION_WORKSPACE_SERVLET) && !ANONYMOUS_LOGIN_VALUE.equals(userId))
			return true;

		// any user can access their site configurations
		if (uri.startsWith("/site") && !ANONYMOUS_LOGIN_VALUE.equals(userId)) //$NON-NLS-1$
			return true;

		// any user can access their own profile
		if (uri.equals("/users/" + userId) && !ANONYMOUS_LOGIN_VALUE.equals(userId)) //$NON-NLS-1$
			return true;

		// any user can access tasks
		if (uri.startsWith("/task")) //$NON-NLS-1$
			return true;

		// import/export rights depend on access to the file content
		if (uri.startsWith(PREFIX_EXPORT) && uri.endsWith(".zip")) { //$NON-NLS-1$
			uri = "/file/" + uri.substring(13, uri.length() - 4) + '/'; //$NON-NLS-1$
		} else if (uri.startsWith(PREFIX_IMPORT)) {
			uri = "/file/" + uri.substring(PREFIX_IMPORT.length()); //$NON-NLS-1$
			if (!uri.endsWith("/")) //$NON-NLS-1$
				uri += '/';
		}

		// allow anonymous read if the corresponding property is set
		String projectWorldReadable = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_ANONYMOUS_READ, "false"); //$NON-NLS-1$
		int methodMask = getMethod(method);
		if (methodMask == GET && uri.startsWith("/file/") && "true".equalsIgnoreCase(projectWorldReadable)) {//$NON-NLS-1$ //$NON-NLS-2$
			// except don't allow access to metadata
			if ("/file/".equals(uri) || uri.startsWith("/file/.metadata/")) //$NON-NLS-1$//$NON-NLS-2$
				return false;
			return true;
		}
		UserInfo user = OrionConfiguration.getMetaStore().readUser(userId);
		JSONArray userRightArray = AuthorizationReader.getAuthorizationData(user);
		for (int i = 0; i < userRightArray.length(); i++) {
			try {
				JSONObject userRight = (JSONObject) userRightArray.get(i);
				String patternToMatch = userRight.getString(ProtocolConstants.KEY_USER_RIGHT_URI);
				int methodToMatch = userRight.getInt(ProtocolConstants.KEY_USER_RIGHT_METHOD);
				if (wildCardMatch(uri, patternToMatch) && ((methodMask & methodToMatch) == methodMask))
					return true;
			} catch (JSONException e) {
				//sk
			}
		}

		return false;
	}

	/**
	 * Create a new user rights object granting all permissions to the given URI.
	 * @throws JSONException 
	 */
	static JSONObject createUserRight(String uri) throws JSONException {
		JSONObject userRight = new JSONObject();
		userRight.put(ProtocolConstants.KEY_USER_RIGHT_URI, uri);
		userRight.put(ProtocolConstants.KEY_USER_RIGHT_METHOD, POST | PUT | GET | DELETE);
		return userRight;
	}

	private static int getMethod(String methodName) {
		if (methodName.equals("POST")) //$NON-NLS-1$
			return 1;
		if (methodName.equals("PUT")) //$NON-NLS-1$
			return 2;
		if (methodName.equals("GET")) //$NON-NLS-1$
			return 4;
		if (methodName.equals("DELETE")) //$NON-NLS-1$
			return 8;
		return 0;
	}

	/**
	 * Removes the right for the given user to put, post, get, or delete the given URI.
	 * @param userId The user name
	 * @param uri The URI to remove access to
	 * @throws CoreException If an error occurred persisting user rights.
	 */
	public static void removeUserRight(String userId, String uri) throws CoreException {
		try {
			@SuppressWarnings("unused")
			Activator r = Activator.getDefault();
			UserInfo user = OrionConfiguration.getMetaStore().readUser(userId);
			JSONArray userRightArray = AuthorizationReader.getAuthorizationData(user);
			for (int i = 0; i < userRightArray.length(); i++) {
				if (uri.equals(((JSONObject) userRightArray.get(i)).get(ProtocolConstants.KEY_USER_RIGHT_URI)))
					userRightArray.remove(i);
			}
			AuthorizationReader.saveRights(user, userRightArray);
		} catch (Exception e) {
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error persisting user rights", e));
		}
	}

	private static boolean wildCardMatch(String text, String pattern) {
		String[] cards = pattern.split("\\*"); //$NON-NLS-1$
		if (!pattern.startsWith("*") && !text.startsWith(cards[0])) { //$NON-NLS-1$
			return false;
		}
		if (!pattern.endsWith("*") && !text.endsWith(cards[cards.length - 1])) { //$NON-NLS-1$
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
