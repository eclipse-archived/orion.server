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
package org.eclipse.orion.server.authentication.form.servlets;

import org.eclipse.orion.server.core.resources.Base64;

import org.eclipse.orion.server.authentication.form.core.FormAuthHelper;

import org.eclipse.orion.server.authentication.form.Activator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.Version;

/**
 * Displays login page on every request regardless the method. If
 * <code>Orion-Version</code> header is set it returns a code that displays
 * the modal window containing login form. The modal window code should be
 * evaluated by the client.
 * 
 */
public class LoginFormServlet extends HttpServlet {

	private static final long serialVersionUID = -7686742575461695377L;
	private String newAccountLink = "/users/create";
	private String newAccountJsFunction = "javascript:addUser";

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

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		super.service(req, resp);
		if (!resp.isCommitted()) {
			// redirection from FormAuthenticationService.setNotAuthenticated
			String versionString = req.getHeader("Orion-Version");
			Version version = versionString == null ? null : new Version(versionString);

			// TODO: This is a workaround for calls
			// that does not include the WebEclipse version header
			String xRequestedWith = req.getHeader("X-Requested-With");

			if (version == null && !"XMLHttpRequest".equals(xRequestedWith)) {
				writeHtmlResponse(req, resp);
			} else {
				writeJavaScriptResponse(req, resp);
			}
		}
	}

	private void writeJavaScriptResponse(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("text/javascript");
		PrintWriter writer = resp.getWriter();
		writer.print("if(!stylg)\n");
		writer.print("var stylg=document.createElement(\"link\");");
		writer.print("stylg.setAttribute(\"rel\", \"stylesheet\");");
		writer.print("stylg.setAttribute(\"type\", \"text/css\");");
		writer.print("stylg.setAttribute(\"href\", \"");
		writer.print(getStyles(req.getParameter("styles")));
		writer.print("\");");
		writer.print("if(!divg)\n");
		writer.print("var divg = document.createElement(\"span\");\n");
		writer.print("divg.innerHTML='");
		writer.print(loadJSResponse(req));
		String path = req.getPathInfo();
		if (path.startsWith("/login")) {
			writer.print("login();");
		} else if (path.startsWith("/checkuser")) {
			writer.print("checkUser();");
		}

		writer.flush();
	}

	private String getStyles(String stylesParam) {
		if (stylesParam == null || stylesParam.length() == 0) {
			return "/loginstatic/css/defaultLoginWindow.css";
		} else {

			return stylesParam.replaceAll("'", "\\\\'").replaceAll("\\t+", " ").replaceAll("\n", "");
		}
	}

	private String loadJSResponse(HttpServletRequest req) throws IOException {

		StringBuilder sb = new StringBuilder();
		StringBuilder authString = new StringBuilder();
		appendFileContentAsJsString(authString, "static/auth.html");
		sb.append(replaceError(replaceNewAccount(authString.toString(), req.getHeader("Referer"), true), ""));
		sb.append("';\n");
		sb.append("var scr = '");
		appendFileContentAsJsString(sb, "static/js/xhrAuth.js");
		sb.append("';\n");
		sb.append(getFileContents("static/js/loadXhrAuth.js"));

		return sb.toString();

	}

	private String getFileContents(String filename) throws IOException {
		StringBuilder sb = new StringBuilder();
		InputStream is = Activator.getBundleContext().getBundle().getEntry(filename).openStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line = "";
		while ((line = br.readLine()) != null) {
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

	private void appendFileContentAsJsString(StringBuilder sb, String filename) throws IOException {
		InputStream is = Activator.getBundleContext().getBundle().getEntry(filename).openStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line = "";
		while ((line = br.readLine()) != null) {
			// escaping ' characters
			line = line.replaceAll("'", "\\\\'");
			// remove tabs
			line = line.replaceAll("\\t+", " ");
			sb.append(line);
		}
	}

	private void writeHtmlResponse(HttpServletRequest req, HttpServletResponse response) throws IOException {

		PrintWriter writer = response.getWriter();
		writer.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">");
		writer.println("<html>");
		writer.println("<head>");
		writer.println("<title>Login Page</title>");
		if (req.getParameter("styles") == null || "".equals(req.getParameter("styles"))) {
			writer.println("<style type=\"text/css\">");
			writer.print("@import \"");
			writer.print("/loginstatic/css/defaultLoginWindow.css");
			writer.print("\";");
			writer.println("</style>");
		} else {
			writer.print("<link rel=\"stylesheet\" type=\"text/css\" href=\"");
			writer.print(req.getParameter("styles"));
			writer.print("\">");
		}
		writer.println("<script type=\"text/javascript\"><!--");
		writer.println("function confirm() {}");
		writer.println("//--></script>");
		writer.println("</head>");
		writer.println("<body>");
		writer.print("<form name=\"AuthForm\" method=post action=\"/login");
		if (req.getParameter("redirect") != null && !req.getParameter("redirect").equals("")) {
			writer.print("?redirect=");
			writer.print(req.getParameter("redirect"));
		}
		writer.println("\">");

		writer.println(replaceError(replaceNewAccount(getFileContents("static/auth.html"), ((req.getParameter("redirect") == null) ? req.getRequestURI() : req.getParameter("redirect")), false), req.getParameter("error")));

		writer.println("</form>");
		writer.println("</body>");
		writer.println("</html>");
		writer.flush();
	}

	private String replaceError(String authSite, String error) {
		if (error == null) {
			return authSite;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<div id=\"errorWin\">"); //$NON-NLS-1$
		sb.append("<ul id=\"loginError\">"); //$NON-NLS-1$
		sb.append("<li id=\"errorMessage\">"); //$NON-NLS-1$
		sb.append(new String(Base64.decode(error.getBytes())));
		sb.append("</li></ul></div>"); //$NON-NLS-1$
		return authSite.replaceAll("<!--ERROR-->", sb.toString()); //$NON-NLS-1$
	}

	private String replaceNewAccount(String authSite, String redirect, boolean javascriptResp) {
		if (!FormAuthHelper.canAddUsers()) {
			return authSite;
		}
		String newAccountA = "";
		String newAccountLink = javascriptResp ? newAccountJsFunction + "(\\'" + redirect + "\\')" : this.newAccountLink + "?redirect=" + redirect;
		if (newAccountLink != null && !"".equals(newAccountLink)) {
			newAccountA = "<a class=\"loginWindow\" href=\"" + newAccountLink + "\">Create new account</a>";
		}
		return authSite.replace("<!--NEW_ACCOUNT_LINK-->", newAccountA);
	}
}
