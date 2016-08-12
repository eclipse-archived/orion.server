/*******************************************************************************
 * Copyright (c) 2013, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.utils;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONException;
import org.json.JSONObject;

public class HttpUtil {
	/**
	 * Default socket connection timeout.
	 */
	private static final int DEFAULT_SOCKET_TIMEOUT = 300000;//five minutes

	public static ServerStatus configureHttpMethod(HttpMethod method, Cloud cloud) throws JSONException {
		method.addRequestHeader(new Header("Accept", "application/json"));
		method.addRequestHeader(new Header("Content-Type", "application/json"));
		//set default socket timeout for connection
		HttpMethodParams params = method.getParams();
		params.setSoTimeout(DEFAULT_SOCKET_TIMEOUT);
		params.setContentCharset("UTF-8");
		method.setParams(params);
		if (cloud.getAccessToken() != null){
			method.addRequestHeader(new Header("Authorization", "bearer " + cloud.getAccessToken().getString("access_token")));
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
		}
		
		JSONObject errorJSON = new JSONObject();
		try {
			errorJSON.put(CFProtocolConstants.V2_KEY_ERROR_CODE, "CF-NotAuthenticated");
			errorJSON.put(CFProtocolConstants.V2_KEY_ERROR_DESCRIPTION, "Not authenticated");
		} catch (JSONException e) {
			// do nothing
		}
		return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated", errorJSON, null);
	}

	public static ServerStatus executeMethod(HttpMethodBase method) throws HttpException, IOException, JSONException {
		try {
			int code = CFActivator.getDefault().getHttpClient().executeMethod(method);

			if (code == 204) {
				/* no content response */
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
			}

			String response = method.getResponseBodyAsString(67108864);
			JSONObject result;

			try {
				result = new MagicJSONObject(response);
			} catch (JSONException e) {
				result = new JSONObject();
				result.put("response", response);
			}

			if (code != 200 && code != 201) {
				String desctiption = result.optString("description");
				if (desctiption == null || desctiption.length() == 0) {
					desctiption = result.optString("response", "Could not connect to host. Error: " + code);
					if (desctiption.length() > 1000) {
						desctiption = "Could not connect to host. Error: " + code;
					}
				}
				return new ServerStatus(Status.ERROR, code, desctiption, result, null);
			}

			if (result.has("error_code")) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, result.optString("description"), result, null);
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);

		} finally {
			/* ensure connections are released back to the connection manager */
			method.releaseConnection();
		}
	}

	public static ServerStatus createErrorStatus(int severity, String errorCode, String description) {
		JSONObject errorJSON = new JSONObject();
		try {
			errorJSON.put(CFProtocolConstants.V2_KEY_ERROR_CODE, errorCode);
			errorJSON.put(CFProtocolConstants.V2_KEY_ERROR_DESCRIPTION, description);
		} catch (JSONException e) {
			// do nothing
		}

		return new ServerStatus(severity, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, description, errorJSON, null);
	}
	
	public static ServerStatus createErrorStatus(int severity, String errorCode, String description, JSONObject metadata) {
		JSONObject errorJSON = new JSONObject();
		try {
			errorJSON.put(CFProtocolConstants.V2_KEY_ERROR_CODE, errorCode);
			errorJSON.put(CFProtocolConstants.V2_KEY_ERROR_DESCRIPTION, description);
			errorJSON.put(CFProtocolConstants.V2_KEY_METADATA, metadata);
		} catch (JSONException e) {
			// do nothing
		}

		return new ServerStatus(severity, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, description, errorJSON, null);
	}
}
