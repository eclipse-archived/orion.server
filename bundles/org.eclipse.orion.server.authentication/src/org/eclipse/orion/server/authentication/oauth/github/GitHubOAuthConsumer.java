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

import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.eclipse.orion.server.authentication.oauth.OAuthConsumer;
import org.eclipse.orion.server.authentication.oauth.OAuthException;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * GitHub specific OAuthConsumer used to handle GitHubs oauth token responses.
 * @author Aidan Redpath
 *
 */
public class GitHubOAuthConsumer extends OAuthConsumer{

	// Parameters
	private static final String EMAIL_PARAMETER = "email";
	private static final String USERNAME_PARAMETER = "login";
	private static final String ID_PARAMETER = "id";
	private static final String URL_PARAMETER = "url";
	private static final String PRIMARY_PARAMETER = "primary";
	private static final String VERIFIED_PARAMETER = "verified";
	
	private static final String PROFILE_URL = "https://api.github.com/user";
	private static final String EMAIL_URL = "https://api.github.com/user/emails";
	private static final String GITHUB_HOST = "github.com";
	
	private String email;
	private String username;
	private String id;
	private String url;
	private boolean email_verified;
	
	public GitHubOAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse, String redirect) throws OAuthException {
		super(oauthAccessTokenResponse, redirect);
		getGitHubProfile();
		getGitHubEmail();
	}

	private void getGitHubProfile() throws OAuthException{
		String body = getServerResponse(PROFILE_URL);
		JSONObject json;
		try {
			json = new JSONObject(body);
			username = json.getString(USERNAME_PARAMETER);
			id = json.getString(ID_PARAMETER);
			url = json.getString(URL_PARAMETER);
		} catch (JSONException e) {
			throw new OAuthException("An error occured while authenticating the user");
		}
	}
	
	private void getGitHubEmail() throws OAuthException{
		String body = getServerResponse(EMAIL_URL);
		JSONArray json;
		try {
			json = new JSONArray(body);
			for(int i = 0; i < json.length(); i++){
				JSONObject emailObject = json.getJSONObject(i);
				boolean primary = emailObject.getBoolean(PRIMARY_PARAMETER);
				if(primary){
					email_verified = emailObject.getBoolean(VERIFIED_PARAMETER);
					email = emailObject.getString(EMAIL_PARAMETER);
					break;
				}
			}
		} catch (JSONException e) {
			throw new OAuthException("An error occured while authenticating the user");
		}
	}
	
	@Override
	public String getIdentifier() {
		return url + "/" + id;
	}

	@Override
	public String getEmail() {
		return email;
	}

	@Override
	public String getUsername() {
		// github allows a dash in the username, so remove any dashes
		return username.replaceAll("-", "");
	}

	@Override
	public boolean isEmailVerifiecd() {
		return email_verified;
	}
	
	public void save(UserInfo userInfo) {
		try {
			String property = userInfo.getProperty(UserConstants.GITHUB_ACCESS_TOKEN);
			JSONObject tokens = null;
			try {
				tokens = new JSONObject(SimpleUserPasswordUtil.decryptPassword(property));
			} catch (Exception e) {
				tokens = new JSONObject();
				if (property != null && property.length() > 0) {
					/*
					 * Backwards-compatibility: Convert this value from its old format (a plain string
					 * representing the user's token for github.com specifically) to the new format.
					 */
					tokens.put(GITHUB_HOST, property);
				}
			}
			tokens.put(GITHUB_HOST, getAccessToken());
			userInfo.setProperty(UserConstants.GITHUB_ACCESS_TOKEN, SimpleUserPasswordUtil.encryptPassword(tokens.toString()));
		} catch (JSONException e) {}
	}

}
