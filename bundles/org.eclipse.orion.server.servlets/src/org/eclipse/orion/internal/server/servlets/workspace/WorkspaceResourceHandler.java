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
import org.eclipse.orion.internal.server.core.IAliasRegistry;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;
import org.osgi.service.prefs.BackingStoreException;

/**
 * 
 */
public class WorkspaceResourceHandler extends WebElementResourceHandler<WebWorkspace> {

	private final ServletResourceHandler<IStatus> statusHandler;
	private final IAliasRegistry aliasRegistry;

	public WorkspaceResourceHandler(ServletResourceHandler<IStatus> statusHandler, IAliasRegistry aliasRegistry) {
		this.statusHandler = statusHandler;
		this.aliasRegistry = aliasRegistry;
		initialize();
	}

	private void initialize() {
		for (WebProject project : WebProject.allProjects()) {
			try {
				registerProjectLocation(project);
			} catch (CoreException e) {
				LogHelper.log(e);
			}
		}
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
			}
		} catch (IOException e) {
			String msg = NLS.bind("Error handling request against workspace {0}", workspace.getId());
			statusHandler.handleRequest(request, response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		}
		return false;
	}

	private void computeProjectLocation(WebProject project, String location, String authority, boolean init) throws URISyntaxException, CoreException {
		URI contentURI;
		if (location == null) {
			//auto-generate a project name from the id
			URI platformLocationURI = Activator.getDefault().getRootLocationURI();
			IFileStore child = EFS.getStore(Util.getURIWithAuthority(platformLocationURI, authority)).getChild(project.getId());
			child.mkdir(EFS.NONE, null);
			//store a relative URI
			contentURI = new URI(null, child.getName(), null);
		} else {
			//use the content location specified by the user
			try {
				contentURI = Util.getURIWithAuthority(new URI(location), authority);//new URI(location);
				EFS.getFileSystem(contentURI.getScheme());//check if we support this scheme
			} catch (URISyntaxException e) {
				contentURI = new File(location).toURI(); //if this is not a valid URI try to parse it as file path
			} catch (CoreException e) {
				contentURI = new File(location).toURI();//if we don't support given scheme try to parse as location as a file path
			}
			if (init) {
				IFileStore child = EFS.getStore(contentURI);
				child.mkdir(EFS.NONE, null);
			}

			//TODO ensure the location is somewhere reasonable
		}
		project.setContentLocation(contentURI);
		registerProjectLocation(project);
	}

	private void removeProject(WebProject project, String authority) throws CoreException {
		URI contentURI = project.getContentLocation();

		// don't remove linked projects
		if (project.getId().equals(contentURI.toString())) {
			URI platformLocationURI = Activator.getDefault().getRootLocationURI();
			IFileStore child;
			child = EFS.getStore(Util.getURIWithAuthority(platformLocationURI, authority)).getChild(project.getId());
			if (child.fetchInfo().exists()) {
				child.delete(EFS.NONE, null);
			}
		}

		project.remove();
	}

	private void registerProjectLocation(WebProject project) throws CoreException {
		URI contentURI = project.getContentLocation();
		//if the location is relative to this server, we need to register an alias so the file service can find it
		if (!contentURI.isAbsolute() || "file".equals(contentURI.getScheme()) || "gitfs".equals(contentURI.getScheme())) { //$NON-NLS-1$ //$NON-NLS-2$
			IPath contentPath = new Path(contentURI.getPath());
			if (contentPath.isAbsolute())
				aliasRegistry.registerAlias(project.getId(), contentURI);
		}
	}

	private boolean handleAddOrRemoveProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace) throws IOException, JSONException, ServletException {
		//make sure required fields are set
		JSONObject data = OrionServlet.readJSONRequest(request);
		if (!data.isNull("Remove")) //$NON-NLS-1$
			return handleRemoveProject(request, response, workspace, data);
		return handleAddProject(request, response, workspace, data);
	}

	private boolean handleRemoveProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace, JSONObject data) throws IOException, JSONException, ServletException {
		//make sure required fields are set
		JSONObject toRemove = data;

		// project Id should be the last URL segment
		String[] s = toRemove.getString("ProjectURL").split("/"); //$NON-NLS-1$ //$NON-NLS-2$
		WebProject project = WebProject.fromId(s[s.length - 1]);

		//we need the base location for the workspace servlet. Since this is a POST 
		//on a single workspace we need to strip off the workspace id from the request URI
		URI baseLocation = getURI(request);
		baseLocation = baseLocation.resolve("");
		JSONObject result = WebProjectResourceHandler.toJSON(project, baseLocation);

		// remove user rights for the project
		try {
			AuthorizationService.removeUserRight(request.getRemoteUser(), URI.create(result.getString(ProtocolConstants.KEY_LOCATION)).getPath());
			AuthorizationService.removeUserRight(request.getRemoteUser(), URI.create(result.getString(ProtocolConstants.KEY_LOCATION)).getPath() + "/*");
		} catch (BackingStoreException e) {
			String msg = "Error persisting user rights";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}

		//If all went well, remove project from workspace
		workspace.removeProject(project);

		// remove the project folder
		try {
			removeProject(project, request.getRemoteUser());
		} catch (CoreException e) {
			//we are unable to write in the platform location!
			String msg = NLS.bind("Server content location could not be written: {0}", Activator.getDefault().getRootLocationURI());
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}

		//save the workspace and project metadata
		try {
			project.save();
			workspace.save();
		} catch (CoreException e) {
			String msg = "Error persisting project state";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}

		return true;
	}

	private boolean handleAddProject(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace, JSONObject data) throws IOException, JSONException, ServletException {
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
		if (!isAllowedLinkDestination(content)) {
			String msg = "Cannot link to server path";
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

		//save the workspace and project metadata
		try {
			project.save();
			workspace.save();
		} catch (CoreException e) {
			String msg = "Error persisting project state";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		//serialize the new project in the response

		//we need the base location for the workspace servlet. Since this is a POST 
		//on a single workspace we need to strip off the workspace id from the request URI
		URI baseLocation = getURI(request);
		baseLocation = baseLocation.resolve("");
		JSONObject result = WebProjectResourceHandler.toJSON(project, baseLocation);
		OrionServlet.writeJSONResponse(request, response, result);

		// add user rights for the project
		try {
			AuthorizationService.addUserRight(request.getRemoteUser(), URI.create(result.getString(ProtocolConstants.KEY_LOCATION)).getPath());
			AuthorizationService.addUserRight(request.getRemoteUser(), URI.create(result.getString(ProtocolConstants.KEY_LOCATION)).getPath() + "/*");
		} catch (CoreException e) {
			statusHandler.handleRequest(request, response, e.getStatus());
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

	private boolean getInit(JSONObject toAdd) {
		return Boolean.valueOf(toAdd.optBoolean(ProtocolConstants.KEY_CREATE_IF_DOESNT_EXIST));
	}

	private boolean isAllowedLinkDestination(String content) {
		if (content == null) {
			return true;
		}

		try {
			// TODO gitfs links are allowed
			if ("gitfs".equals(new URI(content).getScheme())) //$NON-NLS-1$
				return true;
		} catch (URISyntaxException e) {
			// ignore
		}

		String prefixes = System.getProperty("org.eclipse.orion.server.core.allowedPathPrefixes"); //$NON-NLS-1$
		if (prefixes == null) {
			prefixes = ServletTestingSupport.allowedPrefixes;
			if (prefixes == null)
				return false;
		}
		StringTokenizer t = new StringTokenizer(prefixes, ","); //$NON-NLS-1$
		while (t.hasMoreTokens()) {
			String prefix = t.nextToken();
			if (content.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private boolean handlePutWorkspaceMetadata(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace) {
		return false;
	}

	private boolean handleGetWorkspaceMetadata(HttpServletRequest request, HttpServletResponse response, WebWorkspace workspace) throws IOException {
		//we need the base location for the workspace servlet. Since this is a GET 
		//on a single workspace we need to strip off the workspace id from the request URI
		URI baseLocation = getURI(request);
		baseLocation = baseLocation.resolve("");
		OrionServlet.writeJSONResponse(request, response, toJSON(workspace, baseLocation));
		return true;

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
		URI projectBaseLocation = URIUtil.append(baseLocation, "projects"); //$NON-NLS-1$
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
			URI workspaceLocation = URIUtil.append(baseLocation, workspace.getId());
			result.put(ProtocolConstants.KEY_LOCATION, workspaceLocation.toString());
			result.put(ProtocolConstants.KEY_CHILDREN_LOCATION, workspaceLocation.toString());
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
				String contentLocation = computeProjectContentLocation(baseLocation, project);
				child.put(ProtocolConstants.KEY_LOCATION, contentLocation);
				child.put(ProtocolConstants.KEY_CHILDREN_LOCATION, contentLocation + "?depth=1"); //$NON-NLS-1$
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

	/**
	 * Returns the location of the project's content (conforming to File REST API).
	 */
	static String computeProjectContentLocation(URI parentLocation, WebProject project) {
		URI contentLocation = project.getContentLocation();
		//relative URIs (any URI with no scheme) are resolved against the location of the workspace servlet.
		//note when relative URIs are used we must hard-code knowledge of the file servlet
		if (!contentLocation.isAbsolute() || "file".equals(contentLocation.getScheme()) || "gitfs".equals(contentLocation.getScheme())) { //$NON-NLS-1$//$NON-NLS-2$
			IPath contentPath = new Path(contentLocation.getPath());
			//absolute file system paths are mapped via the alias registry so we just provide the project id as the alias
			if (contentPath.isAbsolute())
				contentPath = new Path(project.getId());
			contentLocation = URIUtil.append(parentLocation, ".." + Activator.LOCATION_FILE_SERVLET + contentPath.makeAbsolute().toString()); //$NON-NLS-1$
		}
		String locationString = contentLocation.toString();
		//projects are directories
		if (!locationString.endsWith("/")) //$NON-NLS-1$
			locationString += "/"; //$NON-NLS-1$
		return locationString;
	}
}