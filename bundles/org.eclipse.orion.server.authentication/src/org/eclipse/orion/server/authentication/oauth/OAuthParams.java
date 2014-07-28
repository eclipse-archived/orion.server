package org.eclipse.orion.server.authentication.oauth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.eclipse.orion.server.authentication.Activator;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class OAuthParams {
	
	private static final String CREDENTIAL_FILE = "oauths/credentials.json"; 
	
	protected static final  String CLIENT_KEY = "client_key";
	
	protected static final String CLIENT_SECRET = "client_secret";
	
	public abstract OAuthProviderType getProviderType();
	
	public abstract String getClientKey() throws OAuthException;
	
	public abstract String getClientSecret() throws OAuthException;
	
	public abstract String getRedirectURI();
	
	public abstract String getResponseType();
	
	public abstract String getScope();
	
	public abstract GrantType getGrantType();
	
	public abstract Class<? extends OAuthAccessTokenResponse> getTokenResponseClass();
	
	protected JSONObject readCredentialFile() throws OAuthException{
		try {
			JSONObject json = new JSONObject(getFileContents());
			return json;
		} catch (JSONException e) {
			throw new OAuthException("Error getting oauth credentials");
		} catch (IOException e) {
			throw new OAuthException("Error getting oauth credentials");
		}
	}
	
	private String getFileContents() throws IOException {
		StringBuilder sb = new StringBuilder();
		InputStream is = Activator.getBundleContext().getBundle().getEntry(CREDENTIAL_FILE).openStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		try {
			String line = ""; //$NON-NLS-1$
			while ((line = br.readLine()) != null) {
				sb.append(line).append('\n');
			}
		} finally {
			br.close();
		}
		return sb.toString();
	}
}
