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

import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Google specific OAuthParams containing all information related to google
 * oauth requests and responses.
 * @author Aidan Redpath
 *
 */
public class GoogleOAuthParams extends OAuthParams {
	
	private static final OAuthProviderType PROVIDER_TYPE = OAuthProviderType.GOOGLE;
	
	private static final String GOOGLE = "Google";
		
	private static final String REDIRECT_URI_LOGIN = "http://localhost:8080/login/oauth";
	
	private static final String REDIRECT_URI_LINK = "http://localhost:8080/mixlogin/manageopenids/oauth";
	
	private static final String RESPONSE_TYPE = "code";
	
	private static final String SCOPE = "profile email";
	
	private static final GrantType GRANT_TYPE = GrantType.AUTHORIZATION_CODE;
	
	private static final Class<? extends OAuthAccessTokenResponse> TOKEN_RESPONSE_CLASS = GoogleTokenResponse.class;
	
	private String client_key = null;
	private String client_secret = null;
	
	private final boolean login;
	
	public GoogleOAuthParams(boolean login) {
		this.login = login;
	}
	
	public OAuthProviderType getProviderType() {
		return PROVIDER_TYPE;
	}

	public String getClientKey() throws OAuthException {
		if(client_key == null) {
			setCredentials();
		}
		return client_key;
	}

	public String getClientSecret() throws OAuthException {
		if(client_secret == null) {
			setCredentials();
		}
		return client_secret;
	}

	public String getRedirectURI() {
		return login ? REDIRECT_URI_LOGIN : REDIRECT_URI_LINK;
	}

	public String getResponseType() {
		return RESPONSE_TYPE;
	}

	public String getScope() {
		return SCOPE;
	}

	public GrantType getGrantType() {
		return GRANT_TYPE;
	}

	public Class<? extends OAuthAccessTokenResponse> getTokenResponseClass() {
		return TOKEN_RESPONSE_CLASS;
	}
	
	public OAuthConsumer getNewOAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse) throws OAuthException {
		return new GoogleOAuthConsumer(oauthAccessTokenResponse);
	}
	
	
	private void setCredentials() throws OAuthException{
		JSONObject json = readCredentialFile();
		try { 
			JSONObject googleCredentials = json.getJSONObject(GOOGLE);
			client_key = googleCredentials.getString(CLIENT_KEY);
			client_secret = googleCredentials.getString(CLIENT_SECRET);
		} catch (JSONException e) {
			throw new OAuthException("Error getting oauth credentials");
		}
	}


}
