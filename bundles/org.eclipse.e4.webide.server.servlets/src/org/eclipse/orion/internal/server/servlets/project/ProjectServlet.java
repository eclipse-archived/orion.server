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
package org.eclipse.orion.internal.server.servlets.project;

import org.eclipse.orion.server.servlets.EclipseWebServlet;

import org.eclipse.orion.internal.server.servlets.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.StringTokenizer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servlet for creating, deleting, and accessing projects.
 */
public class ProjectServlet extends EclipseWebServlet {

	/**
	 * Version number of java serialization.
	 */
	private static final long serialVersionUID = 1L;

	private ServletResourceHandler<WebProject> projectResourceHandler;

	public ProjectServlet() {
		projectResourceHandler = new WebProjectResourceHandler(getStatusHandler());
	}

	/**
	 * Verify that the user name is valid. Returns <code>true</code> if the
	 * name is valid and false otherwise. If invalid, this method will handle
	 * filling in the servlet response.
	 */
	private boolean checkUser(String user, HttpServletResponse response) throws ServletException {
		if (user == null) {
			handleException(response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "User name not specified"), HttpServletResponse.SC_FORBIDDEN);
			return false;
		}
		return true;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathString = req.getPathInfo();
		if (pathString == null || pathString.equals("/")) { //$NON-NLS-1$
			doGetProjects(req, resp);
			return;
		}
		IPath path = new Path(pathString);
		if (path.segmentCount() == 1) {
			String projectId = path.segment(0);
			if (!WebProject.exists(projectId)) {
				String msg = NLS.bind("Project does not exist: {0}", projectId);
				getStatusHandler().handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
				return;
			}
			WebProject project = WebProject.fromId(projectId);
			if (projectResourceHandler.handleRequest(req, resp, project))
				return;
		}
		super.doGet(req, resp);
	}

	/**
	 * Gets the list of all projects for this request's user.
	 * @return <code>true</code> if the request has handled (successful or otherwise),
	 * or <code>false</code> if the request could not be handled by this servlet.
	 */
	private boolean doGetProjects(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String userName = getUserName(req);
		if (!checkUser(userName, resp))
			return true;
		try {
			WebUser user = WebUser.fromUserName(userName);
			writeJSONResponse(req, resp, WebUserResourceHandler.toJSON(user, ServletResourceHandler.getURI(req)));
		} catch (Exception e) {
			handleException(resp, "An error occurred while obtaining workspace data", e);
		}
		return true;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathString = req.getPathInfo();
		if (pathString == null || pathString.equals("/")) {//$NON-NLS-1$
			doCreateProject(req, resp);
			return;
		}
		IPath path = new Path(pathString);
		if (path.segmentCount() == 1) {
			WebProject workspace = WebProject.fromId(path.segment(0));
			if (projectResourceHandler.handleRequest(req, resp, workspace))
				return;
		}
		super.doPost(req, resp);
	}

	private boolean handleAddProject(HttpServletRequest request, HttpServletResponse response, WebUser user, JSONObject data) throws IOException, JSONException, ServletException {
		//make sure required fields are set
		JSONObject toAdd = data;
		String id = toAdd.optString(ProtocolConstants.KEY_ID, null);
		if (id == null)
			id = WebProject.nextProjectId();
		boolean projectExists = WebProject.exists(id);
		WebProject project = WebProject.fromId(id);
		String content = toAdd.optString(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		if (!isAllowedLinkDestination(content)) {
			String msg = "Cannot link to server path";
			return getStatusHandler().handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, msg, null));
		}
		try {
			computeProjectLocation(project, content, request.getRemoteUser());
		} catch (CoreException e) {
			//we are unable to write in the platform location!
			String msg = NLS.bind("Server content location could not be written: {0}", Activator.getDefault().getRootLocationURI());
			return getStatusHandler().handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} catch (URISyntaxException e) {
			String msg = NLS.bind("Invalid project location: {0}", content);
			return getStatusHandler().handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, e));
		}
		//If all went well, add project to workspace
		user.addProject(project);

		//save the workspace and project metadata
		try {
			project.save();
			user.save();
		} catch (CoreException e) {
			String msg = "Error persisting project state";
			return getStatusHandler().handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		//serialize the new project in the response
		JSONObject result = WebProjectResourceHandler.toJSON(project, ServletResourceHandler.getURI(request));
		EclipseWebServlet.writeJSONResponse(request, response, result);
		response.setStatus(projectExists ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
		return true;
	}

	private void computeProjectLocation(WebProject project, String location, String authority) throws URISyntaxException, CoreException {
		URI contentURI;
		if (location == null) {
			//content location will simply be the project id
			URI platformLocationURI = Activator.getDefault().getRootLocationURI();
			IFileStore rootStore = EFS.getStore(Util.getURIWithAuthority(platformLocationURI, authority));
			IFileStore child = rootStore.getChild(project.getId());
			child.mkdir(EFS.NONE, null);
			//store a relative URI
			contentURI = new URI(null, child.getName(), null);
		} else {
			//use the content location specified by the user
			try {
				contentURI = Util.getURIWithAuthority(new URI(location), authority);//new URI(location);
			} catch (URISyntaxException e) {
				contentURI = new File(location).toURI();
			}

			//TODO ensure the location is somewhere reasonable
		}
		project.setContentLocation(contentURI);
	}

	private boolean isAllowedLinkDestination(String content) {
		if (content == null) {
			return true;
		}

		try {
			// TODO gitfs links are allowed
			if ("gitfs".equals(new URI(content).getScheme()))
				return true;
		} catch (URISyntaxException e) {
			// ignore
		}

		String prefixes = System.getProperty("org.eclipse.orion.server.core.allowedPathPrefixes");
		if (prefixes == null) {
			prefixes = ServletTestingSupport.allowedPrefixes;
			if (prefixes == null)
				return false;
		}
		StringTokenizer t = new StringTokenizer(prefixes, ",");
		while (t.hasMoreTokens()) {
			String prefix = t.nextToken();
			if (content.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private void doCreateProject(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String userName = getUserName(request);
		if (!checkUser(userName, response))
			return;
		try {
			WebUser user = WebUser.fromUserName(userName);
			JSONObject data = readJSONRequest(request);
			handleAddProject(request, response, user, data);
		} catch (JSONException e) {
			handleException(response, "Project creation request is malformed", e, HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathString = req.getPathInfo();
		if (pathString != null) {
			IPath path = new Path(pathString);
			if (path.segmentCount() == 1) {
				WebProject project = WebProject.fromId(path.segment(0));
				if (projectResourceHandler.handleRequest(req, resp, project))
					return;
			}
		}
		super.doPut(req, resp);
	}

	/**
	 * Obtain and return the user name from the request headers.
	 */
	private String getUserName(HttpServletRequest req) {
		return req.getRemoteUser();
	}

}
