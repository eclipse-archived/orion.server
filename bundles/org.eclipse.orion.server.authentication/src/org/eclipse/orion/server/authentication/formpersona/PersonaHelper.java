/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.formpersona;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import javax.servlet.http.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.user.profile.*;
import org.eclipse.orion.server.useradmin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class PersonaHelper {

	public static final String DEFAULT_VERIFIER = "https://verifier.login.persona.org/verify"; //$NON-NLS-1$

	private final Logger log = LoggerFactory.getLogger("org.eclipse.orion.server.login"); //$NON-NLS-1$
	private static IOrionCredentialsService userAdmin;
	private static IOrionUserProfileService userProfileService;
	private static URL configuredAudience;
	private static String verifierUrl;

	public static String getAuthType() {
		return "Persona"; //$NON-NLS-1$
	}

	public static IOrionCredentialsService getDefaultUserAdmin() {
		return userAdmin;
	}

	public void setUserAdmin(IOrionCredentialsService _userAdmin) {
		userAdmin = _userAdmin;
	}

	public void unsetUserAdmin(IOrionCredentialsService _userAdmin) {
		if (_userAdmin.equals(userAdmin)) {
			userAdmin = null;
		}
	}

	public static IOrionUserProfileService getUserProfileService() {
		return userProfileService;
	}

	public static void bindUserProfileService(IOrionUserProfileService _userProfileService) {
		userProfileService = _userProfileService;
	}

	public static void unbindUserProfileService(IOrionUserProfileService userProfileService) {
		userProfileService = null;
	}

	/**
	 * @param req
	 * @return The audience to use, if the request's server name matches the server's configured audience info.
	 * Throws if it doesn't match.
	 */
	private String getConfiguredAudience(HttpServletRequest req) throws PersonaException {
		URL audienceURL = getConfiguredAudienceURL();
		if (audienceURL == null) {
			throw new PersonaException("Authentication host not configured");
		}
		return audienceURL.toString();
	}

	private static boolean isLoopback(InetAddress addr) {
		try {
			if (addr.isLoopbackAddress())
				return true;
			return NetworkInterface.getByInetAddress(addr) != null;
		} catch (SocketException e) {
			return false;
		}
	}

	/**
	 * If the request appears to be from a loopback interface, returns an audience constructed from the server name.
	 * Otherwise returns null.
	 */
	private String getLoopbackAudience(HttpServletRequest req) throws PersonaException {
		try {
			String serverName = req.getServerName();
			try {
				// First ensure the request is coming from the IP of a loopback device
				if (isLoopback(InetAddress.getByName(req.getLocalAddr()))) {
					// Verify that the server name resolves to a loopback device, to prevent spoofing/proxying
					InetAddress addr = InetAddress.getByName(serverName);
					if (isLoopback(addr))
						return new URI(req.getScheme(), req.getRemoteUser(), serverName, req.getServerPort(), null, null, null).toString();
				}
			} catch (UnknownHostException e) {
				// Bogus serverName, ignore
			}
		} catch (URISyntaxException e) {
			throw new PersonaException(e);
		}
		return null;
	}

	private String getVerifiedAudience(HttpServletRequest req) {
		try {
			String audience;
			if (getConfiguredAudienceURL() != null) {
				// Check if this server is configured to allow the given host as an Persona audience
				audience = getConfiguredAudience(req);
				if (log.isInfoEnabled())
					log.info("Persona auth request for configured host. Sending audience " + audience);
				return audience;
			}
			audience = getLoopbackAudience(req);
			if (audience == null)
				throw new PersonaException("Authentication host not configured");
			if (log.isInfoEnabled())
				log.info("Persona auth request from loopback. Sending audience " + audience);
			return audience;
		} catch (PersonaException e) {
			throw new PersonaException("Error logging in: " + e.getMessage() + ". Contact your system administrator for assistance.");
		}
	}

	public void handleCredentialsAndLogin(HttpServletRequest req, HttpServletResponse res) throws PersonaException {
		String assertion = req.getParameter(PersonaConstants.PARAM_ASSERTION);
		if (assertion != null) {
			String audience = getVerifiedAudience(req);

			// Verify response against verifierUrl
			PersonaVerificationSuccess success = verifyCredentials(assertion, audience, req);
			String email = success.getEmail();
			if (email == null || email.equals("")) { //$NON-NLS-1$
				throw new PersonaException("Verification response is not sufficient");
			}

			User user = userAdmin.getUser(UserConstants.KEY_EMAIL, email);
			if (user == null) {
				throw new PersonaException("There is no Orion account associated with your Persona email. Please register or contact your system administrator for assistance.");
			}
			req.getSession().setAttribute("user", user.getUid()); //$NON-NLS-1$

			IOrionUserProfileNode userProfileNode = getUserProfileService().getUserProfileNode(user.getUid(), IOrionUserProfileConstants.GENERAL_PROFILE_PART);
			try {
				// try to store the login timestamp in the user profile
				userProfileNode.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, new Long(System.currentTimeMillis()).toString(), false);
				userProfileNode.flush();
			} catch (CoreException e) {
				// just log that the login timestamp was not stored
				LogHelper.log(e);
			}
			return;
		}
	}

	/**
	 * @param assertion
	 * @param audience
	 * @param req
	 * @return
	 */
	public PersonaVerificationSuccess verifyCredentials(String assertion, String audience, HttpServletRequest req) throws PersonaException {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(getVerifierUrl()).openConnection();
			connection.setRequestMethod("POST"); //$NON-NLS-1$
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setRequestProperty(ProtocolConstants.HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded"); //$NON-NLS-1$
			connection.setUseCaches(false);
			connection.connect();
			String postData = new StringBuilder().append("assertion=").append(URLEncoder.encode(assertion, "UTF-8")) //$NON-NLS-1$ //$NON-NLS-2$
					.append("&audience=").append(URLEncoder.encode(audience, "UTF-8")) //$NON-NLS-1$ //$NON-NLS-2$
					.toString();
			connection.getOutputStream().write(postData.getBytes("UTF-8")); //$NON-NLS-1$
			PersonaVerificationResponse personaResponse = new PersonaVerificationResponse(IOUtilities.toString(connection.getInputStream()));
			PersonaVerificationSuccess success;
			PersonaVerificationFailure failure;
			if ((success = personaResponse.getSuccess()) != null) {
				HttpSession session = req.getSession(true);
				String email = success.getEmail();
				session.setAttribute(PersonaConstants.PERSONA_IDENTIFIER, email);
				if (log.isInfoEnabled()) {
					log.info("Persona verification succeeded: " + email); //$NON-NLS-1$
				}
				return success;
			} else if ((failure = personaResponse.getFailure()) != null) {
				String failMessage = "Persona verification failed: " + failure.getReason();
				if (log.isInfoEnabled()) {
					log.info(failMessage);
				}
				throw new PersonaException(failMessage);
			} else {
				throw new PersonaException("Unknown state");
			}
		} catch (MalformedURLException e) {
			log.error("An error occured when verifying credentials.", e);
			throw new PersonaException(e);
		} catch (ProtocolException e) {
			log.error("An error occured when verifying credentials.", e);
			throw new PersonaException(e);
		} catch (UnsupportedEncodingException e) {
			log.error("An error occured when verifying credentials.", e);
			throw new PersonaException(e);
		} catch (IOException e) {
			log.error("An error occured when verifying credentials.", e);
			throw new PersonaException(e);
		}
	}

	private static String getVerifierUrl() {
		if (verifierUrl == null)
			verifierUrl = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_PERSONA_VERIFIER, DEFAULT_VERIFIER);
		return verifierUrl;
	}

	private static URL getConfiguredAudienceURL() {
		if (configuredAudience == null) {
			try {
				String audiencePref = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_HOST, null);
				if (audiencePref != null) {
					configuredAudience = new URL(audiencePref);
				}
			} catch (MalformedURLException e) {
				LogHelper.log(e);
			}
		}
		return configuredAudience;
	}
}
