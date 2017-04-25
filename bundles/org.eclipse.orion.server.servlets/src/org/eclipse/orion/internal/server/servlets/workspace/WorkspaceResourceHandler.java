/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others.
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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.file.ServletFileStoreHandler;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles requests against a single workspace.
 */
public class WorkspaceResourceHandler extends MetadataInfoResourceHandler<WorkspaceInfo> {
	static final int CREATE_COPY = 0x1;
	static final int CREATE_MOVE = 0x2;
	static final int CREATE_NO_OVERWRITE = 0x4;

	private final ServletResourceHandler<IStatus> statusHandler;

	public static void computeProjectLocation(HttpServletRequest request, ProjectInfo project, String location, boolean init) throws CoreException {
		String user = request.getRemoteUser();
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
				project.setContentLocation(contentURI);
				IFileStore child = NewFileServlet.getFileStore(request, project);
				child.mkdir(EFS.NONE, null);
			}
		}
		project.setContentLocation(contentURI);
	}

	/**
	 * Returns the location of the project's content (conforming to File REST API).
	 */
	static URI computeProjectURI(URI parentLocation, WorkspaceInfo workspace, ProjectInfo project) {
		return URIUtil.append(parentLocation, ".." + Activator.LOCATION_FILE_SERVLET + '/' + workspace.getUniqueId() + '/' + project.getFullName() + '/'); //$NON-NLS-1$
	}

	/**
	 * Generates a file system location for newly created project. Creates a new
	 * folder in the file system and ensures it is empty.
	 */
	private static URI generateProjectLocation(ProjectInfo project, String user) throws CoreException {
		IFileStore projectStore = OrionConfiguration.getMetaStore().getDefaultContentLocation(project);
		if (projectStore.fetchInfo().exists()) {
			//This folder must be empty initially or we risk showing another user's old private data
			projectStore.delete(EFS.NONE, null);
		}
		projectStore.mkdir(EFS.NONE, null);
		return projectStore.toURI();
	}

	/**
	 * Returns the project for the given project metadata location. The expected format of the
	 * location is a URI whose path is of the form /workspace/workspaceId/project/projectName.
	 * Returns <code>null</code> if there was no such project corresponding to the given location.
	 * @throws CoreException If there was an error reading the project metadata
	 */
	public static ProjectInfo projectForMetadataLocation(IMetaStore store, String sourceLocation) throws CoreException {
		if (sourceLocation == null)
			return null;
		URI sourceURI;
		try {
			sourceURI = new URI(sourceLocation);
		} catch (URISyntaxException e) {
			//bad location
			return null;
		}
		String pathString = sourceURI.getPath();
		if (pathString == null)
			return null;
		IPath path = new Path(pathString);
		if (path.segmentCount() == 3 && path.segment(0).equals("file")) {
			//path format is /file/<workspaceId>/<projectName>
			String workspaceId = path.segment(1);
			String projectName = path.segment(2);
			return store.readProject(workspaceId, projectName);
		}
		//path format is /workspace/<workspaceId>/project/<projectName>
		if (path.segmentCount() < 4)
			return null;
		String workspaceId = path.segment(1);
		String projectName = path.segment(3);
		return store.readProject(workspaceId, projectName);
	}

	public static void removeProject(String user, WorkspaceInfo workspace, ProjectInfo project) throws CoreException {
		// remove the project folder
		URI contentURI = project.getContentLocation();

		// only delete project contents if they are in default location
		IFileStore projectStore = OrionConfiguration.getMetaStore().getDefaultContentLocation(project);
		URI defaultLocation = projectStore.toURI();
		if (URIUtil.sameURI(defaultLocation, contentURI)) {
			projectStore.delete(EFS.NONE, null);
		}

		OrionConfiguration.getMetaStore().deleteProject(workspace.getUniqueId(), project.getFullName());
	}
	
	public static void removeWorkspace(String user, WorkspaceInfo workspace) throws CoreException {
		String workspaceId = workspace.getUniqueId();
		IFileStore workspaceStore = OrionConfiguration.getMetaStore().getWorkspaceContentLocation(workspaceId);
		workspaceStore.delete(EFS.NONE, null);
		OrionConfiguration.getMetaStore().deleteWorkspace(user, workspaceId);
	}

	/**
	 * Returns a JSON representation of the workspace, conforming to Orion
	 * workspace API protocol.
	 * @param workspace The workspace to store
	 * @param requestLocation The location of the current request
	 * @param baseLocation The base location for the workspace servlet
	 */
	public static JSONObject toJSON(WorkspaceInfo workspace, URI requestLocation, URI baseLocation) {
		JSONObject result = MetadataInfoResourceHandler.toJSON(workspace);
		JSONArray projects = new JSONArray();
		URI workspaceLocation = URIUtil.append(baseLocation, workspace.getUniqueId());
		URI projectBaseLocation = URIUtil.append(workspaceLocation, "project"); //$NON-NLS-1$
		//add children element to conform to file API structure
		JSONArray children = new JSONArray();
		IMetaStore metaStore = OrionConfiguration.getMetaStore();
		for (String projectName : workspace.getProjectNames()) {
			try {
				ProjectInfo project = metaStore.readProject(workspace.getUniqueId(), projectName);
				//augment project objects with their location
				JSONObject projectObject = new JSONObject();
				projectObject.put(ProtocolConstants.KEY_ID, project.getUniqueId());
				//this is the location of the project metadata
				projectObject.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(projectBaseLocation, projectName));
				projects.put(projectObject);

				//remote folders are listed separately
				IFileStore projectStore = null;
				try {
					projectStore = project.getProjectStore();
				} catch (CoreException e) {
					//ignore and treat as local
				}
				JSONObject child = new JSONObject();
				child.put(ProtocolConstants.KEY_NAME, project.getFullName());
				child.put(ProtocolConstants.KEY_DIRECTORY, true);
				//this is the location of the project file contents
				URI contentLocation = computeProjectURI(baseLocation, workspace, project);
				child.put(ProtocolConstants.KEY_LOCATION, contentLocation);
				try {
					if (projectStore != null)
						child.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, projectStore.fetchInfo(EFS.NONE, null).getLastModified());
				} catch (CoreException coreException) {
					//just omit the timestamp in this case because the project location is unreachable
				}
				try {
					child.put(ProtocolConstants.KEY_CHILDREN_LOCATION, new URI(contentLocation.getScheme(), contentLocation.getUserInfo(), contentLocation.getHost(), contentLocation.getPort(), contentLocation.getPath(), ProtocolConstants.PARM_DEPTH + "=1", contentLocation.getFragment())); //$NON-NLS-1$
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
				child.put(ProtocolConstants.KEY_ID, project.getUniqueId());
				children.put(child);
			} catch (Exception e) {
				//ignore malformed children
			}
		}
		try {
			//add basic fields to workspace result
			result.put(ProtocolConstants.KEY_LOCATION, workspaceLocation);
			URI contentLocation = new URI(workspaceLocation.getScheme(), workspaceLocation.getUserInfo(), workspaceLocation.getHost(), workspaceLocation.getPort(), Activator.LOCATION_FILE_SERVLET + "/" + workspace.getUniqueId() + "/", null, workspaceLocation.getFragment());
			result.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
			result.put(ProtocolConstants.KEY_CHILDREN_LOCATION, workspaceLocation);
			result.put(ProtocolConstants.KEY_PROJECTS, projects);
			result.put(ProtocolConstants.KEY_DIRECTORY, "true"); //$NON-NLS-1$
			//add children to match file API
			result.put(ProtocolConstants.KEY_CHILDREN, children);
		} catch (Exception e) {
			//cannot happen
		}

		return result;
	}

	public WorkspaceResourceHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
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

	private boolean handleAddOrRemoveProject(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace) throws IOException, JSONException, ServletException {
		//make sure required fields are set
		JSONObject data = OrionServlet.readJSONRequest(request);
		if (!data.isNull("Remove")) //$NON-NLS-1$
			return handleRemoveProject(request, response, workspace);
		int options = getCreateOptions(request);
		if ((options & (CREATE_COPY | CREATE_MOVE)) != 0) {
			return handleCopyMoveProject(request, response, workspace, data);
		}
		handleAddProject(request, response, workspace, data);
		return true;
	}

	private boolean handleCopyMoveProject(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace, JSONObject data) throws ServletException, IOException {
		//resolve the source location to a file system location
		String sourceLocation = data.optString(ProtocolConstants.HEADER_LOCATION);
		IFileStore source = null;
		ProjectInfo sourceProject = null;
		boolean isFile = false;
		try {
			if (sourceLocation != null) {
				//could be either a workspace or file location
				if (sourceLocation.startsWith(request.getContextPath() + Activator.LOCATION_WORKSPACE_SERVLET)) {
					sourceProject = projectForMetadataLocation(getMetaStore(), toOrionLocation(request, sourceLocation));
					if (sourceProject != null)
						source = sourceProject.getProjectStore();
				} else {
					isFile = true;
					//file location - remove servlet name prefix
					sourceProject = projectForMetadataLocation(getMetaStore(), toOrionLocation(request, sourceLocation));
					source = resolveSourceLocation(request, sourceLocation); 
				}
			}
		} catch (Exception e) {
			handleError(request, response, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid source location: {0}", sourceLocation), e);
			return true;
		}
		//null result means we didn't find a matching project
		if (source == null) {
			handleError(request, response, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Source does not exist: {0}", sourceLocation));
			return true;
		}
		int options1 = getCreateOptions(request);
		if (!validateOptions(request, response, options1))
			return true;

		//get the slug first
		String destinationName = request.getHeader(ProtocolConstants.HEADER_SLUG);
		//If the data has a name then it must be used due to UTF-8 issues with names Bug 376671
		try {
			if (data.has(ProtocolConstants.KEY_NAME)) {
				destinationName = data.getString(ProtocolConstants.KEY_NAME);
			}
		} catch (JSONException e) {
			//key is valid so cannot happen
		}

		if (!validateProjectName(workspace, destinationName, request, response))
			return true;

		if ((options1 & CREATE_MOVE) != 0) {
			return handleMoveProject(request, response, workspace, source, sourceProject, sourceLocation, destinationName, isFile);
		} else if ((options1 & CREATE_COPY) != 0) {
			//first create the destination project
			JSONObject projectObject = new JSONObject();
			try {
				projectObject.put(ProtocolConstants.KEY_NAME, destinationName);
			} catch (JSONException e) {
				//should never happen
				throw new RuntimeException(e);
			}

			//copy the project data from source
			ProjectInfo destinationProject = createProject(request, response, workspace, projectObject);
			String sourceName = source.getName();
			try {
				source.copy(destinationProject.getProjectStore(), EFS.OVERWRITE, null);
			} catch (CoreException e) {
				handleError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, NLS.bind("Error copying project {0} to {1}", sourceName, destinationName));
				return true;
			}
			try {
				JSONObject result;
				if (isFile) {
					URI baseLocation = new URI("orion", null, Activator.LOCATION_FILE_SERVLET, null, null);
					baseLocation = URIUtil.append(baseLocation, destinationProject.getWorkspaceId());
					baseLocation = URIUtil.append(baseLocation, destinationProject.getFullName());
					result = ServletFileStoreHandler.toJSON(destinationProject.getProjectStore(), destinationProject.getProjectStore().fetchInfo(), baseLocation);
				} else {
					URI baseLocation = getURI(request);
					result = ProjectInfoResourceHandler.toJSON(workspace, destinationProject, baseLocation);
				}
				OrionServlet.writeJSONResponse(request, response, result);
				response.setHeader(ProtocolConstants.HEADER_LOCATION, result.optString(ProtocolConstants.KEY_LOCATION, "")); //$NON-NLS-1$
				response.setStatus(HttpServletResponse.SC_CREATED);
			} catch (Exception e) {
				String msg = NLS.bind("Error persisting project state: {0}", source.getName());
				return handleError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
			}
			return true;
		}
		//if we got here, it isn't a copy or a move, so we don't know how to handle the request
		return false;
	}

	private ProjectInfo handleAddProject(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace, JSONObject data) throws IOException, ServletException {
		ProjectInfo project = createProject(request, response, workspace, data);
		if (project == null)
			return null;

		//serialize the new project in the response

		//the baseLocation should be the workspace location
		URI baseLocation = getURI(request);
		JSONObject result = ProjectInfoResourceHandler.toJSON(workspace, project, baseLocation);
		OrionServlet.writeJSONResponse(request, response, result);

		//add project location to response header
		response.setHeader(ProtocolConstants.HEADER_LOCATION, result.optString(ProtocolConstants.KEY_LOCATION));
		response.setStatus(HttpServletResponse.SC_CREATED);

		return project;
	}

	/**
	 * Creates a new project and returns the metadata of the created project. Returns <code>null</code>
	 * if there was an error creating the project. In the case of an error this method will handle setting an appropriate response
	 * to the servlet.
	 */
	private ProjectInfo createProject(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace, JSONObject data) throws ServletException {
		JSONObject toAdd = data;
		String id = toAdd.optString(ProtocolConstants.KEY_ID, null);
		String name = toAdd.optString(ProtocolConstants.KEY_NAME, null);
		//make sure required fields are set
		if (name == null)
			name = request.getHeader(ProtocolConstants.HEADER_SLUG);
		if (!validateProjectName(workspace, name, request, response))
			return null;
		ProjectInfo project = new ProjectInfo();
		if (id != null)
			project.setUniqueId(id);
		project.setFullName(name);
		project.setWorkspaceId(workspace.getUniqueId());
		String content = toAdd.optString(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		if (!isAllowedLinkDestination(content, request.getRemoteUser())) {
			String msg = NLS.bind("Cannot link to server path {0}. Use the orion.file.allowedPaths property to specify server locations where content can be linked.", content);
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, msg, null));
			return null;
		}

		try {
			computeProjectLocation(request, project, content, getInit(toAdd));
			//project creation will assign unique project id
			getMetaStore().createProject(project);
		} catch (CoreException e) {
			String msg = "Error persisting project state";
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return null;
		}
		try {
			getMetaStore().updateProject(project);
		} catch (CoreException e) {
			boolean authFail = handleAuthFailure(request, response, e);

			//delete the project so we don't end up with a project in a bad location
			try {
				getMetaStore().deleteProject(workspace.getUniqueId(), project.getFullName());
			} catch (CoreException e1) {
				//swallow secondary error
				LogHelper.log(e1);
			}
			if (authFail) {
				return null;
			}
			//we are unable to write in the platform location!
			String msg = NLS.bind("Cannot create project: {0}", project.getFullName());
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return null;
		}

		return project;
	}

	private boolean handleError(HttpServletRequest request, HttpServletResponse response, int httpCode, String message) throws ServletException {
		return handleError(request, response, httpCode, message, null);
	}

	private boolean handleError(HttpServletRequest request, HttpServletResponse response, int httpCode, String message, Throwable cause) throws ServletException {
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, httpCode, message, cause));
	}

	private boolean handleGetWorkspaceFileList(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace) throws IOException {
		String filter = request.getParameter("filter");
		FileListDirectoryWalker walker = new FileListDirectoryWalker(workspace, filter);
		JSONObject results = walker.getFileList();
		OrionServlet.writeJSONResponse(request, response, results);
		return true;
	}

	private boolean handleGetWorkspaceSyncVersion(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace) throws IOException {
		JSONObject results = new JSONObject();
		try {
			results.put("SyncVersion", FileListDirectoryWalker.SYNC_VERSION);
			OrionServlet.writeJSONResponse(request, response, results);
			return true;
		} catch (JSONException e) {
			//should never happen
			throw new RuntimeException(e);
		}
	}

	private boolean handleGetWorkspaceMetadata(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace) throws IOException {
		//we need the base location for the workspace servlet. Since this is a GET 
		//on workspace servlet we need to strip off all but the first segment of the request path
		URI requestLocation = getURI(request);
		URI baseLocation;
		try {
			baseLocation = new URI(requestLocation.getScheme(), null, request.getServletPath(), null, null);
		} catch (URISyntaxException e) {
			//should never happen
			throw new RuntimeException(e);
		}
		OrionServlet.writeJSONResponse(request, response, toJSON(workspace, requestLocation, baseLocation));
		return true;

	}

	/**
	 * Implementation of project move. Returns whether the move requested was handled.
	 * Returns <code>false</code> if this method doesn't know how to interpret the request.
	 */
	private boolean handleMoveProject(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace, IFileStore source, ProjectInfo projectInfo, String sourceLocation, String destinationName, boolean isFile) throws ServletException, IOException {
		try {
			final IMetaStore metaStore = getMetaStore();

			boolean created = false;
			if (projectInfo == null || !workspace.getUniqueId().equals(projectInfo.getWorkspaceId())) {
				//moving a folder to become a project
				JSONObject data = new JSONObject();
				try {
					data.put(ProtocolConstants.KEY_NAME, destinationName);
				} catch (JSONException e) {
					//cannot happen
				}
				ProjectInfo newProjectInfo = createProject(request, response, workspace, data);
				if (newProjectInfo == null)
					return true;
				//move the contents
				source.move(newProjectInfo.getProjectStore(), EFS.OVERWRITE, null);
				if (projectInfo != null) {
					OrionConfiguration.getMetaStore().deleteProject(projectInfo.getWorkspaceId(), projectInfo.getFullName());
				}
				projectInfo = newProjectInfo;
				created = true;
			} else {
				//a project move is simply a rename
				projectInfo.setFullName(destinationName);
				metaStore.updateProject(projectInfo);
			}

			//location doesn't change on move project
			JSONObject result;
			if (isFile) {
				URI baseLocation = new URI("orion", null, Activator.LOCATION_FILE_SERVLET, null, null);
				baseLocation = URIUtil.append(baseLocation, projectInfo.getWorkspaceId());
				baseLocation = URIUtil.append(baseLocation, projectInfo.getFullName());
				result = ServletFileStoreHandler.toJSON(projectInfo.getProjectStore(), projectInfo.getProjectStore().fetchInfo(), baseLocation);
			} else {
				URI baseLocation = getURI(request);
				result = ProjectInfoResourceHandler.toJSON(workspace, projectInfo, baseLocation);
			}
			OrionServlet.writeJSONResponse(request, response, result);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, sourceLocation);
			response.setStatus(created ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_OK);
		} catch (Exception e) {
			String msg = NLS.bind("Error persisting project state: {0}", source.getName());
			return handleError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
		return true;
	}

	private boolean handlePutWorkspaceMetadata(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace) {
		return false;
	}

	private boolean handleRemoveProject(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace) throws IOException, JSONException, ServletException {
		IPath path = new Path(request.getPathInfo());
		//format is /workspaceId/project/<projectId>
		if (path.segmentCount() != 3)
			return false;
		String workspaceId = path.segment(0);
		String projectName = path.segment(2);
		try {
			ProjectInfo project = getMetaStore().readProject(workspaceId, projectName);
			if (project == null) {
				//nothing to do if project does not exist
				return true;
			}

			removeProject(request.getRemoteUser(), workspace, project);
		} catch (CoreException e) {
			ServerStatus error = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error removing project", e);
			LogHelper.log(error);
			return statusHandler.handleRequest(request, response, error);
		}

		return true;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, WorkspaceInfo workspace) throws ServletException {
		if (workspace == null)
			return statusHandler.handleRequest(request, response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Workspace not specified"));
		//we could split and handle different API versions here if needed
		try {
			switch (getMethod(request)) {
				case GET :
					String fileList = request.getParameter("fileList");
					String syncVersion = request.getParameter("syncVersion");
					if (fileList != null) {
						return handleGetWorkspaceFileList(request, response, workspace);
					} else if (syncVersion != null) {
						return handleGetWorkspaceSyncVersion(request, response, workspace);
					} else {
						return handleGetWorkspaceMetadata(request, response, workspace);
					}
				case PUT :
					return handlePutWorkspaceMetadata(request, response, workspace);
				case POST :
					return handleAddOrRemoveProject(request, response, workspace);
				case DELETE :
					IPath path = new Path(request.getPathInfo());
					if (path.segmentCount() == 1) {
						try {
							removeWorkspace(request.getRemoteUser(), workspace);
						} catch (CoreException e) {
							String msg = NLS.bind("Error handling deletet workspace request {0}", workspace.getUniqueId());
							return statusHandler.handleRequest(request, response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
						}
						return true;
					}
					return handleRemoveProject(request, response, workspace);
				default :
					//fall through
			}
		} catch (IOException e) {
			String msg = NLS.bind("Error handling request against workspace {0}", workspace.getUniqueId());
			statusHandler.handleRequest(request, response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		}
		return false;
	}

	/**
	 * Returns <code>true</code> if the provided content location URI is a valid
	 * place to store project data for the given user. Returns <code>false</code>
	 * otherwise.
	 */
	private boolean isAllowedLinkDestination(String content, String user) {
		if (content == null)
			return true;

		String prefixes = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_ALLOWED_PATHS);
		if (prefixes == null)
			prefixes = ServletTestingSupport.allowedPrefixes;
		if (prefixes != null) {
			StringTokenizer t = new StringTokenizer(prefixes, ","); //$NON-NLS-1$
			while (t.hasMoreTokens()) {
				String prefix = t.nextToken();
				if (content.startsWith(prefix)) {
					if (content.contains("..")) {
						// Bugzilla 420795 do not allow parent in paths
						return false;
					}
					return true;
				}
			}
		}

		URI contentURI = null;
		//use the content location specified by the user
		try {
			URI candidate = new URI(content);
			//check if we support this scheme
			String scheme = candidate.getScheme();
			if (scheme != null) {
				if (EFS.getFileSystem(scheme) != null)
					contentURI = candidate;
				//we only restrict local file system access
				if (!EFS.SCHEME_FILE.equals(scheme))
					return true;
			}
		} catch (URISyntaxException e) {
			//if this is not a valid URI try to parse it as file path below
		} catch (CoreException e) {
			//if we don't support given scheme try to parse as location as a file path below
		}
		String userArea = System.getProperty(Activator.PROP_USER_AREA);
		if (userArea != null) {
			IPath path = new Path(userArea).append(user);
			if (contentURI == null)
				contentURI = new File(content).toURI();
			if (contentURI.toString().startsWith(path.toFile().toURI().toString()))
				return true;
		}
		return false;
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
	private boolean validateProjectName(WorkspaceInfo workspace, String name, HttpServletRequest request, HttpServletResponse response) throws ServletException {
		if (name == null || name.trim().length() == 0) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Project name cannot be empty", null));
			return false;
		}
		if (name.contains("/") || name.equals("workspace") || name.endsWith(".json") || name.equals("user") || name.contains("OrionContent")) { //$NON-NLS-1$
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid project name: {0}", name), null));
			return false;
		}
		try {
			if (getMetaStore().readProject(workspace.getUniqueId(), name) != null) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Duplicate project name: {0}", name), null));
				return false;
			}
		} catch (CoreException e) {
			LogHelper.log(e);
			//this is just pre-validation so let it continue and fail in the actual creation
		}
		return true;
	}
}
