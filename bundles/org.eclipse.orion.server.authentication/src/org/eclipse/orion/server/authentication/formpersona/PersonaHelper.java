/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;
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

	static {
		configuredAudience = null;
		try {
			String audiencePref = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_HOST, null);
			if (audiencePref != null) {
				configuredAudience = new URL(audiencePref);
			}
		} catch (MalformedURLException e) {
			LogHelper.log(e);
		}
		verifierUrl = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_PERSONA_VERIFIER, DEFAULT_VERIFIER);
	}

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
		if (configuredAudience == null) {
			throw new PersonaException("Authentication host not configured");
		}
		int configuredPort = configuredAudience.getPort() == -1 ? configuredAudience.getDefaultPort() : configuredAudience.getPort();
		String scheme = req.getScheme();
		String serverName = req.getServerName();
		int port = req.getServerPort();
		if (!configuredAudience.getProtocol().equals(scheme))
			throw new PersonaException("Scheme not allowed: " + scheme);
		if (!configuredAudience.getHost().equals(serverName))
			throw new PersonaException("Server name not allowed: " + serverName);
		if (configuredPort != port)
			throw new PersonaException("Port not allowed: " + port);
		try {
			return new URI(scheme, null, serverName, port, null, null, null).toString();
		} catch (URISyntaxException e) {
			throw new PersonaException(e);
		}
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
					// Verify that the server name resolves to a loopback device, to prevent spoofing
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
			if (configuredAudience != null) {
				// Check if this server is configured to allow the given host as an Persona audience
				audience = getConfiguredAudience(req);
				if (log.isInfoEnabled())
					log.info("Persona auth request for configured host. Sending audience " + audience);
				return audience;
			} else {
				audience = getLoopbackAudience(req);
				if (audience != null) {
					if (log.isInfoEnabled())
						log.info("Persona auth request from loopback. Sending audience " + audience);
					return audience;
				} else {
					throw new PersonaException("Authentication host not configured");
				}
			}
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
			if (success == null || email == null || email.equals("")) { //$NON-NLS-1$
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
			HttpURLConnection connection = (HttpURLConnection) new URL(verifierUrl).openConnection();
			connection.setRequestMethod("POST"); //$NON-NLS-1$
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setRequestProperty(ProtocolConstants.HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded"); //$NON-NLS-1$
			connection.setUseCaches(false);
			connection.connect();
			String postData = new StringBuilder()
					.append("assertion=").append(URLEncoder.encode(assertion, "UTF-8")) //$NON-NLS-1$ //$NON-NLS-2$
					.append("&audience=").append(URLEncoder.encode(audience, "UTF-8")) //$NON-NLS-1$ //$NON-NLS-2$
					.toString();
			connection.getOutputStream().write(postData.getBytes("UTF-8")); //$NON-NLS-1$
			PersonaVerificationResponse personaResponse = new PersonaVerificationResponse(IOUtilities.toString(connection.getInputStream()));
			PersonaVerificationSuccess success;
			PersonaVerificationFailure failure;
			if ((success = personaResponse.getSuccess()) != null) { //$NON-NLS-1$
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
}
