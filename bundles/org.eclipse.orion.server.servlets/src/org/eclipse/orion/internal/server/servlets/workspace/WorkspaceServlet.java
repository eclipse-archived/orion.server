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

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IAliasRegistry;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONObject;

/**
 * Servlet for accessing workspace metadata.
 */
public class WorkspaceServlet extends OrionServlet {

	//sample project names - should eventually be removed
	private static final String CLIENT_CORE_PROJECT_NAME = "org.eclipse.orion.client.core";
	private static final String EDITOR_PROJECT_NAME = "org.eclipse.orion.client.editor";

	/**
	 * Version number of java serialization.
	 */
	private static final long serialVersionUID = 1L;

	private ServletResourceHandler<WebWorkspace> workspaceResourceHandler;
	private ServletResourceHandler<WebProject> projectResourceHandler;

	private final IAliasRegistry aliasRegistry;

	public WorkspaceServlet() {
		aliasRegistry = Activator.getDefault();
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
		} else if (path.segmentCount() == 2) {
			WebProject project = WebProject.fromId(path.segment(1));
			if (projectResourceHandler.handleRequest(req, resp, project))
				return;
		}
		super.doGet(req, resp);
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
			createSampleContent(workspace, userName);
			JSONObject result = WorkspaceResourceHandler.toJSON(workspace, ServletResourceHandler.getURI(req));
			writeJSONResponse(req, resp, result);
			String resultLocation = result.optString(ProtocolConstants.KEY_LOCATION);
			resp.setHeader(ProtocolConstants.KEY_LOCATION, resultLocation);

			// add user rights for the workspace
			AuthorizationService.addUserRight(req.getRemoteUser(), URI.create(resultLocation).getPath());
			AuthorizationService.addUserRight(req.getRemoteUser(), URI.create(resultLocation).getPath() + "/*");
		} catch (CoreException e) {
			handleException(resp, e.getStatus());
			return;
		}
	}

	/**
	 * This code is only temporary for demo purposes.
	 */
	private void createSampleContent(WebWorkspace workspace, String userName) {
		createSampleProject(workspace, CLIENT_CORE_PROJECT_NAME, userName);
		createSampleProject(workspace, EDITOR_PROJECT_NAME, userName);
	}

	/**
	 * This code is only temporary for demo purposes.
	 */
	private void createSampleProject(WebWorkspace workspace, String projectName, String userName) {
		File location = findSampleProject(projectName);
		if (location == null)
			return;
		try {
			WebProject project = WebProject.fromId(projectName);
			//only create the project once
			if (project.getName() == null) {
				project.setName(projectName);
				project.setContentLocation(location.toURI());
				aliasRegistry.registerAlias(projectName, EFS.getLocalFileSystem().fromLocalFile(location).toURI());
				project.save();
			}
			workspace.addProject(project);
			workspace.save();
			AuthorizationService.addUserRight(userName, "/file/" + projectName);
			AuthorizationService.addUserRight(userName, "/file/" + projectName + "/*");
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Error creating sample project", e)); //$NON-NLS-1$
		}
	}

	/**
	 * Finds the org.eclipse.e4.webide bundle location. Returns <code>null</code>
	 * if it could not be found.
	 */
	private File findSampleProject(final String projectName) {
		String url = Activator.getDefault().getContext().getProperty("osgi.install.area");
		File installArea = null;
		try {
			installArea = URIUtil.toFile(URIUtil.fromString(url));
		} catch (URISyntaxException e) {
			//will just use relative to working directory
		}

		File dir = installArea != null ? new File(installArea, "serverworkspace") : new File("serverworkspace");
		if (!dir.isDirectory())
			return null;
		File[] children = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.equals(projectName) || name.startsWith(projectName + '_');
			}
		});
		if (children.length != 1)
			return null;
		return children[0];
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
