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
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Google specific OAuthConsumer used to handle google oauth token responses.
 * @author Aidan Redpath
 *
 */
public class GoogleOAuthConsumer extends OAuthConsumer {
	// Parameters
	private static final String ID_PARAMETER = "sub"; 
	private static final String PROFILE_PARAMETER = "profile";
	private static final String EMAIL_PARAMETER = "email";
	private static final String EMAIL_VERIFIED_PARAMETER = "email_verified";
	
	private static final String OPENID_URL = "https://www.googleapis.com/plus/v1/people/me/openIdConnect";
	
	private String userId;
	private String profile;
	private String email;
	private boolean email_verified;
	
	public GoogleOAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse) throws OAuthException {
		super(oauthAccessTokenResponse);
		// No validation required since the token comes directly from the google server
		getGoogleProfile();
		
	}

	/**
	 * Gets the user's profile information from google
	 * @throws OAuthException Thrown if an error occurs while retrieving the profile
	 */
	private void getGoogleProfile() throws OAuthException {
		OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
		OAuthClientRequest request;
		try {
			request = new OAuthBearerClientRequest(OPENID_URL)
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
		String body = response.getBody();
		JSONObject jsonClaim;
		try {
			jsonClaim = new JSONObject(body);
			userId = jsonClaim.getString(ID_PARAMETER);
			profile = jsonClaim.getString(PROFILE_PARAMETER);
		} catch (JSONException e) {
			throw new OAuthException(e);
		}
		email = "";
		email_verified = false;
		try{
			email = jsonClaim.getString(EMAIL_PARAMETER);
			email_verified = jsonClaim.getBoolean(EMAIL_VERIFIED_PARAMETER);
		} catch (JSONException e) {
			// Suppress
		}
	}
	
	

	/**
	 * Gets a unique identifier for the user.
	 */	
	@Override
	public String getIdentifier() {
		return profile;
	}

	/**
	 * Gets the user's email.
	 */
	@Override
	public String getEmail() {
		return email;
	}

	/**
	 * Gets a username to user for Orion.
	 */
	@Override
	public String getUsername() {
		return getEmail() == null ? null : getEmail().split("@")[0];
	}

	public boolean isEmailVerifiecd(){
		return email_verified;
	}
}
