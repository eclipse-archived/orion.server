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
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;

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
	private static boolean allowAnonymousAccountCreation;
	private static String serverName;
	private static String verifierUrl;

	static {
		//if there is no list of users authorised to create accounts, it means everyone can create accounts
		allowAnonymousAccountCreation = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null) == null; //$NON-NLS-1$
		serverName = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_PERSONA_SERVER_NAME, null);
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

	private static boolean canAddUsers() {
		return allowAnonymousAccountCreation ? userAdmin.canCreateUsers() : false;
	}

	public void handleCredentialsAndLogin(HttpServletRequest req, HttpServletResponse res) throws PersonaException {
		String assertion = req.getParameter(PersonaConstants.PARAM_ASSERTION);
		if (assertion != null) {
			String hostname = serverName == null ? req.getServerName() : serverName;
			String audience = req.getScheme() + "://" + hostname + ":" + req.getServerPort(); //$NON-NLS-1$ //$NON-NLS-2$
			
			// Verify response against verifierUrl
			PersonaVerificationSuccess success = verifyCredentials(assertion, audience, req);
			String email = success.getEmail();
			if (success == null || email == null || email.equals("")) { //$NON-NLS-1$
				throw new PersonaException("Verification response is not sufficient");
			}

			User user = userAdmin.getUser(UserConstants.KEY_EMAIL, email);
			if (user == null) {
				if (canAddUsers()) {
					User newUser = new User();
					newUser.setName(email);
					newUser.setEmail(email);
					user = userAdmin.createUser(newUser);
				} else {
					throw new PersonaException("Your Persona email, "+email+", is not associated with any Orion account. Log in to Orion and set your email address from the Settings page.");
				}
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
