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
import org.apache.oltu.oauth2.common.token.OAuthToken;

/**
 * An abstract class used to hold information about the oauth
 * token providered by the oauth server.
 * @author Aidan Redpath
 *
 */
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
	
	public abstract String getEmail();
	
	public abstract String getUsername();
}
