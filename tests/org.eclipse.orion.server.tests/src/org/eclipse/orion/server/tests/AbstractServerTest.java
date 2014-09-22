/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests;

import static org.junit.Assert.assertNotNull;

import java.io.UnsupportedEncodingException;
import java.net.URI;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.WebRequest;

/**
 * Base class for all Orion server tests. Providers helper methods common
 * to all server tests.
 */
public class AbstractServerTest {

	protected static String testUserLogin;
	protected static String testUserPassword;
	protected String testUserId;

	public static final String SERVER_LOCATION = ServerTestsActivator.getServerLocation();
	public static final URI SERVER_URI = URI.create(SERVER_LOCATION);
	public static final URI SERVER_PATH_URI = URI.create(SERVER_URI.getRawPath());

	@Rule
	public TestName testName = new TestName();

	@Before
	public void before() {
		// the test user by default is the test method name
		testUserLogin = testName.getMethodName();
		testUserPassword = testName.getMethodName();

		// set to true to enable HttpUnit logging
		HttpUnitOptions.setLoggingHttpHeaders(false);
	}

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
		// see if the user exists already
		IOrionCredentialsService userAdmin = UserServiceHelper.getDefault().getUserStore();
		if (userAdmin.getUser(UserConstants.KEY_LOGIN, login) != null)
			return userAdmin.getUser(UserConstants.KEY_LOGIN, login);

		// create new user in metadata store
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(login);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			return null;
		}

		// create new user in the user store
		User newUser = new User(userInfo.getUniqueId(), login, "", password);
		newUser = userAdmin.createUser(newUser);
		assertNotNull(newUser);
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
