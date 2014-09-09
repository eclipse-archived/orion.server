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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.apache.oltu.oauth2.client.request.OAuthClientRequest.AuthenticationRequestBuilder;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.message.types.GrantType;

/**
 * A set of abstract methods used to store information for OAuth token providers
 * @author Aidan Redpath
 *
 */
public abstract class OAuthParams {

	protected static final String CLIENT_KEY = "client_key";

	protected static final String CLIENT_SECRET = "client_secret";

	protected static final String REDIRECT = "redirect";

	protected static final String REDIRECT_URI_LOGIN = "/login/oauth";

	protected static final String REDIRECT_URI_LINK = "/mixlogin/manageoauth/oauth";

	public abstract OAuthProviderType getProviderType();

	public abstract String getClientKey() throws OAuthException;

	public abstract String getClientSecret() throws OAuthException;

	public abstract String getResponseType();

	public abstract String getScope();

	public abstract GrantType getGrantType();

	public abstract Class<? extends OAuthAccessTokenResponse> getTokenResponseClass();

	public abstract OAuthConsumer getNewOAuthConsumer(OAuthAccessTokenResponse oauthAccessTokenResponse) throws OAuthException;

	protected final boolean login;
	private final URL currentURL;
	private final String state;
	private final String redirect;

	public OAuthParams(HttpServletRequest req, boolean login) throws OAuthException {
		redirect = req.getParameter(REDIRECT);
		this.login = login;
		state = UUID.randomUUID().toString();
		try {
			currentURL = new URL(OAuthHelper.getAuthServerRequest(req).toString());
		} catch (MalformedURLException e) {
			throw new OAuthException("An error occured while authenticating");
		}
	}

	public void addAdditionsParams(AuthenticationRequestBuilder requestBuiler) throws OAuthException {
		return;
	}

	protected URL getCurrentURL() throws OAuthException {
		try {
			return new URL(currentURL.getProtocol(), currentURL.getHost(), currentURL.getPort(), "");
		} catch (MalformedURLException e) {
			throw new OAuthException("An error occured while authenticating");
		}
	}

	public String getRedirectURI() throws OAuthException {
		return getCurrentURL().toString() + (login ? REDIRECT_URI_LOGIN : REDIRECT_URI_LINK);
	}

	protected String getRedirect() {
		return redirect;
	}

	public String getState(){
		return state;
	}
}
