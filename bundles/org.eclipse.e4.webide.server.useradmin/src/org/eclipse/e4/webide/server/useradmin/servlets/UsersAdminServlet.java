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
package org.eclipse.e4.webide.server.useradmin.servlets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.e4.webide.server.servlets.EclipseWebServlet;
import org.eclipse.e4.webide.server.useradmin.EclipseWebUserAdmin;
import org.eclipse.e4.webide.server.useradmin.EclipseWebUserAdminRegistry;
import org.eclipse.e4.webide.server.useradmin.UnsupportedUserStoreException;
import org.eclipse.e4.webide.server.useradmin.User;
import org.eclipse.e4.webide.server.useradmin.UserAdminActivator;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.osgi.service.useradmin.Role;

//POST /users/ creates a new user
//POST /users/create creates a new user via form
//GET /users/create displays a form to create user

//GET /users/ gets list of users
//GET /users/[userId] gets user details
//DELETE /users/[usersId] deletes a user
//DELETE /users/roles/[usersId] removes roles for given a user
//PUT /users/[userId] updates user details
//PUT /users/roles/[userId] adds roles for given user
public class UsersAdminServlet extends EclipseWebServlet {

	private static final long serialVersionUID = -6809742538472682623L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathString = req.getPathInfo();
		if (pathString != null && pathString.startsWith("/create")) {
			displayCreateUserForm(req, resp, new ArrayList<String>());
		} else if (pathString == null || pathString.equals("/")) {
			Collection<User> users = getUserAdmin().getUsers();
			try {
				Set<JSONObject> userjsons = new HashSet<JSONObject>();
				for (User user : users) {
					userjsons.add(formJson(user));
				}
				JSONObject json = new JSONObject();
				json.put("users", userjsons);
				writeJSONResponse(req, resp, json);
			} catch (JSONException e) {
				handleException(resp, "Cannot get users list", e);
			}
		} else if (pathString.startsWith("/")) {
			String login = pathString.substring(1);
			try {
				User user = (User) getUserAdmin().getUser("login", login);
				if (user == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User login is required");
					return;
				}
				writeJSONResponse(req, resp, formJson(user));
			} catch (JSONException e) {
				handleException(resp, "Cannot get users details", e);
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || "/".equals(pathInfo)) {
			String createError = createUser(req.getParameter("store"), req.getParameter("login"), req.getParameter("name"), req.getParameter("email"), req.getParameter("workspace"), req.getParameter("password"), req.getParameter("roles"));

			if (createError != null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, createError);
			}
		} else if (pathInfo.startsWith("/create")) {
			String versionString = req.getHeader("EclipseWeb-Version");
			Version version = versionString == null ? null : new Version(versionString);

			String password1 = req.getParameter("password");
			String password2 = req.getParameter("passwordConf");
			if (password1 == null || !password1.equals(password2)) {
				List<String> errors = new ArrayList<String>();
				errors.add("Passwords do not match");
				displayCreateUserForm(req, resp, errors);
				return;
			}
			String createError = createUser(req.getParameter("store"), req.getParameter("login"), req.getParameter("name"), req.getParameter("email"), req.getParameter("workspace"), req.getParameter("password"), req.getParameter("roles"));
			if (createError != null) {
				List<String> errors = new ArrayList<String>();
				errors.add(createError);
				displayCreateUserForm(req, resp, errors);
				return;
			}
			if (req.getParameter("redirect") == null) {

				if (version == null) {
					RequestDispatcher rd = getServletContext().getRequestDispatcher("/login");
					rd.forward(req, resp);
					return;
				} else {
					// Redirecting to login page only for plain calls
				}
			} else {
				RequestDispatcher rd = getServletContext().getRequestDispatcher("/login/form?redirect=" + req.getParameter("redirect"));
				rd.forward(req, resp);
				return;
			}
		}
	}

	private String createUser(String userStore, String login, String name, String email, String workspace, String password, String roles) {
		if (login == null || login.length() == 0) {
			return "User login is required";
		}
		EclipseWebUserAdmin userAdmin;
		try {
			userAdmin = (userStore == null || "".equals(userStore)) ? getUserAdmin() : getUserAdmin(userStore);
		} catch (UnsupportedUserStoreException e) {
			return "User store is not available: " + userStore;
		}
		if (userAdmin.getUser("login", login) != null) {
			return "User " + login + " already exists";
		}
		User newUser = new User(login, name == null ? "" : name, password == null ? "" : password);
		if (roles != null) {
			StringTokenizer tokenizer = new StringTokenizer(roles, ",");
			while (tokenizer.hasMoreTokens()) {
				String role = tokenizer.nextToken();
				newUser.addRole(userAdmin.getRole(role));
			}
		}
		if (userAdmin.createUser(newUser) == null) {
			return "User could not be created";
		}
		return null;
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathString = req.getPathInfo();

		if (pathString.startsWith("/roles/")) {
			String login = pathString.substring("/roles/".length());
			User user = (User) getUserAdmin().getUser("login", login);
			if (user == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found");
				return;
			}
			if (req.getParameter("roles") != null) {
				String roles = req.getParameter("roles");
				if (roles != null) {
					StringTokenizer tokenizer = new StringTokenizer(roles, ",");
					while (tokenizer.hasMoreTokens()) {
						String role = tokenizer.nextToken();
						user.addRole(getUserAdmin().getRole(role));
					}
				}
			}

		} else if (pathString.startsWith("/")) {
			String login = pathString.substring(1);
			EclipseWebUserAdmin userAdmin;
			try {
				userAdmin = req.getParameter("store") == null ? getUserAdmin() : getUserAdmin(req.getParameter("store"));
			} catch (UnsupportedUserStoreException e) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User store not be found: " + req.getParameter("store"));
				return;
			}
			User user = (User) userAdmin.getUser("login", login);
			if (user == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found");
				return;
			}
			if (req.getParameter("login") != null) {
				user.setLogin(req.getParameter("login"));
			}
			if (req.getParameter("name") != null) {
				user.setName(req.getParameter("name"));
			}
			if (req.getParameter("password") != null) {
				user.setPassword(req.getParameter("password"));
			}
			if (req.getParameter("roles") != null) {
				user.getRoles().clear();
				String roles = req.getParameter("roles");
				if (roles != null) {
					StringTokenizer tokenizer = new StringTokenizer(roles, ",");
					while (tokenizer.hasMoreTokens()) {
						String role = tokenizer.nextToken();
						user.addRole(userAdmin.getRole(role));
					}
				}
			}
			userAdmin.updateUser(login, user);
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathString = req.getPathInfo();
		if (pathString.startsWith("/roles/")) {
			String login = pathString.substring("/roles/".length());
			User user = (User) getUserAdmin().getUser("login", login);
			if (user == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found");
			}
			if (req.getParameter("roles") != null) {
				String roles = req.getParameter("roles");
				if (roles != null) {
					StringTokenizer tokenizer = new StringTokenizer(roles, ",");
					while (tokenizer.hasMoreTokens()) {
						String role = tokenizer.nextToken();
						user.removeRole(getUserAdmin().getRole(role));
					}
				}
			}
		} else if (pathString.startsWith("/")) {
			String login = pathString.substring(1);
			EclipseWebUserAdmin userAdmin;
			try {
				userAdmin = req.getParameter("store") == null ? getUserAdmin() : getUserAdmin(req.getParameter("store"));
			} catch (UnsupportedUserStoreException e) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User store could not be found: " + req.getParameter("store"));
				return;
			}
			if (userAdmin.deleteUser((User) userAdmin.getUser("login", login)) == false) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User could not be found");
			}
		}

	}

	private EclipseWebUserAdmin getUserAdmin(String userStoreId) throws UnsupportedUserStoreException {
		return EclipseWebUserAdminRegistry.getDefault().getUserStore(userStoreId);
	}

	private EclipseWebUserAdmin getUserAdmin() {
		return EclipseWebUserAdminRegistry.getDefault().getUserStore();
	}

	private JSONObject formJson(User user) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("login", user.getLogin());
		json.put("name", user.getName());
		Set<String> roles = new HashSet<String>();
		for (Role role : user.getRoles()) {
			roles.add(role.getName());
		}
		json.put("roles", roles);
		return json;
	}

	private void displayCreateUserForm(HttpServletRequest req, HttpServletResponse resp, List<String> errors) throws IOException {

		String versionString = req.getHeader("EclipseWeb-Version");
		Version version = versionString == null ? null : new Version(versionString);

		if (version == null) {
			writeHtmlResponse(req, resp, errors);
		} else {
			if (errors == null || errors.isEmpty()) {
				writeJavaScriptResponse(req, resp);
			} else {
				setErrorList(req, resp, errors);
			}
		}
	}

	private void setErrorList(HttpServletRequest req, HttpServletResponse response, List<String> errors) throws IOException {
		response.setContentType("text/javascript");
		PrintWriter writer = response.getWriter();
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		writer.println("dojo.byId('createUserError').innerHTML='" + getErrorsList(errors) + "';");
		writer.flush();
	}

	private void writeHtmlResponse(HttpServletRequest req, HttpServletResponse response, List<String> errors) throws IOException {
		response.setContentType("html");
		PrintWriter writer = response.getWriter();
		writer.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">");
		writer.println("<html>");
		writer.println("<head>");
		writer.println("<title>Create account</title>");
		if (req.getParameter("styles") == null || "".equals(req.getParameter("styles"))) {
			writer.println("<style type=\"text/css\">");
			writer.print("@import \"");
			writer.print("/usersstatic/css/defaultCreateUserForm.css");
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
		writer.print("<form name=\"AuthForm\" method=post action=\"/users/create");
		if (req.getParameter("redirect") != null && !req.getParameter("redirect").equals("")) {
			writer.print("?redirect=");
			writer.print(req.getParameter("redirect"));
		}
		if (req.getParameter("store") != null && !req.getParameter("store").equals("")) {
			writer.print("&store=");
			writer.print(req.getParameter("store"));
		}
		writer.println("\">");

		writer.println(addErrors(getFileContents("static/createUser.html"), errors));

		writer.println("</form>");
		writer.println("</body>");
		writer.println("</html>");
		writer.flush();

	}

	private void writeJavaScriptResponse(HttpServletRequest req, HttpServletResponse response) throws IOException {
		response.setContentType("text/javascript");
		PrintWriter writer = response.getWriter();
		writer.print("if(!stylf)\n");
		writer.print("var stylf=document.createElement(\"link\");");
		writer.print("stylf.setAttribute(\"rel\", \"stylesheet\");");
		writer.print("stylf.setAttribute(\"type\", \"text/css\");");
		writer.print("stylf.setAttribute(\"href\", \"");
		writer.print(getStyles(req.getParameter("styles")));
		writer.print("\");");
		writer.println("if(!divf)");
		writer.println("var divf = document.createElement('span');");
		writer.print("divf.innerHTML='");
		writer.print(loadJSResponse(req));
		if (req.getParameter("onUserCreated") != null && req.getParameter("onUserCreated").length() > 0) {
			writer.println("userCreatedNotifier=" + req.getParameter("onUserCreated") + ";");
		}
		if (req.getParameter("store") != null && req.getParameter("store").length() > 0) {
			writer.println("userStore='" + req.getParameter("store") + "';");
		}
		writer.flush();
	}

	private String getStyles(String stylesParam) {
		if (stylesParam == null || stylesParam.length() == 0) {
			return "/usersstatic/css/defaultCreateUserForm.css";
		} else {

			return stylesParam.replaceAll("'", "\\\\'").replaceAll("\\t+", " ").replaceAll("\n", "");
		}
	}

	private String loadJSResponse(HttpServletRequest req) throws IOException {

		StringBuilder sb = new StringBuilder();
		appendFileContentAsJsString(sb, "static/createUser.html");
		sb.append("';\n");
		sb.append("var scrf = '");
		appendFileContentAsJsString(sb, "static/js/xhrCreateUser.js");
		sb.append("';\n");
		sb.append(getFileContents("static/js/loadXhrCreateUser.js"));

		return sb.toString();

	}

	private void appendFileContentAsJsString(StringBuilder sb, String filename) throws IOException {
		InputStream is = UserAdminActivator.getDefault().getBundleContext().getBundle().getEntry(filename).openStream();
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

	private String getErrorsList(List<String> errors) {
		if (errors == null || errors.size() < 1) {
			return "";
		}
		StringBuilder errorsList = new StringBuilder("<div id=\"createUserErrorWin\">");
		errorsList.append("<ul class=\"createUserForm loginError\" id=\"createUserErrorsList\">");
		for (String error : errors) {
			errorsList.append("<li>").append(error).append("</li>");
		}
		errorsList.append("</ul>");
		errorsList.append("</div>");
		return errorsList.toString();
	}

	private String addErrors(String divSrc, List<String> errors) {
		return divSrc.replace("<!--ERROR-->", getErrorsList(errors));
	}

	private String getFileContents(String filename) throws IOException {
		StringBuilder sb = new StringBuilder();
		InputStream is = UserAdminActivator.getDefault().getBundleContext().getBundle().getEntry(filename).openStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line = "";
		while ((line = br.readLine()) != null) {
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

}
