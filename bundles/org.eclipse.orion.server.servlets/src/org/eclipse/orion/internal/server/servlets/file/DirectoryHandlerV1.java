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
package org.eclipse.orion.internal.server.servlets.file;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.Slug;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles HTTP requests against directories for eclipse web protocol version
 * 1.0.
 */
public class DirectoryHandlerV1 extends ServletResourceHandler<IFileStore> {
	static final int CREATE_COPY = 0x1;
	static final int CREATE_MOVE = 0x2;
	static final int CREATE_NO_OVERWRITE = 0x4;

	private final ServletResourceHandler<IStatus> statusHandler;

	public DirectoryHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws IOException, CoreException {
		URI location = getURI(request);
		JSONObject result = ServletFileStoreHandler.toJSON(dir, dir.fetchInfo(EFS.NONE, null), location);
		String depthString = request.getParameter(ProtocolConstants.PARM_DEPTH);
		int depth = 0;
		if (depthString != null) {
			try {
				depth = Integer.parseInt(depthString);
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		encodeChildren(dir, location, result, depth);
		OrionServlet.writeJSONResponse(request, response, result);
		return true;
	}

	public static void encodeChildren(IFileStore dir, URI location, JSONObject result, int depth) throws CoreException {
		encodeChildren(dir, location, result, depth, true);
	}
	
	public static void encodeChildren(IFileStore dir, URI location, JSONObject result, int depth, boolean addLocation) throws CoreException {
		if (depth <= 0)
			return;
		JSONArray children = new JSONArray();
		//more efficient to ask for child information in bulk for certain file systems
		IFileInfo[] childInfos = dir.childInfos(EFS.NONE, null);
		for (IFileInfo childInfo : childInfos) {
			IFileStore childStore = dir.getChild(childInfo.getName());
			String name = childInfo.getName();
			if (childInfo.isDirectory())
				name += "/"; //$NON-NLS-1$
			URI childLocation = URIUtil.append(location, name);
			JSONObject childResult = ServletFileStoreHandler.toJSON(childStore, childInfo, addLocation ? childLocation : null);
			if (childInfo.isDirectory())
				encodeChildren(childStore, childLocation, childResult, depth - 1);
			children.put(childResult);
		}
		try {
			result.put(ProtocolConstants.KEY_CHILDREN, children);
		} catch (JSONException e) {
			// cannot happen
			throw new RuntimeException(e);
		}
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws JSONException, CoreException, ServletException, IOException {
		//setup and precondition checks
		JSONObject requestObject = OrionServlet.readJSONRequest(request);
		String name = computeName(request, requestObject);
		if (name.length() == 0)
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "File name not specified.", null));
		IFileStore toCreate = dir.getChild(name);
		if (!name.equals(toCreate.getName()) || name.contains(":"))
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad file name: " + name, null));
		int options = getCreateOptions(request);
		boolean destinationExists = toCreate.fetchInfo(EFS.NONE, null).exists();
		if (!validateOptions(request, response, toCreate, destinationExists, options))
			return true;
		//perform the operation
		if (performPost(request, response, requestObject, toCreate, options)) {
			//write the response
			URI location = URIUtil.append(getURI(request), name);
			JSONObject result = ServletFileStoreHandler.toJSON(toCreate, toCreate.fetchInfo(EFS.NONE, null), location);
			result.append("FileEncoding", System.getProperty("file.encoding"));
			OrionServlet.writeJSONResponse(request, response, result);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, ServletResourceHandler.resovleOrionURI(request, location).toASCIIString());
			//response code should indicate if a new resource was actually created or not
			response.setStatus(destinationExists ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED);
		}
		return true;
	}

	/**
	 * Performs the actual modification corresponding to a POST request. All preconditions
	 * are assumed to be satisfied.
	 * @return <code>true</code> if the operation was successful, and <code>false</code> otherwise.
	 */
	private boolean performPost(HttpServletRequest request, HttpServletResponse response, JSONObject requestObject, IFileStore toCreate, int options) throws CoreException, IOException, ServletException {
		boolean isCopy = (options & CREATE_COPY) != 0;
		boolean isMove = (options & CREATE_MOVE) != 0;
		try {
			if (isCopy || isMove)
				return performCopyMove(request, response, requestObject, toCreate, isCopy, options);
			if (requestObject.optBoolean(ProtocolConstants.KEY_DIRECTORY))
				toCreate.mkdir(EFS.NONE, null);
			else
				toCreate.openOutputStream(EFS.NONE, null).close();
		} catch (CoreException e) {
			IStatus status = e.getStatus();
			if (status != null && status.getCode() == EFS.ERROR_WRITE) {
				// Sanitize message, as it might contain the filepath.
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create: " + toCreate.getName(), null));
				return false;
			}
			throw e;
		}
		return true;
	}

	/**
	 * Perform a copy or move as specified by the request.
	 * @return <code>true</code> if the operation was successful, and <code>false</code> otherwise.
	 */
	private boolean performCopyMove(HttpServletRequest request, HttpServletResponse response, JSONObject requestObject, IFileStore toCreate, boolean isCopy, int options) throws ServletException, CoreException {
		String locationString = requestObject.optString(ProtocolConstants.KEY_LOCATION, null);
		if (locationString == null) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Copy or move request must specify source location", null));
			return false;
		}
		try {
			IFileStore source = resolveSourceLocation(request, locationString);
			if (source == null) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("Source does not exist: ", locationString), null));
				return false;
			} else if (source.isParentOf(toCreate)) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "The destination cannot be a descendent of the source location", null));
				return false;
			}
			boolean allowOverwrite = (options & CREATE_NO_OVERWRITE) == 0;
			int efsOptions = allowOverwrite ? EFS.OVERWRITE : EFS.NONE;
			try {
				if (isCopy) {
					source.copy(toCreate, efsOptions, null);
				} else {
					source.move(toCreate, efsOptions, null);
					// Path format is /file/workspaceId/projectId/[location to folder]
					Path path = new Path(locationString);
					if (path.segmentCount() == 3 && path.segment(0).equals("file")) {
						// The folder is a project, remove the metadata
						OrionConfiguration.getMetaStore().deleteProject(path.segment(1), source.getName());
					}
				}
			} catch (CoreException e) {
				if (!source.fetchInfo(EFS.NONE, null).exists()) {
					statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("Source does not exist: ", locationString), e));
					return false;
				}
				if (e.getStatus().getCode() == EFS.ERROR_EXISTS) {
					statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_PRECONDITION_FAILED, "A file or folder with the same name already exists at this location", null));
					return false;
				}
				//just rethrow if we can't do something more specific
				throw e;
			}
		} catch (URISyntaxException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Bad source location in request: ", locationString), e));
			return false;
		}
		return true;
	}

	/**
	 * Computes the name of the resource to be created by a POST operation. Returns
	 * an empty string if the name was not specified.
	 */
	private String computeName(HttpServletRequest request, JSONObject requestObject) {
		String name = Slug.decode(request.getHeader(ProtocolConstants.HEADER_SLUG));

		//next comes the source location for a copy/move
		if (name == null || name.length() == 0) {
			String location = requestObject.optString(ProtocolConstants.KEY_LOCATION);
			int lastSlash = location.lastIndexOf('/');
			if (lastSlash >= 0)
				name = location.substring(lastSlash + 1);
		}
		//finally use the name attribute from the request body
		if (name == null || name.length() == 0)
			name = requestObject.optString(ProtocolConstants.KEY_NAME);
		return name;
	}

	/**
	 * Asserts that request options are valid. If options are not valid then this method handles the request response and return false. If the options
	 * are valid this method return true.
	 */
	private boolean validateOptions(HttpServletRequest request, HttpServletResponse response, IFileStore toCreate, boolean destinationExists, int options) throws ServletException {
		//operation cannot be both copy and move
		int copyMove = CREATE_COPY | CREATE_MOVE;
		if ((options & copyMove) == copyMove) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", null));
			return false;
		}
		//if overwrite is disallowed make sure destination does not exist yet
		boolean noOverwrite = (options & CREATE_NO_OVERWRITE) != 0;
		//for copy/move case, let the implementation check for overwrite because pre-validating is complicated
		if ((options & copyMove) == 0 && noOverwrite && destinationExists) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_PRECONDITION_FAILED, "A file or folder with the same name already exists at this location", null));
			return false;
		}
		return true;
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

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws JSONException, CoreException, ServletException, IOException {
		Path path = new Path(request.getPathInfo());
		dir.delete(EFS.NONE, null);
		if (path.segmentCount() == 2) {
			// The folder is a project, remove the metadata
			OrionConfiguration.getMetaStore().deleteProject(path.segment(0), path.segment(1));
		} else if (path.segmentCount() == 1) {
			// The folder is a workspace, remove the metadata
			String workspaceId = path.segment(0);
			OrionConfiguration.getMetaStore().deleteWorkspace(request.getRemoteUser(), workspaceId);
		}
		return true;
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws JSONException, IOException, CoreException {
		IFileInfo info = ServletFileStoreHandler.fromJSON(request);
		dir.putInfo(info, EFS.NONE, null);
		return true;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws ServletException {
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, dir);
				case PUT :
					return handlePut(request, response, dir);
				case POST :
					return handlePost(request, response, dir);
				case DELETE :
					return handleDelete(request, response, dir);
				default :
					return false;
			}
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		} catch (CoreException e) {
			//core exception messages are designed for end user consumption, so use message directly
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		} catch (Exception e) {
			if (handleAuthFailure(request, response, e))
				return true;
			//the exception message is probably not appropriate for end user consumption
			LogHelper.log(e);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An unknown failure occurred. Consult your server log or contact your system administrator.", e));
		}
	}
}
