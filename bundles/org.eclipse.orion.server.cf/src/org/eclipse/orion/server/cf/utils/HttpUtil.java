/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
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
import org.apache.commons.httpclient.*;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONException;
import org.json.JSONObject;

public class HttpUtil {
	public static void configureHttpMethod(HttpMethod method, Target target) throws JSONException {
		method.addRequestHeader(new Header("Accept", "application/json"));
		method.addRequestHeader(new Header("Content-Type", "application/json"));
		if (target.getAccessToken() != null)
			method.addRequestHeader(new Header("Authorization", "bearer " + target.getAccessToken().getString("access_token")));
	}

	public static ServerStatus executeMethod(HttpMethod method) throws HttpException, IOException, JSONException {
		int code = CFActivator.getDefault().getHttpClient().executeMethod(method);
		String response = method.getResponseBodyAsString();
		JSONObject result = new MagicJSONObject(response);
		if (code != 200 && code != 201) {
			return new ServerStatus(Status.ERROR, code, result.getString("description"), result, null);
		}
		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
	}
}
