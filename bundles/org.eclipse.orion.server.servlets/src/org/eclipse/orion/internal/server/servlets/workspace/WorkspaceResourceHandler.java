/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

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
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * Handles requests against a single workspace.
 */
public class WorkspaceResourceHandler extends WebElementResourceHandler<WebWorkspace> {
	static final int CREATE_COPY = 0x1;
	static final int CREATE_MOVE = 0x2;
	static final int CREATE_NO_OVERWRITE = 0x4;

	private final ServletResourceHandler<IStatus> statusHandler;

	/**
	 * Returns the location of the project's content (conforming to File REST API).
	 */
	static URI computeProjectURI(URI parentLocation, WebWorkspace workspace, WebProject project) {
		return URIUtil.append(parentLocation, ".." + Activator.LOCATION_FILE_SERVLET + '/' + workspace.getId() + '/' + project.getName() + '/'); //$NON-NLS-1$
	}

	/**
	 * Returns a JSON representation of the workspace, conforming to EclipseWeb
	 * workspace API protocol.
	 * @param workspace The workspace to store
	 * @param baseLocation The base location for the workspace servlet
	 */
	public static JSONObject toJSON(WebWorkspace workspace, URI baseLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(workspace);
		JSONArray projects = workspace.getProjectsJSON();
		URI workspaceLocation = URIUtil.append(baseLocation, workspace.getId());
		URI projectBaseLocation = URIUtil.append(workspaceLocation, "project"); //$NON-NLS-1$
		if (projects == null)
			projects = new JSONArray();
		//augment project objects with their location
		for (int i = 0; i < projects.length(); i++) {
			try {
				JSONObject project = (JSONObject) projects.get(i);
				//this is the location of the project metadata
				project.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(projectBaseLocation, project.getString(ProtocolConstants.KEY_ID)));
			} catch (JSONException e) {
				//ignore malformed children
			}
		}

		//add basic fields to workspace result
		try {
			result.put(ProtocolConstants.KEY_LOCATION, workspaceLocation);
			result.put(ProtocolConstants.KEY_CHILDREN_LOCATION, workspaceLocation);
			result.put(ProtocolConstants.KEY_PROJECTS, projects);
			result.put(ProtocolConstants.KEY_DIRECTORY, "true"); //$NON-NLS-1$
		} catch (JSONException e) {
			//can't happen because key and value are well-formed
		}
		//add children element to conform to file API structure
		JSONArray children = new JSONArray();
		for (int i = 0; i < projects.length(); i++) {
			try {
				WebProject project = WebProject.fromId(projects.getJSONObject(i).getString(ProtocolConstants.KEY_ID));
				JSONObject child = new JSONObject();
				child.put(ProtocolConstants.KEY_NAME, project.getName());
				child.put(ProtocolConstants.KEY_DIRECTORY, true);
				//this is the location of the project file contents
				URI contentLocation = computeProjectURI(baseLocation, workspace, project);
				child.put(ProtocolConstants.KEY_LOCATION, contentLocation);
				try {
					child.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, project.getProjectStore().fetchInfo().getLastModified());
				} catch (CoreException coreException) {
					//just omit the timestamp in this case because the project location is unreachable
				}
				try {
					child.put(ProtocolConstants.KEY_CHILDREN_LOCATION, new URI(contentLocation.getScheme(), contentLocation.getUserInfo(), contentLocation.getHost(), contentLocation.getPort(), contentLocation.getPath(), ProtocolConstants.PARM_DEPTH + "=1", contentLocation.getFragment())); //$NON-NLS-1$
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
				child.put(ProtocolConstants.KEY_ID, project.getId());
				children.put(child);
			} catch (JSONException e) {
				//ignore malformed children
			}
		}
		try {
			result.put(ProtocolConstants.KEY_CHILDREN, children);
		} catch (JSONException e) {
			//cannot happen
		}

		return result;
	}

	public WorkspaceResourceHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	/**
	 * Adds the right for the user of the current request to access/modify the given location.
	 * @throws CoreException 
	 */
	private static void addProjectRights(String user, WebProject project) throws CoreException {
		String location = Activator.LOCATION_FILE_SERVLET + '/' + project.getId();
		//right to access the location
		AuthorizationService.addUserRight(user, location);
		//right to access all children of the location
		AuthorizationService.addUserRight(user, location + "/*"); //$NON-NLS-1$
	}

	/**
	 * Removes the right for the user of the current request to access/modify the given location.
	 * @throws CoreException 
	 */
	private static void removeProjectRights(String user, WebProject project) throws CoreException {
		String location = Activator.LOCATION_FILE_SERVLET + '/' + project.getId();
		//right to access the location
		AuthorizationService.removeUserRight(user, location);
		//right to access all children of the location
		AuthorizationService.removeUserRight(user, location + "/*"); //$NON-NLS-1$
	}

	public static void computeProjectLocation(WebProject project, String location, String user, boolean init) throws URISyntaxException, CoreException {
		URI contentURI;
		if (location == null) {
			contentURI = generateProjectLocation(project, user);
		} else {
			//use the content location specified by the user
			try {
				contentURI = new URI(location);
				EFS.getFileSystem(contentURI.getScheme());//check if we support this scheme
			} catch (Exception e) {
				//if this is not a valid URI or scheme try to parse it as file path
				contentURI = new File(location).toURI();
			}
			if (init) {
				IFileStore child = EFS.getStore(contentURI);
				child.mkdir(EFS.NONE, null);
			}
		}
		project.setContentLocation(contentURI);
	}

	/**
	 * Generates a file system location for newly created project
	 */
	private static URI generateProjectLocation(WebProject project, String user) throws CoreException, URISyntaxException {
		URI platformLocationURI = Activator.getDefault().getRootLocationURI();
		IFileStore root = EFS.getStore(platformLocationURI);

		//consult layout preference
		String layout = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_LAYOUT, "flat").toLowerCase(); //$NON-NLS-1$

		IFileStore projectStore;
		URI location;
		if ("usertree".equals(layout) && user != null) { //$NON-NLS-1$
			//the user-tree layout organises projects by the user who created it
			String userPrefix = user.substring(0, Math.min(2, user.length()));
			projectStore = root.getChild(userPrefix).getChild(user).getChild(project.getId());
			location = projectStore.toURI();
		} else {
			//default layout is a flat list of projects at the root
			projectStore = root.getChild(project.getId());
			location = new URI(null, projectStore.getName(), null);
		}
		projectStore.mkdir(EFS.NONE, null);
		return location;
	}

	/**
	 * Returns a bit-mask of create options as specified by the request.
	 */
	private int getCreateOptions(HttpServletRequest request) {
		int result = 0;
		String optionString = request.getHeader(ProtocolConstants.HEADER_CREATE_OPTIONS);
		if (optionString != null) {
			for (String option : optionString.split(",")) { //$NON-NLS-1$
				if (ProtocolConstants.OPTION_COPY.equalsIgnoreCase(option))
					result |= CREATE_COPY;
				else if (ProtocolConstants.OPTION_MOVE.equalsIgnoreCase(option))
					result |= CREATE_MOVE;
				else if (ProtocolConstants.OPTION_NO_OVERWRITE.equalsIgnoreCase(option))
					result |= CREATE_NO_OVERWRITE;
			}
		}
		return result;
	}

	private boolean getInit(JSONObject toAdd) {
		return Boolean.valueOf(toAdd.optBoolean(ProtocolConstants.KEY_CREATE_IF_DOESNT_EXIST));
	}

	private boolean handleAddOrRemoveProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace) throws IOException, JSONException, ServletException {
		//make sure required fields are set
		JSONObject data = OrionServlet.readJSONRequest(request);
		if (!data.isNull("Remove")) //$NON-NLS-1$
			return handleRemoveProject(request, response, workspace);
		int options = getCreateOptions(request);
		if ((options & (CREATE_COPY | CREATE_MOVE)) != 0)
			return handleCopyMoveProject(request, response, workspace, data);
		return handleAddProject(request, response, workspace, data);
	}

	private boolean handleAddProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace, JSONObject data) throws IOException, ServletException {
		//make sure required fields are set
		JSONObject toAdd = data;
		String id = toAdd.optString(ProtocolConstants.KEY_ID, null);
		if (id == null)
			id = WebProject.nextProjectId();
		WebProject project = WebProject.fromId(id);
		String name = toAdd.optString(ProtocolConstants.KEY_NAME, null);
		if (name == null)
			name = request.getHeader(ProtocolConstants.HEADER_SLUG);
		if (!validateProjectName(name, request, response))
			return true;
		project.setName(name);
		String content = toAdd.optString(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		if (!isAllowedLinkDestination(content, request.getRemoteUser())) {
			String msg = NLS.bind("Cannot link to server path {0}. Use the orion.file.allowedPaths property to specify server locations where content can be linked.", content);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, msg, null));
		}
		try {
			computeProjectLocation(project, content, request.getRemoteUser(), getInit(toAdd));
		} catch (CoreException e) {
			//we are unable to write in the platform location!
			String msg = NLS.bind("Server content location could not be written: {0}", Activator.getDefault().getRootLocationURI());
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} catch (URISyntaxException e) {
			String msg = NLS.bind("Invalid project location: {0}", content);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, e));
		}
		//If all went well, add project to workspace
		workspace.addProject(project);

		try {
			// give the project creator access rights to the project
			addProjectRights(request.getRemoteUser(), project);
			//save the workspace and project metadata
			project.save();
			workspace.save();
		} catch (CoreException e) {
			String msg = "Error persisting project state";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		//serialize the new project in the response

		//the baseLocation should be the workspace location
		URI baseLocation = getURI(request);
		JSONObject result = WebProjectResourceHandler.toJSON(workspace, project, baseLocation);
		OrionServlet.writeJSONResponse(request, response, result);

		//add project location to response header
		response.setHeader(ProtocolConstants.HEADER_LOCATION, result.optString(ProtocolConstants.KEY_LOCATION));
		response.setStatus(HttpServletResponse.SC_CREATED);

		return true;
	}

	/**
	 * Handle a project copy or move request. Returns <code>true</code> if the request
	 * was handled (either success or failure). Returns <code>false</code> if this method
	 * does not know how to handle the request.
	 */
	private boolean handleCopyMoveProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace, JSONObject data) throws ServletException, IOException {
		String sourceLocation = data.optString(ProtocolConstants.HEADER_LOCATION);
		String sourceId = projectForLocation(request, response, sourceLocation);
		//null result means there was an error and we already handled it
		if (sourceId == null)
			return true;
		boolean sourceExists = WebProject.exists(sourceId);
		if (!sourceExists) {
			handleError(request, response, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Source does not exist: {0}", sourceId));
			return true;
		}
		int options = getCreateOptions(request);
		if (!validateOptions(request, response, options))
			return true;

		//get the slug first
		String destinationName = request.getHeader(ProtocolConstants.HEADER_SLUG);
		//If the data has a name then it must be used due to UTF-8 issues with names Bug 376671
		try {
			if (data != null && data.has("Name")) {
				destinationName = data.getString("Name");
			}
		} catch (JSONException e) {
		}

		if (!validateProjectName(destinationName, request, response))
			return true;
		WebProject sourceProject = WebProject.fromId(sourceId);
		if ((options & CREATE_MOVE) != 0) {
			return handleMoveProject(request, response, workspace, sourceProject, sourceLocation, destinationName);
		} else if ((options & CREATE_COPY) != 0) {
			return handleCopyProject(request, response, workspace, sourceProject, destinationName);
		}
		//if we got here, it isn't a copy or a move, so we don't know how to handle the request
		return false;
	}

	/**
	 * Implementation of project copy
	 * Returns <code>false</code> if this method doesn't know how to intepret the request.
	 */
	private boolean handleCopyProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace, WebProject sourceProject, String destinationName) throws IOException, ServletException {
		//first create the destination project
		String destinationId = WebProject.nextProjectId();
		JSONObject projectInfo = new JSONObject();
		try {
			projectInfo.put(ProtocolConstants.KEY_ID, destinationId);
			projectInfo.put(ProtocolConstants.KEY_NAME, destinationName);
		} catch (JSONException e) {
			//should never happen
			throw new RuntimeException(e);
		}
		handleAddProject(request, response, workspace, projectInfo);
		//copy the project data from source
		WebProject destinationProject = WebProject.fromId(destinationId);
		String sourceName = sourceProject.getName();
		try {
			copyProjectContents(sourceProject, destinationProject);
		} catch (CoreException e) {
			handleError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, NLS.bind("Error copying project {0} to {1}", sourceName, destinationName));
			return true;
		}
		URI baseLocation = getURI(request);
		JSONObject result = WebProjectResourceHandler.toJSON(workspace, destinationProject, baseLocation);
		OrionServlet.writeJSONResponse(request, response, result);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, result.optString(ProtocolConstants.KEY_LOCATION, "")); //$NON-NLS-1$
		response.setStatus(HttpServletResponse.SC_CREATED);
		return true;
	}

	/**
	 * Implementation of project move. Returns whether the move requested was handled.
	 * Returns <code>false</code> if this method doesn't know how to interpret the request.
	 */
	private boolean handleMoveProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace, WebProject sourceProject, String sourceLocation, String destinationName) throws ServletException, IOException {
		String sourceName = sourceProject.getName();
		//a project move is simply a rename
		sourceProject.setName(destinationName);
		try {
			sourceProject.save();
		} catch (CoreException e) {
			String msg = NLS.bind("Error persisting project state: {0}", sourceName);
			return handleError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
		//location doesn't change on move project
		URI baseLocation = getURI(request);
		JSONObject result = WebProjectResourceHandler.toJSON(workspace, sourceProject, baseLocation);
		OrionServlet.writeJSONResponse(request, response, result);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, sourceLocation);
		response.setStatus(HttpServletResponse.SC_OK);
		return true;
	}

	/**
	 * Copies the content of one project to the location of a second project. 
	 */
	private void copyProjectContents(WebProject sourceProject, WebProject destinationProject) throws CoreException {
		sourceProject.getProjectStore().copy(destinationProject.getProjectStore(), EFS.OVERWRITE, null);
	}

	private boolean handleError(HttpServletRequest request, HttpServletResponse response, int httpCode, String message) throws ServletException {
		return handleError(request, response, httpCode, message, null);
	}

	private boolean handleError(HttpServletRequest request, HttpServletResponse response, int httpCode, String message, Throwable cause) throws ServletException {
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, httpCode, message, cause));
	}

	private boolean handleGetWorkspaceMetadata(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace) throws IOException {
		//we need the base location for the workspace servlet. Since this is a GET 
		//on a single workspace we need to strip off the workspace id from the request URI
		URI baseLocation = getURI(request);
		baseLocation = baseLocation.resolve(""); //$NON-NLS-1$
		OrionServlet.writeJSONResponse(request, response, toJSON(workspace, baseLocation));
		return true;

	}

	private boolean handlePutWorkspaceMetadata(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace) {
		return false;
	}

	private boolean handleRemoveProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace) throws IOException, JSONException, ServletException {
		IPath path = new Path(request.getPathInfo());
		//format is /workspaceId/project/<projectId>
		if (path.segmentCount() != 3)
			return false;
		String projectId = path.segment(2);
		if (!WebProject.exists(projectId)) {
			//nothing to do
			return true;
		}
		WebProject project = WebProject.fromId(projectId);

		try {
			removeProject(request.getRemoteUser(), workspace, project);
		} catch (CoreException e) {
			ServerStatus error = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error removing project", e);
			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}

		return true;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace) throws ServletException {
		if (workspace == null)
			return statusHandler.handleRequest(request, response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Workspace not specified"));
		//we could split and handle different API versions here if needed
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGetWorkspaceMetadata(request, response, workspace);
				case PUT :
					return handlePutWorkspaceMetadata(request, response, workspace);
				case POST :
					return handleAddOrRemoveProject(request, response, workspace);
				case DELETE :
					//TBD could also handle deleting the workspace itself
					return handleRemoveProject(request, response, workspace);
			}
		} catch (IOException e) {
			String msg = NLS.bind("Error handling request against workspace {0}", workspace.getId());
			statusHandler.handleRequest(request, response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		}
		return false;
	}

	private boolean isAllowedLinkDestination(String content, String user) {
		if (content == null) {
			return true;
		}

		String prefixes = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_ALLOWED_PATHS);
		if (prefixes == null)
			prefixes = ServletTestingSupport.allowedPrefixes;
		if (prefixes != null) {
			StringTokenizer t = new StringTokenizer(prefixes, ","); //$NON-NLS-1$
			while (t.hasMoreTokens()) {
				String prefix = t.nextToken();
				if (content.startsWith(prefix)) {
					return true;
				}
			}
		}

		String userArea = System.getProperty(Activator.PROP_USER_AREA);
		if (userArea == null)
			return false;

		IPath path = new Path(userArea).append(user);

		URI contentURI = null;
		//use the content location specified by the user
		try {
			URI candidate = new URI(content);
			//check if we support this scheme
			String scheme = candidate.getScheme();
			if (scheme != null && EFS.getFileSystem(scheme) != null)
				contentURI = candidate;
		} catch (URISyntaxException e) {
			//if this is not a valid URI try to parse it as file path below
		} catch (CoreException e) {
			//if we don't support given scheme try to parse as location as a file path below
		}
		if (contentURI == null)
			contentURI = new File(content).toURI();
		if (contentURI.toString().startsWith(path.toFile().toURI().toString()))
			return true;
		return false;
	}

	/**
	 * Returns the project id for the given project location. If the project id cannot
	 * be determined, this method will handle setting an appropriate HTTP response
	 * and return <code>null</code>.
	 */
	private String projectForLocation(HttpServletRequest request, HttpServletResponse response, String sourceLocation) throws ServletException {
		try {
			if (sourceLocation != null) {
				URI sourceURI = new URI(sourceLocation);
				String path = sourceURI.getPath();
				if (path != null) {
					String id = new Path(path).lastSegment();
					if (id != null)
						return id;
				}
			}
		} catch (URISyntaxException e) {
			//fall through and fail below
		}
		handleError(request, response, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid source location for copy/move request: {0}", sourceLocation));
		return null;
	}

	public static void addProject(String user, WebWorkspace workspace, WebProject project) throws CoreException {
		//add project to workspace
		workspace.addProject(project);
		// give the project creator access rights to the project
		addProjectRights(user, project);
		//save the workspace and project metadata
		project.save();
		workspace.save();
	}

	public static void removeProject(String user, WebWorkspace workspace, WebProject project) throws CoreException {
		// remove the project folder
		URI contentURI = project.getContentLocation();

		// don't remove linked projects
		if (project.getId().equals(contentURI.toString())) {
			URI platformLocationURI = Activator.getDefault().getRootLocationURI();
			IFileStore child;
			child = EFS.getStore(platformLocationURI).getChild(project.getId());
			if (child.fetchInfo().exists()) {
				child.delete(EFS.NONE, null);
			}
		}

		// remove user rights for the project
		removeProjectRights(user, project);

		//If all went well, remove project from workspace
		workspace.removeProject(project);

		//remove project metadata
		project.remove();

		//save the workspace and project metadata
		project.save();
		workspace.save();

	}

	/**
	 * Asserts that request options are valid. If options are not valid then this method handles the request response and return false. If the options
	 * are valid this method return true.
	 */
	private boolean validateOptions(HttpServletRequest request, HttpServletResponse response, int options) throws ServletException {
		//operation cannot be both copy and move
		int copyMove = CREATE_COPY | CREATE_MOVE;
		if ((options & copyMove) == copyMove) {
			handleError(request, response, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request");
			return false;
		}
		return true;
	}

	/**
	 * Validates that the provided project name is valid. Returns <code>true</code> if the
	 * project name is valid, and <code>false</code> otherwise. This method takes care of
	 * setting the error response when the project name is not valid.
	 */
	private boolean validateProjectName(String name, HttpServletRequest request, HttpServletResponse response) throws ServletException {
		if (name == null || name.trim().length() == 0) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Project name cannot be empty", null));
			return false;
		}
		if (name.contains("/")) { //$NON-NLS-1$
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid project name: {0}", name), null));
			return false;
		}
		return true;
	}
}
