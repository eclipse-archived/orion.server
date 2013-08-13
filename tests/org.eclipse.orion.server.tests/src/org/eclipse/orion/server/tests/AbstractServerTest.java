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
package org.eclipse.orion.server.tests;

import java.io.UnsupportedEncodingException;
import java.net.URI;

import junit.framework.Assert;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserServiceHelper;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebRequest;

/**
 * Base class for all Orion server tests. Providers helper methods common
 * to all server tests.
 */
public class AbstractServerTest {

	protected static String testUserLogin = "test";
	protected static String testUserPassword = "test";
	protected String testUserId;

	public static final String SERVER_LOCATION = ServerTestsActivator.getServerLocation();
	public static final URI SERVER_URI = URI.create(SERVER_LOCATION);
	public static final URI SERVER_PATH_URI = URI.create(SERVER_URI.getRawPath());

	public static void setAuthentication(WebRequest request) {
		setAuthentication(request, testUserLogin, testUserPassword);
	}

	public void setUpAuthorization() throws CoreException {
		User testUser = createUser(testUserLogin, testUserPassword);
		testUserId = testUser.getUid();
		//by default allow tests to modify anything
		AuthorizationService.addUserRight(testUserId, "/");
		AuthorizationService.addUserRight(testUserId, "/*");
	}

	/**
	 * Returns the user id of the test user.
	 */
	protected String getTestUserId() {
		return createUser(testUserLogin, testUserPassword).getUid();
	}

	/**
	 * Returns the user with the given login. If the user doesn't exist,
	 * one is created with the provided id and password.
	 */
	protected User createUser(String login, String password) {
		IOrionCredentialsService userAdmin = UserServiceHelper.getDefault().getUserStore();
		if (userAdmin.getUser("login", login) != null)
			return userAdmin.getUser("login", login);
		User newUser = new User(login, "", password);
		newUser = userAdmin.createUser(newUser);
		Assert.assertNotNull(newUser);
		//persist new user in metadata store
		UserInfo userInfo = new UserInfo();
		userInfo.setUniqueId(newUser.getUid());
		userInfo.setUserName(login);
		userInfo.setFullName(login);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			// this should never happen
			e.printStackTrace();
		}
		return newUser;
	}

	protected static void setAuthentication(WebRequest request, String user, String pass) {
		try {
			request.setHeaderField("Authorization", "Basic " + new String(Base64.encode((user + ":" + pass).getBytes()), "UTF8"));
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
		}
	}

	protected static String toAbsoluteURI(String location) {
		return SERVER_URI.resolve(location).toString();
	}

	protected static String toRelativeURI(String location) {
		URI locationURI = URI.create(location);
		if (locationURI.isAbsolute()) {
			return SERVER_URI.relativize(URI.create(location)).toString();
		}
		return SERVER_PATH_URI.relativize(URI.create(location)).toString();
	}

	/**
	 * Returns a GET request for the given location.
	 */
	protected static WebRequest getGetRequest(String location) {
		String requestURI = toAbsoluteURI(location);
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
