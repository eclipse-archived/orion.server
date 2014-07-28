package org.eclipse.orion.server.authentication.oauth;

import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.eclipse.orion.server.core.resources.Base64;
import org.json.JSONException;
import org.json.JSONObject;

public class GoogleOAuthConsumer extends OAuthConsumer {

	private static final String ID_TOKEN = "id_token"; 
	private static final String ID_PARAMETER = "sub"; 
	private static final String ISS_PARAMETER = "iss";

	private final String consumerId; 

	public GoogleOAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse) throws OAuthException {
		super(oauthAccessTokenResponse);
		String idToken = oauthAccessTokenResponse.getParam(ID_TOKEN);
		// No validation required since the token comes direcly from the google server
		// Token Sections: header.claim.signature
		String [] idTokenSections = idToken.split("\\.");
		if(idTokenSections.length != 3)
			throw new OAuthException("Invalid authentication response from the oauth server");
		String claim = idTokenSections[1];
		String decodedClaim = new String(Base64.decode(claim.getBytes()));
		try {
			JSONObject jsonClaim = new JSONObject(decodedClaim);
			String userId = jsonClaim.getString(ID_PARAMETER);
			String iss = jsonClaim.getString(ISS_PARAMETER);
			consumerId = iss + "/" + userId;
		} catch (JSONException e) {
			throw new OAuthException(e);
		}
	}


	@Override
	public String getIdentifier() {
		return consumerId;
	}

}
