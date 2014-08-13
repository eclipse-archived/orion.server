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
package org.eclipse.orion.server.authentication.oauth.github;

import javax.servlet.http.HttpServletRequest;

import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.eclipse.orion.server.authentication.oauth.OAuthConsumer;
import org.eclipse.orion.server.authentication.oauth.OAuthException;
import org.eclipse.orion.server.authentication.oauth.OAuthParams;
import org.eclipse.orion.server.authentication.oauth.OAuthTokenResponse;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * GitHub specific OAuthParams containing all information related to GitHub
 * oauth requests and responses.
 * @author Aidan Redpath
 *
 */
public class GitHubOAuthParams extends OAuthParams {
	
	private static final OAuthProviderType PROVIDER_TYPE = OAuthProviderType.GITHUB;
	
	private static final String GITHUB = "GitHub";
		
	private static final String REDIRECT_URI_LOGIN = "/login/oauth";
	
	private static final String REDIRECT_URI_LINK = "/mixlogin/manageopenids/oauth";
	
	private static final String RESPONSE_TYPE = "code";
	
	private static final String SCOPE = "user";
	
	private static final GrantType GRANT_TYPE = GrantType.AUTHORIZATION_CODE;
	
	private static final Class<? extends OAuthAccessTokenResponse> TOKEN_RESPONSE_CLASS = OAuthTokenResponse.class;
	
	private String client_key = null;
	private String client_secret = null;
	

	public GitHubOAuthParams(HttpServletRequest req, boolean login) throws OAuthException {
		super(req, login);
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
		return currentURL.toString() + (login ? REDIRECT_URI_LOGIN : REDIRECT_URI_LINK);
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
		return new GitHubOAuthConsumer(oauthAccessTokenResponse);
	}
	
	
	private void setCredentials() throws OAuthException{
		JSONObject json = readCredentialFile();
		try { 
			JSONObject googleCredentials = json.getJSONObject(GITHUB);
			client_key = googleCredentials.getString(CLIENT_KEY);
			client_secret = googleCredentials.getString(CLIENT_SECRET);
		} catch (JSONException e) {
			throw new OAuthException("Error getting oauth credentials");
		}
	}


}
