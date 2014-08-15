/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.openid;

import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.authentication.Activator;
import org.eclipse.orion.server.authentication.openid.OpenIdConstants;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.core.events.IEventService;
import org.eclipse.orion.server.user.profile.*;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.discovery.Identifier;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static IEventService eventService;

	private HttpService httpService;

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
	 * @return the authenticated open id consumer.
	 * @throws IOException
	 * @throws OpenIdException 
	 */
	public static OpenidConsumer redirectToOpenIdProvider(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException, OpenIdException {
		String redirect = req.getParameter(REDIRECT);
		try {
			StringBuffer sb = getAuthServerRequest(req);
			sb.append("?").append(OP_RETURN).append("=true"); //$NON-NLS-1$ //$NON-NLS-2$
			if (redirect != null && redirect.length() > 0) {
				sb.append("&").append(REDIRECT).append("="); //$NON-NLS-1$ //$NON-NLS-2$

				// need to urlencode redirect (URLEncoder form encodes rest of the replaces handle differences when on a browser address bar)
				sb.append(URLEncoder.encode(redirect, "UTF-8").replaceAll("\\+", "%20").replaceAll("\\%21", "!").replaceAll("\\%27", "'").replaceAll("\\%28", "(").replaceAll("\\%29", ")").replaceAll("\\%7E", "~"));
			}
			consumer = new OpenidConsumer(sb.toString());
			consumer.authRequest(req.getParameter(OPENID), req, resp);
			// redirection takes place in the authRequest method
		} catch (ConsumerException e) {
			throw new OpenIdException(e);
		} catch (CoreException e) {
			throw new OpenIdException(e);
		}
		return consumer;
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
	 * @throws OpenIdException 
	 */
	public static void handleOpenIdReturnAndLogin(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException, OpenIdException {
		String redirect = req.getParameter(REDIRECT);
		String op_return = req.getParameter(OP_RETURN);
		if (Boolean.parseBoolean(op_return) && consumer != null) {
			Identifier id = consumer.verifyResponse(req);
			if (id == null || id.getIdentifier() == null || id.getIdentifier().equals("")) {
				throw new OpenIdException("Authentication response is not sufficient");
			}
			Set<User> users = userAdmin.getUsersByProperty("openid", ".*\\Q" + id.getIdentifier() + "\\E.*", true, false);
			User user;
			if (users.size() > 0) {
				user = users.iterator().next();
			} else {
				throw new OpenIdException("There is no Orion account associated with this Id. Please register or contact your system administrator for assistance.");
			}

			req.getSession().setAttribute("user", user.getUid()); //$NON-NLS-1$

			if (getEventService() != null) {
				JSONObject message = new JSONObject();
				try {
					SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
					Date date = new Date(System.currentTimeMillis());
					message.put("event", "login");
					message.put("published", format.format(date));
					message.put("user", user.getUid());
				} catch (JSONException e1) {
					LogHelper.log(e1);
				}
				getEventService().publish("orion/login", message);
			}

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

	public static void handleOpenIdReturn(HttpServletRequest req, HttpServletResponse resp, OpenidConsumer consumer) throws IOException, OpenIdException {
		String op_return = req.getParameter(OP_RETURN);
		if (Boolean.parseBoolean(op_return) && consumer != null) {
			Identifier id = consumer.verifyResponse(req);
			if (id == null || id.getIdentifier() == null || id.getIdentifier().equals("")) {
				throw new OpenIdException("Authentication response is not sufficient");
			}

			PrintWriter out = resp.getWriter();
			resp.setContentType("text/html; charset=UTF-8");
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

	/**
	 * Returns the string representation of the request  to use for the redirect (the URL
	 * to return to after openid login completes). The returned URL will include the host and
	 * path, but no query parameters. If this server has not been configured with a different
	 * authentication host, then the server that received this request is considered to
	 * be the authentication server.
	 */
	static StringBuffer getAuthServerRequest(HttpServletRequest req) {
		//use authentication host for redirect because we may be sitting behind a proxy
		String hostPref = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_HOST, null);
		//assume non-proxy direct server connection if no auth host defined
		if (hostPref == null)
			return req.getRequestURL();
		StringBuffer result = new StringBuffer(hostPref);
		result.append(req.getServletPath());
		if (req.getPathInfo() != null)
			result.append(req.getPathInfo());

		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.openid"); //$NON-NLS-1$
		if (logger.isInfoEnabled())
			logger.info("Auth server redirect: " + result.toString()); //$NON-NLS-1$ 
		return result;
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
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, 1, "A namespace error occured when registering servlets", e));
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

	private static OpendIdProviderDescription getOpenidProviderFromJson(JSONObject json) throws JSONException {
		OpendIdProviderDescription provider = new OpendIdProviderDescription();
		String url = json.getString(OpenIdConstants.KEY_URL);
		provider.setAuthSite(url);

		try {
			String name = json.getString(ProtocolConstants.KEY_NAME);
			provider.setName(name);
		} catch (JSONException e) {
			// ignore, Name is not mandatory
		}
		try {
			String image = json.getString(OpenIdConstants.KEY_IMAGE);
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
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, "Cannot load OpenId provider, invalid entry " + jsonProvider + " Attribute \"ulr\" is mandatory", e));
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
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_AUTHENTICATION_SERVLETS, "Cannot load default openid list, JSON format expected", e)); //$NON-NLS-1$
			return new ArrayList<OpendIdProviderDescription>();
		}
		return defaultOpenids;
	}

	private static IEventService getEventService() {
		if (eventService == null) {
			BundleContext context = Activator.getBundleContext();
			ServiceReference<IEventService> eventServiceRef = context.getServiceReference(IEventService.class);
			if (eventServiceRef == null) {
				// Event service not available
				return null;
			}
			eventService = context.getService(eventServiceRef);
			if (eventService == null) {
				// Event service not available
				return null;
			}
		}
		return eventService;
	}
}
