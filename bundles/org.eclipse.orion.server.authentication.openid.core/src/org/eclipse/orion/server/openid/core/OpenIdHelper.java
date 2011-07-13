/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.openid.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserAdminActivator;
import org.json.JSONException;
import org.json.JSONObject;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.discovery.Identifier;

/**
 * Groups methods to handle session attributes for OpenID authentication.
 * 
 */
public class OpenIdHelper {

	public static final String OPENID = "openid"; //$NON-NLS-1$
	public static final String OP_RETURN = "op_return"; //$NON-NLS-1$
	public static final String REDIRECT = "redirect"; //$NON-NLS-1$
	static final String OPENID_IDENTIFIER = "openid_identifier"; //$NON-NLS-1$
	static final String OPENID_DISC = "openid-disc"; //$NON-NLS-1$
	private static Map<String, IOrionCredentialsService> userStores = new HashMap<String, IOrionCredentialsService>();
	private static IOrionCredentialsService defaultUserAdmin;

	private static IOrionUserProfileService userProfileService;

	private static boolean allowAnonymousAccountCreation;

	static {
		//if there is no list of users authorised to create accounts, it means everyone can create accounts
		allowAnonymousAccountCreation = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_USER_CREATION, null) == null; //$NON-NLS-1$
	}

	/**
	 * Checks session attributes to retrieve authenticated user identifier.
	 * 
	 * @param req
	 * @return OpenID identifier of authenticated user of <code>null</code> if
	 *         user is not authenticated
	 * @throws IOException
	 */
	public static String getAuthenticatedUser(HttpServletRequest req) throws IOException {
		HttpSession s = req.getSession(true);
		if (s.getAttribute("user") != null) { //$NON-NLS-1$
			return (String) s.getAttribute("user"); //$NON-NLS-1$
		}
		return null;
	}

	/**
	 * Redirects to OpenId provider stored in <code>openid</code> parameter of
	 * request. If user is to be redirected after login perform the redirect
	 * site should be stored in <code>redirect<code> parameter.
	 * 
	 * @param req
	 * @param resp
	 * @param consumer
	 *            {@link OpenidConsumer} used to login user by OpenId. The same
	 *            consumer should be used for
	 *            {@link #handleOpenIdReturn(HttpServletRequest, HttpServletResponse, OpenidConsumer)}
	 *            . If <code>null</code> the new {@link OpenidConsumer} will be
	 *            created and returned
	 * @return
	 * @throws IOException
	 */
	public static OpenidConsumer redirectToOpenIdProvider(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException {
		String redirect = req.getParameter(REDIRECT);
		try {
			StringBuffer sb = getRequestServer(req);
			sb.append(req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo())); //$NON-NLS-1$
			sb.append("?").append(OP_RETURN).append("=true"); //$NON-NLS-1$ //$NON-NLS-2$
			if (redirect != null && redirect.length() > 0) {
				sb.append("&").append(REDIRECT).append("="); //$NON-NLS-1$ //$NON-NLS-2$
				sb.append(redirect);
			}
			consumer = new OpenidConsumer(sb.toString());
			consumer.authRequest(req.getParameter(OPENID), req, resp);
			// redirection takes place in the authRequest method
		} catch (ConsumerException e) {
			writeOpenIdError(e.getMessage(), req, resp);
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, "An error occured when creating OpenidConsumer", e));
		} catch (CoreException e) {
			writeOpenIdError(e.getMessage(), req, resp);
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, "An error occured when authenticing request", e));
		}
		return consumer;
	}

	private static void writeOpenIdError(String error, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (req.getParameter(REDIRECT) == null) {
			PrintWriter out = resp.getWriter();
			out.println("<html><head></head>"); //$NON-NLS-1$
			// TODO: send a message using
			// window.eclipseMessage.postImmediate(otherWindow, message) from
			// /org.eclipse.e4.webide/web/orion/message.js
			out.print("<body onload=\"window.opener.handleOpenIDResponse((window.location+'').split('?')[1],'");
			out.print(error);
			out.println("');window.close();\">"); //$NON-NLS-1$
			out.println("</body>"); //$NON-NLS-1$
			out.println("</html>"); //$NON-NLS-1$

			out.close();
			return;
		}
		PrintWriter out = resp.getWriter();
		out.println("<html><head></head>"); //$NON-NLS-1$
		// TODO: send a message using
		// window.eclipseMessage.postImmediate(otherWindow, message) from
		// /org.eclipse.e4.webide/web/orion/message.js

		String url = req.getParameter(REDIRECT);
		url = url.replaceAll("/&error(\\=[^&]*)?(?=&|$)|^error(\\=[^&]*)?(&|$)/", ""); // remove
																						// "error"
																						// parameter
		out.print("<body onload=\"window.location.replace('");
		out.print(url.toString());
		if (url.contains("?")) {
			out.print("&error=");
		} else {
			out.print("?error=");
		}
		out.print(new String(Base64.encode(error.getBytes())));
		out.println("');\">"); //$NON-NLS-1$
		out.println("</body>"); //$NON-NLS-1$
		out.println("</html>"); //$NON-NLS-1$
	}

	private static boolean canAddUsers() {
		return allowAnonymousAccountCreation ? defaultUserAdmin.canCreateUsers() : false;
	}

	/**
	 * Parses the response from OpenId provider. If <code>redirect</code>
	 * parameter is not set closes the current window.
	 * 
	 * @param req
	 * @param resp
	 * @param consumer
	 *            same {@link OpenidConsumer} as used in
	 *            {@link #redirectToOpenIdProvider(HttpServletRequest, HttpServletResponse, OpenidConsumer)}
	 * @throws IOException
	 */
	public static void handleOpenIdReturn(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException {
		String redirect = req.getParameter(REDIRECT);
		String op_return = req.getParameter(OP_RETURN);
		if (Boolean.parseBoolean(op_return) && consumer != null) {
			Identifier id = consumer.verifyResponse(req);
			if (id == null || id.getIdentifier() == null || id.getIdentifier().equals("")) {
				writeOpenIdError("Authentication response is not sufficient", req, resp);
				return;
			}
			Set<User> users = defaultUserAdmin.getUsersByProperty("openid", ".*\\Q" + id.getIdentifier() + "\\E.*", true);
			User user;
			if (users.size() > 0) {
				user = users.iterator().next();
			} else if (canAddUsers()) {
				User newUser = new User(id.getIdentifier());
				newUser.addProperty("openid", id.getIdentifier());
				user = defaultUserAdmin.createUser(newUser);
			} else {
				writeOpenIdError("Your authentication was successful but you are not authorized to access Orion", req, resp);
				return;
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

			if (redirect != null) {
				resp.sendRedirect(redirect);
				return;
			}
			PrintWriter out = resp.getWriter();
			out.println("<html><head></head>"); //$NON-NLS-1$
			// TODO: send a message using
			// window.eclipseMessage.postImmediate(otherWindow, message) from
			// /org.eclipse.e4.webide/web/js/message.js
			out.println("<body onload=\"window.opener.handleOpenIDResponse((window.location+'').split('?')[1]);window.close();\">"); //$NON-NLS-1$
			out.println("</body>"); //$NON-NLS-1$
			out.println("</html>"); //$NON-NLS-1$

			out.close();
			return;
		}
	}

	private static StringBuffer getRequestServer(HttpServletRequest req) {
		StringBuffer url = new StringBuffer();
		String scheme = req.getScheme();
		int port = req.getServerPort();

		url.append(scheme);
		url.append("://"); //$NON-NLS-1$
		url.append(req.getServerName());
		if ((scheme.equals("http") && port != 80) //$NON-NLS-1$
				|| (scheme.equals("https") && port != 443)) { //$NON-NLS-1$
			url.append(':');
			url.append(req.getServerPort());
		}
		return url;
	}

	/**
	 * Destroys the session attributes that identify the user.
	 * 
	 * @param req
	 */
	public static void performLogout(HttpServletRequest req) {
		HttpSession s = req.getSession(true);
		if (s.getAttribute("user") != null) { //$NON-NLS-1$
			s.removeAttribute("user"); //$NON-NLS-1$
		}
	}

	/**
	 * Writes a response in JSON that contains user login.
	 * 
	 * @param login
	 * @param resp
	 * @throws IOException
	 */
	public static void writeLoginResponse(String login, HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		try {
			JSONObject array = new JSONObject();
			array.put("login", login); //$NON-NLS-1$
			resp.getWriter().print(array.toString());
		} catch (JSONException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, "An error occured when creating JSON object for logged in user", e));
		}
	}

	public static String getAuthType() {
		return "OpenId"; //$NON-NLS-1$
	}

	public static IOrionCredentialsService getDefaultUserAdmin() {
		return defaultUserAdmin;
	}

	public void setUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin instanceof IOrionCredentialsService) {
			IOrionCredentialsService eclipseWebUserAdmin = (IOrionCredentialsService) userAdmin;
			userStores.put(eclipseWebUserAdmin.getStoreName(), eclipseWebUserAdmin);
			if (defaultUserAdmin == null || UserAdminActivator.eclipseWebUsrAdminName.equals(eclipseWebUserAdmin.getStoreName())) {
				defaultUserAdmin = eclipseWebUserAdmin;
			}
		}
	}

	public void unsetUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin instanceof IOrionCredentialsService) {
			IOrionCredentialsService eclipseWebUserAdmin = (IOrionCredentialsService) userAdmin;
			userStores.remove(eclipseWebUserAdmin.getStoreName());
			if (userAdmin.equals(defaultUserAdmin)) {
				Iterator<IOrionCredentialsService> iterator = userStores.values().iterator();
				if (iterator.hasNext())
					defaultUserAdmin = iterator.next();
			}
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
}
