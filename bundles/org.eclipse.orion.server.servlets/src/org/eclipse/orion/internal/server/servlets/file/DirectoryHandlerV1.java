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
package org.eclipse.orion.internal.server.servlets.file;

import org.eclipse.orion.server.servlets.OrionServlet;

import org.eclipse.orion.internal.server.servlets.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * Handles HTTP requests against directories for eclipse web protocol version
 * 1.0.
 */
public class DirectoryHandlerV1 extends ServletResourceHandler<IFileStore> {
	static final int CREATE_COPY = 0x1;
	static final int CREATE_MOVE = 0x2;
	static final int CREATE_NO_OVERWRITE = 0x4;

	private final ServletResourceHandler<IStatus> statusHandler;

	private final URI rootStoreURI;

	public DirectoryHandlerV1(URI rootStoreURI, ServletResourceHandler<IStatus> statusHandler) {
		this.rootStoreURI = rootStoreURI;
		this.statusHandler = statusHandler;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws IOException, CoreException {
		URI location = getURI(request);
		JSONObject result = ServletFileStoreHandler.toJSON(dir.fetchInfo(), location);
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

	private void encodeChildren(IFileStore dir, URI location, JSONObject result, int depth) throws CoreException {
		if (depth <= 0)
			return;
		JSONArray children = new JSONArray();
		IFileStore[] childStores = dir.childStores(EFS.NONE, null);
		if (childStores.length == 0)
			return;
		for (IFileStore child : childStores) {
			IFileInfo childInfo = child.fetchInfo();
			URI childLocation = URIUtil.append(location, childInfo.getName());
			JSONObject childResult = ServletFileStoreHandler.toJSON(childInfo, childLocation);
			encodeChildren(child, childLocation, childResult, depth - 1);
			children.put(childResult);
		}
		try {
			result.put("Children", children);
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
		int options = getCreateOptions(request);
		IFileStore toCreate = dir.getChild(name);
		boolean sourceExists = toCreate.fetchInfo().exists();
		if (!validateOptions(request, response, toCreate, sourceExists, options))
			return true;
		//perform the operation
		if (performPost(request, response, requestObject, toCreate, options)) {
			//write the response
			URI location = URIUtil.append(getURI(request), name);
			JSONObject result = ServletFileStoreHandler.toJSON(toCreate.fetchInfo(), location);
			OrionServlet.writeJSONResponse(request, response, result);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, location.toString());
			//response code should indicate if a new resource was actually created or not
			response.setStatus(sourceExists ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED);
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
		if (isCopy || isMove)
			return performCopyMove(request, response, requestObject, toCreate, isCopy);
		if (requestObject.optBoolean(ProtocolConstants.KEY_DIRECTORY))
			toCreate.mkdir(EFS.NONE, null);
		else
			toCreate.openOutputStream(EFS.NONE, null).close();
		return true;
	}

	/**
	 * Perform a copy or move as specified by the request.
	 * @return <code>true</code> if the operation was successful, and <code>false</code> otherwise.
	 */
	private boolean performCopyMove(HttpServletRequest request, HttpServletResponse response, JSONObject requestObject, IFileStore toCreate, boolean isCopy) throws ServletException, CoreException {
		String locationString = requestObject.optString(ProtocolConstants.KEY_LOCATION, null);
		if (locationString == null) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Copy or move request must specify source location", null));
			return false;
		}
		try {
			URI sourceLocation = new URI(locationString);
			//resolve relative URI against request URI
			String sourcePath = getURI(request).resolve(sourceLocation).getPath();
			IFileStore source = EFS.getStore(Util.getURIWithAuthority(rootStoreURI, request.getRemoteUser())).getFileStore(new Path(sourcePath));
			//note we checked in preconditions that overwrite is ok here
			try {
				if (isCopy)
					source.copy(toCreate, EFS.OVERWRITE, null);
				else
					source.move(toCreate, EFS.OVERWRITE, null);
			} catch (CoreException e) {
				if (!source.fetchInfo().exists()) {
					statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("Source does not exist: ", locationString), e));
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
		//slug takes precedence
		String name = request.getHeader(ProtocolConstants.HEADER_SLUG);
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
	private boolean validateOptions(HttpServletRequest request, HttpServletResponse response, IFileStore toCreate, boolean sourceExists, int options) throws ServletException {
		//operation cannot be both copy and move
		int copyMove = CREATE_COPY | CREATE_MOVE;
		if ((options & copyMove) == copyMove) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", null));
			return false;
		}
		//if overwrite is disallowed make sure destination does not exist yet
		boolean noOverwrite = (options & CREATE_NO_OVERWRITE) != 0;
		if (noOverwrite && sourceExists) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_PRECONDITION_FAILED, "Resource cannot be overwritten", null));
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
				if ("copy".equalsIgnoreCase(option)) //$NON-NLS-1$
					result |= CREATE_COPY;
				else if ("move".equalsIgnoreCase(option)) //$NON-NLS-1$
					result |= CREATE_MOVE;
				else if ("no-overwrite".equalsIgnoreCase(option)) //$NON-NLS-1$
					result |= CREATE_NO_OVERWRITE;
			}
		}
		return result;
	}

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws JSONException, CoreException, ServletException, IOException {
		dir.delete(EFS.NONE, null);
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
			}
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		} catch (Exception e) {
			throw new ServletException(NLS.bind("Error retrieving file: {0}", dir), e);
		}
		return false;
	}
}
