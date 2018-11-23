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
package org.eclipse.orion.server.authentication.oauth.google;


import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.eclipse.orion.server.authentication.oauth.OAuthConsumer;
import org.eclipse.orion.server.authentication.oauth.OAuthException;
import org.eclipse.orion.server.core.resources.Base64;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Google specific OAuthConsumer used to handle google oauth token responses.
 * @author Aidan Redpath
 *
 */
public class GoogleOAuthConsumer extends OAuthConsumer {
	// Parameters
	private static final String TOKEN_PARAMETER = "id_token";
	private static final String ID_PARAMETER = "sub"; 
	private static final String PROVIDER_PARAMETER = "iss";
	private static final String EMAIL_PARAMETER = "email";
	private static final String EMAIL_VERIFIED_PARAMETER = "email_verified";
	private static final String OPEN_ID_PARAMETER = "openid_id";

	private String userId;
	private String provider;
	private String openid_id;
	private String email;
	private boolean email_verified;
	
	public GoogleOAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse, String redirect) throws OAuthException {
		super(oauthAccessTokenResponse, redirect);
		try {
			JSONObject json = new JSONObject(oauthAccessTokenResponse.getBody());
			String jwt = json.getString(TOKEN_PARAMETER);
			parseToken(jwt);
		} catch (JSONException e) {
			throw new OAuthException(e);
		}
	}

	private void parseToken(String jwt) throws OAuthException {
		String [] sections = jwt.split("\\.");
		if(sections.length != 3)
			throw new OAuthException("An error occured while authenticating");
		// No validation required since the token comes directly from the google server
		// JWT Structure
		// Header.Claim.Signature
		String claim = sections[1];
		int buffer = 4 - (claim.length() % 4);
		// Encoded base64 should never need 3 buffer characters
		if(buffer == 3)
			throw new OAuthException("An error occured while authenticating");
		// Don't add 4 buffer characters
		for(int i = 0; i < buffer && buffer != 4; i++) {
			claim += "=";
		}
		String decodedClaim = new String(Base64.decode(claim.getBytes()));
		JSONObject jsonClaim;
		try {
			jsonClaim = new JSONObject(decodedClaim);
			userId = jsonClaim.getString(ID_PARAMETER);
			provider = jsonClaim.getString(PROVIDER_PARAMETER);
			openid_id = jsonClaim.getString(OPEN_ID_PARAMETER);
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
		return provider + "/" + userId;
	}

	public String getOpenidIdentifier(){
		return openid_id;
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

	public boolean isEmailVerifiecd() {
		return email_verified;
	}
}
