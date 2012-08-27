/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONObject;

/**
 * Servlet for accessing workspace metadata.
 */
public class WorkspaceServlet extends OrionServlet {
	/**
	 * Version number of java serialization.
	 */
	private static final long serialVersionUID = 1L;

	private ServletResourceHandler<WebWorkspace> workspaceResourceHandler;
	private ServletResourceHandler<WebProject> projectResourceHandler;

	public WorkspaceServlet() {
		workspaceResourceHandler = new WorkspaceResourceHandler(getStatusHandler());
		projectResourceHandler = new WebProjectResourceHandler();
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
	protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathString = req.getPathInfo();
		if (pathString == null || pathString.equals("/")) { //$NON-NLS-1$
			doGetWorkspaces(req, resp);
			return;
		}
		IPath path = new Path(pathString);
		if (path.segmentCount() == 1) {
			WebWorkspace workspace = WebWorkspace.fromId(path.segment(0));
			if (workspaceResourceHandler.handleRequest(req, resp, workspace))
				return;
		} else if (path.segmentCount() == 3) {
			//path format is /<wsId>/project/<projectId>
			WebProject project = WebProject.fromId(path.segment(2));
			if (projectResourceHandler.handleRequest(req, resp, project))
				return;
		}
		super.doGet(req, resp);
	}

	@Override
	protected synchronized void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathString = req.getPathInfo();
		IPath path = new Path(pathString == null ? "" : pathString); //$NON-NLS-1$
		if (path.segmentCount() > 0) {
			WebWorkspace workspace = WebWorkspace.fromId(path.segment(0));
			if (workspaceResourceHandler.handleRequest(req, resp, workspace))
				return;
		}
		super.doDelete(req, resp);
	}

	/**
	 * Gets the list of all workspaces for this request's user.
	 * @return <code>true</code> if the request has handled (successful or otherwise),
	 * or <code>false</code> if the request could not be handled by this servlet.
	 */
	private boolean doGetWorkspaces(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
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
	protected synchronized void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathString = req.getPathInfo();
		if (pathString == null || pathString.equals("/")) {//$NON-NLS-1$
			doCreateWorkspace(req, resp);
			return;
		}
		IPath path = new Path(pathString);
		if (path.segmentCount() == 1) {
			WebWorkspace workspace = WebWorkspace.fromId(path.segment(0));
			if (workspaceResourceHandler.handleRequest(req, resp, workspace))
				return;
		}
		super.doPost(req, resp);
	}

	private void doCreateWorkspace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = getUserName(req);
		if (!checkUser(userName, resp))
			return;
		String workspaceName = req.getHeader(ProtocolConstants.HEADER_SLUG);
		if (workspaceName == null) {
			handleException(resp, "Workspace name not specified", null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		try {
			WebUser user = WebUser.fromUserName(userName);
			WebWorkspace workspace = user.createWorkspace(workspaceName);
			JSONObject result = WorkspaceResourceHandler.toJSON(workspace, ServletResourceHandler.getURI(req));
			writeJSONResponse(req, resp, result);
			String resultLocation = result.optString(ProtocolConstants.KEY_LOCATION);
			resp.setHeader(ProtocolConstants.KEY_LOCATION, resultLocation);

			// add user rights for the workspace
			AuthorizationService.addUserRight(req.getRemoteUser(), URI.create(resultLocation).getPath());
			AuthorizationService.addUserRight(req.getRemoteUser(), URI.create(resultLocation).getPath() + "/*"); //$NON-NLS-1$
			// add user rights for file servlet location
			String filePath = Activator.LOCATION_FILE_SERVLET + '/' + workspace.getId();
			AuthorizationService.addUserRight(req.getRemoteUser(), filePath);
			AuthorizationService.addUserRight(req.getRemoteUser(), filePath + "/*"); //$NON-NLS-1$
		} catch (CoreException e) {
			handleException(resp, e.getStatus());
			return;
		}
	}

	@Override
	protected synchronized void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathString = req.getPathInfo();
		if (pathString != null) {
			IPath path = new Path(pathString);
			if (path.segmentCount() == 1) {
				WebWorkspace workspace = WebWorkspace.fromId(path.segment(0));
				if (workspaceResourceHandler.handleRequest(req, resp, workspace))
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
