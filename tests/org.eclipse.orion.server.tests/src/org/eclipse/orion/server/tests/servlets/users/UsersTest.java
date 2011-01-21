/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.users;

import org.eclipse.orion.server.tests.AbstractServerTest;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;

import org.eclipse.orion.server.core.resources.Base64;

import com.meterware.httpunit.*;
import java.io.UnsupportedEncodingException;
import java.util.Map;

public abstract class UsersTest extends AbstractServerTest {

	public static final String SERVER_LOCATION = ServerTestsActivator.getServerLocation();

	private static void setAuthenticationUser(WebRequest request) {

		try {
			request.setHeaderField("Authorization", "Basic " + new String(Base64.encode("test:test".getBytes()), "UTF8"));
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
		}

	}

	private static void setAuthenticationAdmin(WebRequest request) {

		try {
			request.setHeaderField("Authorization", "Basic " + new String(Base64.encode("admin:admin".getBytes()), "UTF8"));
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
		}

	}

	protected static WebRequest getPostUsersRequest(String uri, Map<String, String> params, boolean admin) {
		WebRequest request = new PostMethodWebRequest(SERVER_LOCATION + "/useradmin/" + uri);
		request.setHeaderField("Orion-Version", "1");
		for (String key : params.keySet()) {
			request.setParameter(key, params.get(key));
		}
		if (admin) {
			setAuthenticationAdmin(request);
		} else {
			setAuthenticationUser(request);
		}
		return request;
	}

	protected static WebRequest getDeleteUsersRequest(String uri, boolean admin) {
		WebRequest request = new DeleteMethodWebRequest(SERVER_LOCATION + "/useradmin/" + uri);
		request.setHeaderField("Orion-Version", "1");
		if (admin) {
			setAuthenticationAdmin(request);
		} else {
			setAuthenticationUser(request);
		}
		return request;
	}

	protected static WebRequest getDeleteUsersRequest(String uri, Map<String, String> params, boolean admin) {
		WebRequest request = new DeleteMethodWebRequest(SERVER_LOCATION + "/useradmin/" + uri);
		request.setHeaderField("Orion-Version", "1");
		for (String key : params.keySet()) {
			request.setParameter(key, params.get(key));
		}
		if (admin) {
			setAuthenticationAdmin(request);
		} else {
			setAuthenticationUser(request);
		}
		return request;
	}

	protected static WebRequest getGetUsersRequest(String uri, boolean admin) {
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/useradmin/" + uri);
		request.setHeaderField("Orion-Version", "1");
		if (admin) {
			setAuthenticationAdmin(request);
		} else {
			setAuthenticationUser(request);
		}
		return request;
	}

	private static String addPutParamethers(String requestStr, Map<String, String> params) {
		StringBuilder request = new StringBuilder(requestStr);
		String delim = requestStr.contains("?") ? "&" : "?";
		for (String key : params.keySet()) {
			request.append(delim);
			request.append(key);
			request.append("=");
			request.append(params.get(key));
			delim = "&";
		}
		return request.toString();
	}

	protected static WebRequest getPutUsersRequest(String uri, Map<String, String> params, boolean admin) throws UnsupportedEncodingException {
		WebRequest request = new PutMethodWebRequest(addPutParamethers(SERVER_LOCATION + "/useradmin/" + uri, params), getJsonAsStream(""), "text/plain");
		request.setHeaderField("Orion-Version", "1");
		if (admin) {
			setAuthenticationAdmin(request);
		} else {
			setAuthenticationUser(request);
		}
		return request;
	}
}
