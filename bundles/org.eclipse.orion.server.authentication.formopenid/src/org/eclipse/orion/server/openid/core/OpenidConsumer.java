/*******************************************************************************
 * Copyright 2006, 2011 Sxip Identity Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Sxip Identity Corporation - Original SampleConsumer.java class
 *     IBM Corporation - sample modified to integrate with Eclipse
 *******************************************************************************/
package org.eclipse.orion.server.openid.core;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.authentication.formopenid.Activator;
import org.openid4java.OpenIDException;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.InMemoryConsumerAssociationStore;
import org.openid4java.consumer.InMemoryNonceVerifier;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryException;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.ParameterList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simplified version of org.openid4java.consumer.SampleConsumer. It doesn't
 * fetch any attributes from an OpenID Provider.
 */
public class OpenidConsumer {

	final Logger log = LoggerFactory.getLogger("org.eclipse.orion.server.login"); //$NON-NLS-1$

	private ConsumerManager manager;
	private String returnToUrl;

	public OpenidConsumer(String returnToUrl) throws ConsumerException {
		// configure the return_to URL where your application will receive
		// the authentication responses from the OpenID provider
		this.returnToUrl = returnToUrl;

		// instantiate a ConsumerManager object
		manager = new ConsumerManager();
		manager.setAssociations(new InMemoryConsumerAssociationStore());
		manager.setNonceVerifier(new InMemoryNonceVerifier(5000));

		// for a working demo, not enforcing RP realm discovery
		// since this new feature is not deployed
		manager.getRealmVerifier().setEnforceRpId(false);
	}

	// --- placing the authentication request ---
	public String authRequest(String userSuppliedString, HttpServletRequest httpReq, HttpServletResponse httpResp) throws CoreException {
		AuthRequest authReq = null;

		// --- Forward proxy setup (only if needed) ---
		// ProxyProperties proxyProps = new ProxyProperties();
		// proxyProps.setProxyName("proxy.example.com");
		// proxyProps.setProxyPort(8080);
		// HttpClientFactory.setProxyProperties(proxyProps);

		// perform discovery on the user-supplied identifier
		List<DiscoveryInformation> discoveries;
		try {
			discoveries = manager.discover(userSuppliedString);
		} catch (DiscoveryException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "Could not discover: " + userSuppliedString, e));
		}

		// attempt to associate with the OpenID provider
		// and retrieve one service endpoint for authentication
		DiscoveryInformation discovered = manager.associate(discoveries);

		// store the discovery information in the user's session
		httpReq.getSession().setAttribute(OpenIdHelper.OPENID_DISC, discovered);

		// obtain a AuthRequest message to be sent to the OpenID provider
		try {
			URL returnURL = new URL(returnToUrl);
			authReq = manager.authenticate(discovered, returnToUrl, new URL(returnURL.getProtocol(), returnURL.getHost(), returnURL.getPort(), "").toString());
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "An error occured when authenticating request for " + userSuppliedString, e));
		}

		// redirect location in the OpenID popup window
		try {
			httpResp.sendRedirect(authReq.getDestinationUrl(true));
			httpResp.flushBuffer();
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, 1, "An error occured when trying to redirect to " + authReq.getDestinationUrl(true), e));
		}

		return null;
	}

	// --- processing the authentication response ---
	public Identifier verifyResponse(HttpServletRequest httpReq) {
		// extract the parameters from the authentication response
		// (which comes in as a HTTP request from the OpenID provider)
		ParameterList response = new ParameterList(httpReq.getParameterMap());

		// retrieve the previously stored discovery information
		DiscoveryInformation discovered = (DiscoveryInformation) httpReq.getSession().getAttribute(OpenIdHelper.OPENID_DISC);
		try {
			// extract the receiving URL from the HTTP request
			StringBuffer receivingURL = httpReq.getRequestURL();
			String queryString = httpReq.getQueryString();
			if (queryString != null && queryString.length() > 0)
				receivingURL.append("?").append(httpReq.getQueryString()); //$NON-NLS-1$

			// verify the response; ConsumerManager needs to be the same
			// (static) instance used to place the authentication request
			VerificationResult verification = manager.verify(receivingURL.toString(), response, discovered);

			// examine the verification result and extract the verified identifier
			Identifier verified = verification.getVerifiedId();
			if (verified != null) {
				AuthSuccess authSuccess = (AuthSuccess) verification.getAuthResponse();

				HttpSession session = httpReq.getSession(true);
				session.setAttribute(OpenIdHelper.OPENID_IDENTIFIER, authSuccess.getIdentity());
				if (log.isInfoEnabled())
					log.info("Login success: " + verified.getIdentifier()); //$NON-NLS-1$ 
				return verified; // success
			}
		} catch (OpenIDException e) {
			log.error("An error occured when verifyng response.", e); //$NON-NLS-1$
		}
		if (log.isInfoEnabled()) {
			Identifier claimedId = discovered.getClaimedIdentifier();
			if (claimedId != null)
				log.info("Login failed: " + claimedId.getIdentifier());//$NON-NLS-1$ 
		}
		return null;
	}
}
