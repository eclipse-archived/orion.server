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
package org.eclipse.orion.server.cf.commands;

import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginCommand implements ICFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private Cloud cloud;
	private String username;
	private String password;

	private JSONObject accessToken;

	public LoginCommand(Cloud cloud, String username, String password) {
		this.commandName = "Login"; //$NON-NLS-1$
		this.cloud = cloud;
		this.username = username;
		this.password = password;
	}

	public IStatus doIt() {
		try {
			URI infoURI = URIUtil.toURI(this.cloud.getUrl());

			infoURI = infoURI.resolve("/v2/info");

			GetMethod getMethod = new GetMethod(infoURI.toString());
			getMethod.addRequestHeader(new Header("Accept", "application/json"));
			getMethod.addRequestHeader(new Header("Content-Type", "application/json"));

			String response;
			try {
				CFActivator.getDefault().getHttpClient().executeMethod(getMethod);
				response = getMethod.getResponseBodyAsString(67108864);
			} finally {
				getMethod.releaseConnection();
			}
			JSONObject result = new JSONObject(response);

			// login

			String authorizationEndpoint = result.getString("authorization_endpoint");
			URI loginURI = new URI(authorizationEndpoint);
			loginURI = URIUtil.append(loginURI, "/oauth/token");

			PostMethod postMethod = new PostMethod(loginURI.toString());
			postMethod.addRequestHeader(new Header("Accept", "application/json"));
			postMethod.addRequestHeader(new Header("Content-Type", "application/x-www-form-urlencoded"));
			postMethod.addRequestHeader(new Header("Authorization", "Basic Y2Y6"));

			postMethod.addParameter("grant_type", "password");
			postMethod.addParameter("password", this.password);
			postMethod.addParameter("username", this.username);
			postMethod.addParameter("scope", "");

			try {
				int code = CFActivator.getDefault().getHttpClient().executeMethod(postMethod);
				response = postMethod.getResponseBodyAsString(67108864);
				if (code != HttpServletResponse.SC_OK) {
					try {
						result = new JSONObject(response);
						return new ServerStatus(Status.ERROR, code, "", result, null);
					} catch (Exception e) {
						result = null;
						return new ServerStatus(Status.ERROR, code, "Unexpected error", null);
					}
				}
			} finally {
				postMethod.releaseConnection();
			}

			this.cloud.setAccessToken(new JSONObject(response));
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new Status(IStatus.ERROR, CFActivator.PI_CF, msg, e);
		}

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	public JSONObject getOAuthAccessToken() {
		return accessToken;
	}
}
