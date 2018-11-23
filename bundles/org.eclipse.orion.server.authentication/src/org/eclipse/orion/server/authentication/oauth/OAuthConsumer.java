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

import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthBearerClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthResourceResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.token.OAuthToken;

/**
 * An abstract class used to hold information about the oauth
 * token providered by the oauth server.
 * @author Aidan Redpath
 *
 */
public abstract class OAuthConsumer implements OAuthToken {

	protected OAuthToken accessToken;
	private final String redirect;

	public OAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse, String redirect) {
		this.redirect = redirect;
		accessToken = oauthAccessTokenResponse.getOAuthToken();
	}

	public String getRedirect() {
		return redirect;
	}

	public String getAccessToken() {
		return accessToken.getAccessToken();
	}

	public Long getExpiresIn() {
		return accessToken.getExpiresIn();
	}

	public String getRefreshToken() {
		return accessToken.getRefreshToken();
	}

	public String getScope() {
		return accessToken.getScope();
	}

	/**
	 * Makes an authenticated HTTP Get call the the provided url.
	 * @param url The url to call.
	 * @return The body of the response.
	 * @throws OAuthException If an error occurs while making the call.
	 */
	protected String getServerResponse(String url) throws OAuthException{
		OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
		OAuthClientRequest request;
		try {
			request = new OAuthBearerClientRequest(url)
			.setAccessToken(getAccessToken())
			.buildQueryMessage();
		} catch (OAuthSystemException e1) {
			throw new OAuthException("An error occured while authenticating the user");
		}
		OAuthResourceResponse response;
		try {
			response = oAuthClient.resource(request, OAuth.HttpMethod.GET, OAuthResourceResponse.class);
		} catch (OAuthProblemException e) {
			throw new OAuthException("An error occured while authenticating the user");
		} catch (OAuthSystemException e) {
			throw new OAuthException("An error occured while authenticating the user");
		}
		return response.getBody();
	}

	public abstract String getIdentifier();

	public abstract String getEmail();

	public abstract String getUsername();

	public abstract boolean isEmailVerifiecd();

	public String getOpenidIdentifier(){
		return null;
	}
}
