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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.discovery.Identifier;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

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
	private static IOrionCredentialsService userAdmin;
	private static List<OpendIdProviderDescription> defaultOpenids;

	private static IOrionUserProfileService userProfileService;

	private HttpService httpService;

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
	 *            {@link #handleOpenIdReturnAndLogin(HttpServletRequest, HttpServletResponse, OpenidConsumer)}
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
		return allowAnonymousAccountCreation ? userAdmin.canCreateUsers() : false;
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
	public static void handleOpenIdReturnAndLogin(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException {
		String redirect = req.getParameter(REDIRECT);
		String op_return = req.getParameter(OP_RETURN);
		if (Boolean.parseBoolean(op_return) && consumer != null) {
			Identifier id = consumer.verifyResponse(req);
			if (id == null || id.getIdentifier() == null || id.getIdentifier().equals("")) {
				writeOpenIdError("Authentication response is not sufficient", req, resp);
				return;
			}
			Set<User> users = userAdmin.getUsersByProperty("openid", ".*\\Q" + id.getIdentifier() + "\\E.*", true);
			User user;
			if (users.size() > 0) {
				user = users.iterator().next();
			} else if (canAddUsers()) {
				User newUser = new User();
				newUser.setName(id.getIdentifier());
				newUser.addProperty("openid", id.getIdentifier());
				user = userAdmin.createUser(newUser);
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

			return;
		}
	}

	public static void handleOpenIdReturn(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException {
		String op_return = req.getParameter(OP_RETURN);
		if (Boolean.parseBoolean(op_return) && consumer != null) {
			Identifier id = consumer.verifyResponse(req);
			if (id == null || id.getIdentifier() == null || id.getIdentifier().equals("")) {
				writeOpenIdError("Authentication response is not sufficient", req, resp);
				return;
			}

			PrintWriter out = resp.getWriter();
			out.println("<html><head></head>"); //$NON-NLS-1$
			// TODO: send a message using
			// window.eclipseMessage.postImmediate(otherWindow, message) from
			// /org.eclipse.e4.webide/web/js/message.js
			out.println("<body onload=\"window.opener.handleOpenIDResponse('" + id.getIdentifier() + "');window.close();\">"); //$NON-NLS-1$  //$NON-NLS-2$
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
		return userAdmin;
	}

	public void setUserAdmin(IOrionCredentialsService userAdmin) {
		OpenIdHelper.userAdmin = userAdmin;
	}

	public void unsetUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin.equals(OpenIdHelper.userAdmin)) {
			OpenIdHelper.userAdmin = null;
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

	public void setHttpService(HttpService hs) {
		httpService = hs;

		HttpContext httpContext = new BundleEntryHttpContext(Activator.getBundleContext().getBundle());

		try {
			httpService.registerResources("/openids", "/openids", httpContext);
		} catch (NamespaceException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, 1, "A namespace error occured when registering servlets", e));
		}
	}

	public void unsetHttpService(HttpService hs) {
		if (httpService != null) {
			httpService.unregister("/openids"); //$NON-NLS-1$
			httpService = null;
		}
	}

	private static String getFileContents(String filename) throws IOException {
		StringBuilder sb = new StringBuilder();
		InputStream is = Activator.getBundleContext().getBundle().getEntry(filename).openStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line = ""; //$NON-NLS-1$
		while ((line = br.readLine()) != null) {
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

	private static OpendIdProviderDescription getOpenidProviderFromJson(JSONObject json) throws JSONException {
		OpendIdProviderDescription provider = new OpendIdProviderDescription();
		String url = json.getString("url");
		provider.setAuthSite(url);

		try {
			String name = json.getString("name");
			provider.setName(name);
		} catch (JSONException e) {
			// ignore, Name is not mandatory
		}
		try {
			String image = json.getString("image");
			provider.setImage(image);
		} catch (JSONException e) {
			// ignore, Image is not mandatory
		}
		return provider;
	}

	public static List<OpendIdProviderDescription> getSupportedOpenIdProviders(String openids) throws JSONException {
		List<OpendIdProviderDescription> opendIdProviders = new ArrayList<OpendIdProviderDescription>();
		JSONArray openidArray = new JSONArray(openids);
		for (int i = 0; i < openidArray.length(); i++) {
			JSONObject jsonProvider = openidArray.getJSONObject(i);
			try {
				opendIdProviders.add(getOpenidProviderFromJson(jsonProvider));
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, "Cannot load OpenId provider, invalid entry " + jsonProvider + " Attribute \"ulr\" is mandatory", e));
			}
		}
		return opendIdProviders;
	}

	public static List<OpendIdProviderDescription> getDefaultOpenIdProviders() {
		try {
			if (defaultOpenids == null) {
				defaultOpenids = getSupportedOpenIdProviders(getFileContents("/openids/DefaultOpenIdProviders.json")); //$NON-NLS-1$
			}
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_OPENID_CORE, "Cannot load default openid list, JSON format expected", e)); //$NON-NLS-1$
			return new ArrayList<OpendIdProviderDescription>();
		}
		return defaultOpenids;
	}
}
