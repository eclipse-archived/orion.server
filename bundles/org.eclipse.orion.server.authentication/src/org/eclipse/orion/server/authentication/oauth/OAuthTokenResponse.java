/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.oauth;

import java.util.Iterator;

import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.token.BasicOAuthToken;
import org.apache.oltu.oauth2.common.token.OAuthToken;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A OAuthAccessTokenResponse that handles a json response
 * @author Aidan Redpath
 *
 */
public class OAuthTokenResponse  extends OAuthAccessTokenResponse {


	public String getAccessToken() {
		return getParam(OAuth.OAUTH_ACCESS_TOKEN);
	}

	public Long getExpiresIn() {
		String value = getParam(OAuth.OAUTH_EXPIRES_IN);
		return value == null? null: Long.valueOf(value);
	}

	public String getRefreshToken() {
		return getParam(OAuth.OAUTH_REFRESH_TOKEN);
	}

	public String getScope() {
		return getParam(OAuth.OAUTH_SCOPE);
	}

	public String getTokenType() {
		return getParam(OAuth.OAUTH_TOKEN_TYPE);
	}

	public OAuthToken getOAuthToken() {
		return new BasicOAuthToken(getAccessToken(), getExpiresIn(), getRefreshToken(), getScope());
	}

	protected void setBody(String body) {
		this.body = body;
		if (body != null) {
			JSONObject json = null;
			try {
				json = new JSONObject(body);
			} catch (JSONException e) {
				// Body might be a form
				parameters = OAuthUtils.decodeForm(body);
				return;
			}
			Iterator<?> keys = json.keys();
			while ( keys.hasNext()) {
				String key = (String) keys.next();
				try {
					String value = json.getString(key);
					if (!OAuthUtils.isEmpty(value)) {
						parameters.put(key, value);
					}
				} catch (JSONException e) {
					// No string value for the key
					continue;
				}
			}
		}
	}

	protected void setContentType(String contentType) {
		this.contentType = contentType;
	}

	protected void setResponseCode(int code) {
		this.responseCode = code;
	}

	@Override
	protected void init(String body, String contentType, int responseCode) throws OAuthProblemException {
		super.init(body, contentType, responseCode);
	}

}
