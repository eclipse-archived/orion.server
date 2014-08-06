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
import org.eclipse.orion.server.core.resources.Base64;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Google specific OAuthConsumer used to handle google oauth token responses.
 * @author Aidan Redpath
 *
 */
public class GoogleOAuthConsumer extends OAuthConsumer {

	private static final String ID_TOKEN = "id_token"; 
	private static final String ID_PARAMETER = "sub"; 
	private static final String ISS_PARAMETER = "iss";
	private static final String EMAIL_PARAMETER = "email";
	private static final String EMAIL_VERIFIED_PARAMETER = "email_verified";
	
	private final String userId;
	private final String issuer;
	private String email;
	private boolean email_verified;
	
	public GoogleOAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse) throws OAuthException {
		super(oauthAccessTokenResponse);
		String idToken = oauthAccessTokenResponse.getParam(ID_TOKEN);
		// No validation required since the token comes directly from the google server
		// Token Sections: header.claim.signature
		String [] idTokenSections = idToken.split("\\.");
		if(idTokenSections.length != 3)
			throw new OAuthException("Invalid authentication response from the oauth server");
		String claim = idTokenSections[1];
		String decodedClaim = new String(Base64.decode(claim.getBytes()));
		JSONObject jsonClaim;
		try {
			jsonClaim = new JSONObject(decodedClaim);
			userId = jsonClaim.getString(ID_PARAMETER);
			issuer = jsonClaim.getString(ISS_PARAMETER);
		} catch (JSONException e) {
			throw new OAuthException(e);
		}
		try {
			email = jsonClaim.getString(EMAIL_PARAMETER);
		} catch (JSONException e) {
			email = null;
		}
		try {
			email_verified = jsonClaim.getBoolean(EMAIL_VERIFIED_PARAMETER);
		} catch (JSONException e) {
			email_verified = false;
		}
	}


	@Override
	public String getIdentifier() {
		return issuer + "/" + userId;
	}


	@Override
	public String getEmail() {
		return email;
	}


	@Override
	public String getUsername() {
		return getEmail() == null ? null : getEmail().split("@")[0];
	}

	public boolean isEmailVerifiecd(){
		return email_verified;
	}
}
