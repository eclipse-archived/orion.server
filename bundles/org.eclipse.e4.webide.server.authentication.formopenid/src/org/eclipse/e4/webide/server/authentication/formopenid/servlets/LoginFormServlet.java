/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.webide.server.authentication.formopenid.servlets;

import static org.eclipse.e4.webide.server.authentication.formopenid.FormOpenIdAuthenticationService.OPENIDS_PROPERTY;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.e4.webide.server.LogHelper;
import org.eclipse.e4.webide.server.authentication.form.core.FormAuthHelper;
import org.eclipse.e4.webide.server.authentication.formopenid.Activator;
import org.eclipse.e4.webide.server.authentication.formopenid.FormOpenIdAuthenticationService;
import org.eclipse.e4.webide.server.authentication.formopenid.internal.OpendIdProviderDescription;
import org.eclipse.e4.webide.server.resources.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;

/**
 * Displays login page on every request regardless the method. If
 * <code>EclipseWeb-Version</code> header is set it returns a code that displays
 * the modal window containing login form. The modal window code should be
 * evaluated by the client.
 * 
 */
public class LoginFormServlet extends HttpServlet {

	private static final long serialVersionUID = -1941415021420599704L;
	private String newAccountLink = "/users/create"; //$NON-NLS-1$
	private String newAccountJsFunction = "javascript:addUser"; //$NON-NLS-1$
	private List<OpendIdProviderDescription> defaultOpenids;
	private FormOpenIdAuthenticationService authenticationService;

	public LoginFormServlet(FormOpenIdAuthenticationService authenticationService) {
		super();
		this.authenticationService = authenticationService;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// handled by service()
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// handled by service()
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// handled by service()
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// handled by service()
	}

	private OpendIdProviderDescription getOpenidProviderFromJson(JSONObject json) throws JSONException {
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

	private List<OpendIdProviderDescription> getSupportedOpenIdProviders(String openids) throws JSONException {
		List<OpendIdProviderDescription> opendIdProviders = new ArrayList<OpendIdProviderDescription>();
		JSONArray openidArray = new JSONArray(openids);
		for (int i = 0; i < openidArray.length(); i++) {
			JSONObject jsonProvider = openidArray.getJSONObject(i);
			try {
				opendIdProviders.add(getOpenidProviderFromJson(jsonProvider));
			} catch (JSONException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, "Cannot load OpenId provider, invalid entry " + jsonProvider + " Attribute \"ulr\" is mandatory"));
			}
		}
		return opendIdProviders;
	}

	private List<OpendIdProviderDescription> getDefaultOpenIdProviders() {
		try {
			if (defaultOpenids == null) {
				defaultOpenids = getSupportedOpenIdProviders(getFileContents("/openids/DefaultOpenIdProviders.json"));
			}
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, "Cannot load default openid list, JSON format expected"));
			return new ArrayList<OpendIdProviderDescription>();
		}
		return defaultOpenids;
	}

	private String getConfiguredOpenIds() {
		return (String) (authenticationService.getDefaultAuthenticationProperties() == null ? null : authenticationService.getDefaultAuthenticationProperties().get(OPENIDS_PROPERTY));
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		super.service(req, resp);
		if (!resp.isCommitted()) {
			List<OpendIdProviderDescription> openidProviders;
			String customOpenids = req.getAttribute(OPENIDS_PROPERTY) == null ? getConfiguredOpenIds() : (String) req.getAttribute(OPENIDS_PROPERTY);
			if (customOpenids == null || customOpenids.trim().length() == 0) {
				openidProviders = getDefaultOpenIdProviders();
			} else {
				try {
					openidProviders = getSupportedOpenIdProviders(customOpenids);
				} catch (JSONException e) {
					LogHelper.log(new Status(IStatus.ERROR, Activator.PI_FORMOPENID_SERVLETS, "Cannot load openid list, JSON format expected"));
					openidProviders = getDefaultOpenIdProviders();
				}
			}

			// redirection from FormAuthenticationService.setNotAuthenticated
			String versionString = req.getHeader("EclipseWeb-Version"); //$NON-NLS-1$
			Version version = versionString == null ? null : new Version(versionString);

			// TODO: This is a workaround for calls
			// that does not include the WebEclipse version header
			String xRequestedWith = req.getHeader("X-Requested-With"); //$NON-NLS-1$

			if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) { //$NON-NLS-1$
				writeHtmlResponse(req, resp, openidProviders);
			} else {
				writeJavaScriptResponse(req, resp, openidProviders);
			}
		}
	}

	private void writeJavaScriptResponse(HttpServletRequest req, HttpServletResponse resp, List<OpendIdProviderDescription> openids) throws IOException {
		resp.setContentType("text/javascript"); //$NON-NLS-1$
		PrintWriter writer = resp.getWriter();
		writer.print("if(!stylg)\n"); //$NON-NLS-1$
		writer.print("var stylg=document.createElement(\"link\");"); //$NON-NLS-1$
		writer.print("stylg.setAttribute(\"rel\", \"stylesheet\");"); //$NON-NLS-1$
		writer.print("stylg.setAttribute(\"type\", \"text/css\");"); //$NON-NLS-1$
		writer.print("stylg.setAttribute(\"href\", \""); //$NON-NLS-1$
		writer.print(getStyles(req.getParameter("styles"))); //$NON-NLS-1$
		writer.print("\");"); //$NON-NLS-1$
		writer.print("if(!divg)\n"); //$NON-NLS-1$
		writer.print("var divg = document.createElement(\"span\");\n"); //$NON-NLS-1$
		writer.print("divg.innerHTML='"); //$NON-NLS-1$
		writer.print(loadJSResponse(req, openids));
		writer.print("setUserStore('");
		writer.print(FormAuthHelper.getDefaultUserAdmin().getStoreName());
		writer.print("');");
		String path = req.getPathInfo();
		if (path.startsWith("/login")) { //$NON-NLS-1$
			writer.print("login();"); //$NON-NLS-1$
		} else if (path.startsWith("/checkuser")) { //$NON-NLS-1$
			writer.print("checkUser();"); //$NON-NLS-1$
		}

		writer.flush();
	}

	private String getStyles(String stylesParam) {
		if (stylesParam == null || stylesParam.length() == 0) {
			return "/mixloginstatic/css/defaultLoginWindow.css"; //$NON-NLS-1$
		} else {

			return stylesParam.replaceAll("'", "\\\\'").replaceAll("\\t+", " ") //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$
					.replaceAll("\n", ""); //$NON-NLS-1$//$NON-NLS-2$
		}
	}

	private String loadJSResponse(HttpServletRequest req, List<OpendIdProviderDescription> openids) throws IOException {

		StringBuilder sb = new StringBuilder();
		StringBuilder authString = new StringBuilder();
		appendFileContentAsJsString(authString, "static/auth.html"); //$NON-NLS-1$
		String authSite = replaceNewAccount(authString.toString(), req.getHeader("Referer"), true); //$NON-NLS-1$
		authSite = replaceError(authSite, ""); //$NON-NLS-1$
		authSite = replaceOpenidList(authSite, openids, true);
		authSite = replaceUserStores(authSite, true);
		sb.append(authSite);
		sb.append("';\n"); //$NON-NLS-1$
		sb.append("var scr = '"); //$NON-NLS-1$
		appendFileContentAsJsString(sb, "static/js/xhrAuth.js"); //$NON-NLS-1$
		sb.append("';\n"); //$NON-NLS-1$
		sb.append(getFileContents("static/js/loadXhrAuth.js")); //$NON-NLS-1$

		return sb.toString();

	}

	private String getFileContents(String filename) throws IOException {
		StringBuilder sb = new StringBuilder();
		InputStream is = Activator.getBundleContext().getBundle().getEntry(filename).openStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line = ""; //$NON-NLS-1$
		while ((line = br.readLine()) != null) {
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

	private void appendFileContentAsJsString(StringBuilder sb, String filename) throws IOException {
		InputStream is = Activator.getBundleContext().getBundle().getEntry(filename).openStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line = ""; //$NON-NLS-1$
		while ((line = br.readLine()) != null) {
			// escaping ' characters
			line = line.replaceAll("'", "\\\\'"); //$NON-NLS-1$ //$NON-NLS-2$
			// remove tabs
			line = line.replaceAll("\\t+", " "); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append(line);
		}
	}

	private void writeHtmlResponse(HttpServletRequest req, HttpServletResponse response, List<OpendIdProviderDescription> openids) throws IOException {
		response.setContentType("text/html"); //$NON-NLS-1$
		PrintWriter writer = response.getWriter();
		writer.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">"); //$NON-NLS-1$
		writer.println("<html>"); //$NON-NLS-1$
		writer.println("<head>"); //$NON-NLS-1$
		writer.println("<meta name=\"copyright\" content=\"Copyright (c) IBM Corporation and others 2010.\" >");
		writer.println("<meta http-equiv=\"Content-Language\" content=\"en-us\">");
		writer.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\">");
		writer.println("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=8\">");

		writer.println("<title>Login Page</title>");
		if (req.getParameter("styles") == null //$NON-NLS-1$
				|| "".equals(req.getParameter("styles"))) { //$NON-NLS-1$ //$NON-NLS-2$
			writer.println("<style type=\"text/css\">"); //$NON-NLS-1$
			writer.print("@import \""); //$NON-NLS-1$
			writer.print("/mixloginstatic/css/defaultLoginWindow.css"); //$NON-NLS-1$
			writer.print("\";"); //$NON-NLS-1$
			writer.println("</style>"); //$NON-NLS-1$
		} else {
			writer.print("<link rel=\"stylesheet\" type=\"text/css\" href=\""); //$NON-NLS-1$
			writer.print(req.getParameter("styles")); //$NON-NLS-1$
			writer.print("\">"); //$NON-NLS-1$
		}
		writer.println("<script type=\"text/javascript\"><!--"); //$NON-NLS-1$
		writer.println("function confirm() {}"); //$NON-NLS-1$
		writer.println(getFileContents("static/js/htmlAuth.js")); //$NON-NLS-1$
		writer.println("//--></script>"); //$NON-NLS-1$
		writer.println("</head>"); //$NON-NLS-1$
		writer.print("<body onLoad=\"javascript:setUserStore('"); //$NON-NLS-1$
		writer.print(FormAuthHelper.getDefaultUserAdmin().getStoreName());
		writer.println("');\">"); //$NON-NLS-1$

		String authSite = getFileContents("static/auth.html"); //$NON-NLS-1$
		authSite = replaceForm(authSite, req.getParameter("redirect")); //$NON-NLS-1$
		authSite = replaceNewAccount(authSite, ((req.getParameter("redirect") == null) ? req.getRequestURI() //$NON-NLS-1$
				: req.getParameter("redirect")), false); //$NON-NLS-1$
		authSite = replaceError(authSite, req.getParameter("error")); //$NON-NLS-1$
		authSite = replaceOpenidList(authSite, openids, false);
		authSite = replaceUserStores(authSite, false);
		writer.println(authSite);

		writer.println("</body>"); //$NON-NLS-1$
		writer.println("</html>"); //$NON-NLS-1$
		writer.flush();
	}

	private String replaceError(String authSite, String error) {
		if (error == null) {
			return authSite;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<div id=\"errorWin\">");
		sb.append("<ul id=\"loginError\">"); //$NON-NLS-1$
		sb.append("<li id=\"errorMessage\">"); //$NON-NLS-1$
		sb.append(new String(Base64.decode(error.getBytes())));
		sb.append("</li></ul>"); //$NON-NLS-1$
		sb.append("</div>");
		return authSite.replaceAll("<!--ERROR-->", sb.toString()); //$NON-NLS-1$
	}

	private String replaceForm(String authSite, String redirect) {
		StringBuilder formBegin = new StringBuilder();
		formBegin.append("<form name=\"AuthForm\" method=post action=\"/login/form"); //$NON-NLS-1$
		if (redirect != null && !redirect.equals("")) { //$NON-NLS-1$
			formBegin.append("?redirect="); //$NON-NLS-1$
			formBegin.append(redirect);
		}
		formBegin.append("\">"); //$NON-NLS-1$
		formBegin.append("<input id=\"store\" name=\"store\" type=\"hidden\" value=\"" + FormAuthHelper.getDefaultUserAdmin().getStoreName() + "\">");
		return authSite.replace("<!--form-->", formBegin.toString()).replace( //$NON-NLS-1$
				"<!--/form-->", "</form>"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private String getNewAccountJsLink(String redirect, String userStore) {
		return newAccountJsFunction + "(\\'" + redirect + "\\'" + (userStore == null ? ")" : ", \\'" + userStore + "\\')");
	}

	private String getNewAccountHtmlLink(String redirect, String userStore) {
		return this.newAccountLink + "?redirect=" //$NON-NLS-1$ //$NON-NLS-2$
				+ redirect + (userStore == null ? "" : ("&store=" + userStore));//$NON-NLS-1$ //$NON-NLS-2$
	}

	private String replaceNewAccount(String authSite, String redirect, boolean javascriptResp) {
		if (!FormAuthHelper.canAddUsers()) {
			return authSite;
		}
		String newAccountA = ""; //$NON-NLS-1$
		String userStore = FormAuthHelper.getDefaultUserAdmin().getStoreName();
		String newAccountLink = javascriptResp ? getNewAccountJsLink(redirect, userStore) : getNewAccountHtmlLink(redirect, userStore);
		if (newAccountLink != null && !"".equals(newAccountLink)) { //$NON-NLS-1$
			newAccountA = "<div class=\"hrloginWindow\"></div><h3 class=\"loginWindow\">New to EclipseWeb? <a class=\"loginWindow\" href=\"" + newAccountLink //$NON-NLS-1$
					+ "\">Create " + userStore + " account</a></h3>"; //$NON-NLS-1$
		}
		return authSite.replace("<!--NEW_ACCOUNT_LINK-->", newAccountA); //$NON-NLS-1$
	}

	private String replaceUserStores(String authSite, boolean isJsResponce) {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String store : FormAuthHelper.getSupportedUserStores()) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(" | ");
			}
			if (isJsResponce) {
				sb.append("<a href=\"javascript:setUserStore(\\\\'");
			} else {
				sb.append("<a href=\"javascript:setUserStore('");
			}
			sb.append(store);
			if (isJsResponce) {
				sb.append("\\\\')\" id=\"Login_");
			} else {
				sb.append("')\" id=\"Login_");
			}
			sb.append(store);
			sb.append("\">");
			sb.append(store);
			sb.append("</a>");
		}

		return authSite.replaceAll("<!--LOGIN STORES-->", sb.toString());
	}

	private String replaceOpenidList(String authSite, List<OpendIdProviderDescription> openids, boolean javascriptResp) {
		if (openids == null || openids.isEmpty()) {
			return authSite;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"hrloginWindow\"></div>");
		sb.append("<h3 class=\"loginWindow\">Login with: ");
		for (OpendIdProviderDescription openid : openids) {
			sb.append(javascriptResp ? openid.toJsImage() : openid.toHtmlImage());
		}
		sb.append("</h3>");
		return authSite.replace("<!--OpenID list-->", sb.toString());
	}
}
