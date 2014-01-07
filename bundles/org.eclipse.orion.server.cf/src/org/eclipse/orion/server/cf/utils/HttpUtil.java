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

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.eclipse.orion.server.cf.objects.Target;
import org.json.JSONException;

public class HttpUtil {
	public static void configureHttpMethod(HttpMethod method, Target target) throws JSONException {
		method.addRequestHeader(new Header("Accept", "application/json"));
		method.addRequestHeader(new Header("Content-Type", "application/json"));
		if (target.getAccessToken() != null)
			method.addRequestHeader(new Header("Authorization", "bearer " + target.getAccessToken().getString("access_token")));
		//		else {
		//			try {
		//				JSONObject token = new Test().getToken("Szymon.Brandys@pl.ibm.com");
		//				method.addRequestHeader(new Header("Authorization", "bearer " + token.getString("access_token")));
		//			} catch (Exception e) {
		//				e.printStackTrace();
		//			}
		//		}
	}
}
