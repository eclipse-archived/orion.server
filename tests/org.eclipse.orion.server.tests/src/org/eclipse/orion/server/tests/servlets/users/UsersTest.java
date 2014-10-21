/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.users;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.tests.AbstractServerTest;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONObject;
import org.junit.Before;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;

public abstract class UsersTest extends AbstractServerTest {

	protected static final int METHOD_GET = 0;
	protected static final int METHOD_PUT = 1;
	protected static final int METHOD_POST = 2;
	protected static final int METHOD_DELETE = 3;

	@Before
	public void setUp() throws CoreException {
		setUpAuthorization();
	}

	@Override
	public void setUpAuthorization() throws CoreException {
		UserInfo testUser = createUser(getTestUserName(), getTestUserPassword());
		setTestUserRights(testUser);
		UserInfo adminUser = createUser("admin", "admin");
		setAdminRights(adminUser);
	}

	public abstract void setAdminRights(UserInfo adminUser) throws CoreException;

	public abstract void setTestUserRights(UserInfo testUser) throws CoreException;

	private void setAuthenticationUser(WebRequest request) {
		try {
			request.setHeaderField("Authorization", "Basic " + new String(Base64.encode((getTestUserName() + ":" + getTestUserPassword()).getBytes()), "UTF8"));
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
		}
	}

	private void setAuthenticationAdmin(WebRequest request) {
		try {
			request.setHeaderField("Authorization", "Basic " + new String(Base64.encode("admin:admin".getBytes()), "UTF8"));
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
		}
	}

	/**
	 * @return a string representing the test users name.
	 */
	public String getTestUserName() {
		return "test";
	}

	/**
	 * @return a string representing the test users password.
	 */
	public String getTestUserPassword() {
		return "test";
	}

	protected WebRequest getPostUsersRequest(String uri, Map<String, String> params, boolean admin) {

		try {
			return getAuthenticatedRequest(SERVER_LOCATION + "/users" + (uri.equals("") ? "" : ("/" + uri)), METHOD_POST, admin, params, new JSONObject());
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
			return null;
		}

	}

	protected WebRequest getDeleteUsersRequest(String uri, boolean admin) {
		return getAuthenticatedRequest(SERVER_LOCATION + "/users" + (uri.equals("") ? "" : ("/" + uri)), METHOD_DELETE, admin);
	}

	protected WebRequest getDeleteUsersRequest(String uri, Map<String, String> params, boolean admin) {

		try {
			return getAuthenticatedRequest(SERVER_LOCATION + "/users" + (uri.equals("") ? "" : ("/" + uri)), METHOD_DELETE, admin, params, new JSONObject());
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
			return null;
		}
	}

	protected WebRequest getAuthenticatedRequest(String uri, int method, boolean admin) {
		try {
			return getAuthenticatedRequest(uri, method, admin, null, new JSONObject());
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
			return null;
		}
	}

	protected WebRequest getAuthenticatedRequest(String uri, int method, boolean admin, Map<String, String> params, JSONObject body) throws UnsupportedEncodingException {
		uri = toAbsoluteURI(uri);
		WebRequest request;
		switch (method) {
			case METHOD_DELETE :
				request = new DeleteMethodWebRequest(uri);
				break;
			case METHOD_POST :
				request = new PostMethodWebRequest(uri);
				break;
			case METHOD_PUT :
				request = new PutMethodWebRequest(uri, IOUtilities.toInputStream(body.toString()), "text/plain");
				break;
			default :
				request = new GetMethodWebRequest(uri);
		}

		if (params != null)
			for (String key : params.keySet()) {
				request.setParameter(key, params.get(key));
			}

		request.setHeaderField("Orion-Version", "1");
		if (admin) {
			setAuthenticationAdmin(request);
		} else {
			setAuthenticationUser(request);
		}
		return request;

	}

	protected WebRequest getGetUsersRequest(String uri, boolean admin) {
		return getAuthenticatedRequest(SERVER_LOCATION + "/users" + (uri.equals("") ? "" : ("/" + uri)), METHOD_GET, admin);
	}

	protected WebRequest getPutUsersRequest(String uri, JSONObject body, boolean admin) throws UnsupportedEncodingException {
		return getAuthenticatedRequest(SERVER_LOCATION + "/users/" + uri, METHOD_PUT, admin, null, body);
	}
}
