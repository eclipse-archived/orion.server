package org.eclipse.orion.server.authentication.oauth;

import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.token.OAuthToken;

public abstract class OAuthConsumer implements OAuthToken {
	
	protected OAuthToken accessToken;
	
	public OAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse){
		accessToken = oauthAccessTokenResponse.getOAuthToken();
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
	public abstract String getIdentifier();
}
