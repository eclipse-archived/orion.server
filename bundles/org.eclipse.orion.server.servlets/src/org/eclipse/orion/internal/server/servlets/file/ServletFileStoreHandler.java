/*******************************************************************************
 * Copyright (c) 2010, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.file;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.EncodingUtils;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General responder for IFileStore. This class provides general support for serializing
 * and deserializing file and directory representations in server requests and responses.
 * Specific behavior for a particular request is performed by a separate handler
 * depending on file type and protocol version.
 */
public class ServletFileStoreHandler extends ServletResourceHandler<IFileStore> {
	public static VersionRange VERSION1 = new VersionRange("[1,2)"); //$NON-NLS-1$
	//The following two arrays must define their attributes in the same order
	private static int[] ATTRIBUTE_BITS = new int[] {EFS.ATTRIBUTE_ARCHIVE, EFS.ATTRIBUTE_EXECUTABLE, EFS.ATTRIBUTE_HIDDEN, EFS.ATTRIBUTE_IMMUTABLE, EFS.ATTRIBUTE_READ_ONLY, EFS.ATTRIBUTE_SYMLINK};
	private static String[] ATTRIBUTE_KEYS = new String[] {ProtocolConstants.KEY_ATTRIBUTE_ARCHIVE, ProtocolConstants.KEY_ATTRIBUTE_EXECUTABLE, ProtocolConstants.KEY_ATTRIBUTE_HIDDEN, ProtocolConstants.KEY_ATTRIBUTE_IMMUTABLE, ProtocolConstants.KEY_ATTRIBUTE_READ_ONLY, ProtocolConstants.KEY_ATTRIBUTE_SYMLINK};

	private final ServletResourceHandler<IFileStore> directorySerializerV1;
	private final ServletResourceHandler<IFileStore> fileSerializerV1;
	private final ServletResourceHandler<IFileStore> genericDirectorySerializer;
	private final ServletResourceHandler<IFileStore> genericFileSerializer;

	final ServletResourceHandler<IStatus> statusHandler;

	public static IFileInfo fromJSON(JSONObject object) {
		FileInfo info = (FileInfo) EFS.createFileInfo();
		copyJSONToFileInfo(object, info);
		return info;
	}

	/**
	 * Copies any defined fields in the provided JSON object into the destination file info.
	 * @param source The JSON object to copy fields from
	 * @param destination The file info to copy fields to
	 */
	public static void copyJSONToFileInfo(JSONObject source, FileInfo destination) {
		destination.setName(source.optString(ProtocolConstants.KEY_NAME, destination.getName()));
		destination.setLastModified(source.optLong(ProtocolConstants.KEY_LAST_MODIFIED, destination.getLastModified()));
		destination.setDirectory(source.optBoolean(ProtocolConstants.KEY_DIRECTORY, destination.isDirectory()));

		JSONObject attributes = source.optJSONObject(ProtocolConstants.KEY_ATTRIBUTES);
		if (attributes != null) {
			for (int i = 0; i < ATTRIBUTE_KEYS.length; i++) {
				//undefined means the client does not want to change the value, so can't interpret as false
				if (!attributes.isNull(ATTRIBUTE_KEYS[i]))
					destination.setAttribute(ATTRIBUTE_BITS[i], attributes.optBoolean(ATTRIBUTE_KEYS[i]));
			}
		}
	}

	public static IFileInfo fromJSON(HttpServletRequest request) throws IOException, JSONException {
		return fromJSON(OrionServlet.readJSONRequest(request));
	}

	public static JSONObject toJSON(IFileStore store, IFileInfo info, URI location) {
		JSONObject result = new JSONObject();
		try {
			result.put(ProtocolConstants.KEY_NAME, info.getName());
			result.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, info.getLastModified());
			if (location != null || info.isDirectory()) {
				result.put(ProtocolConstants.KEY_DIRECTORY, info.isDirectory());
			}
			result.put(ProtocolConstants.KEY_LENGTH, info.getLength());
			if (location != null) {
				if (info.isDirectory() && !location.getPath().endsWith("/")) {
					location = URIUtil.append(location, "");
				}
				result.put(ProtocolConstants.KEY_LOCATION, location);
				if (info.isDirectory())
					try {
						result.put(ProtocolConstants.KEY_CHILDREN_LOCATION, new URI(location.getScheme(), location.getAuthority(), location.getPath(), "depth=1", location.getFragment())); //$NON-NLS-1$
					} catch (URISyntaxException e) {
						throw new RuntimeException(e);
					}
			}
			JSONObject attributes = getAttributes(store, info, location == null);
			if (attributes.keys().hasNext()) {
				result.put(ProtocolConstants.KEY_ATTRIBUTES, attributes);
			}
		} catch (JSONException e) {
			//cannot happen because the key is non-null and the values are strings
			throw new RuntimeException(e);
		}
		return result;
	}

	/**
	 * Returns a JSON Object containing the attributes supported and defined by the given file.
	 */
	private static JSONObject getAttributes(IFileStore store, IFileInfo info, boolean optional) throws JSONException {
		int supported = store.getFileSystem().attributes();
		JSONObject attributes = new JSONObject();
		for (int i = 0; i < ATTRIBUTE_KEYS.length; i++)
			if ((supported & ATTRIBUTE_BITS[i]) != 0 && (!optional || info.getAttribute(ATTRIBUTE_BITS[i])))
				attributes.put(ATTRIBUTE_KEYS[i], info.getAttribute(ATTRIBUTE_BITS[i]));
		return attributes;
	}

	public ServletFileStoreHandler(ServletResourceHandler<IStatus> statusHandler, ServletContext context) {
		this.statusHandler = statusHandler;
		fileSerializerV1 = new FileHandlerV1(statusHandler, context);
		genericFileSerializer = new GenericFileHandler(context);
		directorySerializerV1 = new DirectoryHandlerV1(statusHandler);
		genericDirectorySerializer = new GenericDirectoryHandler();
	}

	private boolean handleDirectory(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		String versionString = request.getHeader(ProtocolConstants.HEADER_ORION_VERSION);
		Version version = versionString == null ? null : new Version(versionString);
		ServletResourceHandler<IFileStore> handler;
		if (version != null && VERSION1.isIncluded(version))
			handler = directorySerializerV1;
		else
			handler = genericDirectorySerializer;
		return handler.handleRequest(request, response, file);
	}

	private boolean handleFile(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		//could plug in more complex mapping here
		String versionString = request.getHeader(ProtocolConstants.HEADER_ORION_VERSION);
		Version version = versionString == null ? null : new Version(versionString);
		ServletResourceHandler<IFileStore> handler;
		if (version != null && VERSION1.isIncluded(version))
			handler = fileSerializerV1;
		else
			handler = genericFileSerializer;
		return handler.handleRequest(request, response, file);
	}

	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		IFileInfo fileInfo;
		try {
			fileInfo = file.fetchInfo(EFS.NONE, null);
		} catch (CoreException e) {
			if (handleAuthFailure(request, response, e))
				return true;
			//assume file does not exist
			fileInfo = new FileInfo(file.getName());
			((FileInfo) fileInfo).setExists(false);
		}
		if (!request.getMethod().equals("PUT") && !fileInfo.exists()) { //$NON-NLS-1$
			if("true".equals(request.getHeader("read-if-exists"))) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.WARNING, 204, NLS.bind("No file content: {0}", EncodingUtils.encodeForHTML(request.getPathInfo())), null)); 
			}
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, 404, NLS.bind("File not found: {0}", EncodingUtils.encodeForHTML(request.getPathInfo())), null));
		}
		if("true".equals(IOUtilities.getQueryParameter(request, "project"))) {
			return getProject(request, response, file, fileInfo);
		}
		if (fileInfo.isDirectory()) {
			return handleDirectory(request, response, file);
		}
		return handleFile(request, response, file);
	}

	/**
	 * Tries to find the enclosing project context for the given file information
	 * @param request The original request
	 * @param fileInfo The file information to start looking from
	 * @param names The map of file names that are considered "project-like" files
	 * @return The name of the project containing the given file information
	 * @throws ServletException 
	 * @since 14.0
	 */
	private boolean getProject(HttpServletRequest request, HttpServletResponse response, IFileStore file, IFileInfo fileInfo) throws ServletException {
		String n = IOUtilities.getQueryParameter(request, "names");
		HashMap<String, Boolean> names = new HashMap<String, Boolean>();
		names.put(".git", true);
		names.put("project.json", false);
		if(n != null) {
			try {
				String[] clientNames = URLDecoder.decode(n, "UTF-8").split(",");
				for (String _n : clientNames) {
					names.put(_n, false);
				}
			} catch(UnsupportedEncodingException use) {
				Logger logger = LoggerFactory.getLogger(ServletFileStoreHandler.class);
				logger.error("Failed to decode client project names: " + n);
			}
		}
		String pathString = request.getPathInfo();
        IPath path = new Path(pathString);
        String workspacePath = null;
        String workspaceId = null;
        try {
            WorkspaceInfo workspace = OrionConfiguration.getMetaStore().readWorkspace(path.segment(0));
            workspaceId = workspace.getUniqueId();
            IFileStore userHome = OrionConfiguration.getMetaStore().getUserHome(workspace.getUserId());
            IFileStore workspaceHome = userHome.getChild(workspaceId.substring(workspaceId.indexOf('-') + 1));
            File workspaceRoot = workspaceHome.toLocalFile(EFS.NONE, null);
            workspacePath = workspaceRoot.getAbsolutePath();
        } catch (CoreException e) {
        	Logger logger = LoggerFactory.getLogger(ServletFileStoreHandler.class);
			logger.error("Exception computing workspace path");
        }
		if(workspacePath == null) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.WARNING, 204, NLS.bind("No workspace path for: {0}", EncodingUtils.encodeForHTML(fileInfo.getName())), null));
		}
		if(workspaceId == null) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.WARNING, 204, NLS.bind("No workspace ID for: {0}", EncodingUtils.encodeForHTML(fileInfo.getName())), null));
		}
		IFileStore project = findProject(request, file, fileInfo, names, workspacePath);
		if(project == null) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.WARNING, 204, NLS.bind("No project context for: {0}", EncodingUtils.encodeForHTML(fileInfo.getName())), null));
		}
		try {
			URI location = getProjectURI(project.toURI(), workspacePath, workspaceId);
			JSONObject result = toJSON(project, project.fetchInfo(), location);
			OrionServlet.writeJSONResponse(request, response, result);
		} catch (IOException ioe) {
			Logger logger = LoggerFactory.getLogger(ServletFileStoreHandler.class);
			logger.error("Failed to write response for project: " + project.getName());
		}
		return true;
	}
	
	/**
	 * Resolve the correct project URI using the <code>orion</code> scheme
	 * @param projectUri The computed project URI
	 * @return The {@link URI} to pass back in the response
	 * @since 14.0
	 */
	private URI getProjectURI(URI projectUri, String workspacePath, String workspaceId) {
		String pathInfo = projectUri.getPath().substring(workspacePath.length());
		try {
			// Note: no query string!
			return new URI("orion", null, "/file/"+workspaceId+pathInfo, null, null);
		} catch (URISyntaxException e) {
			//location not properly encoded
			return null;
		}
	}
	
	/**
	 * @param fileInfo The originating fileInfo 
	 * @param file The originating file store
	 * @param request The original request
	 * @param names The map of names to consider project-like
	 * @return The {@link IFileStore} for the project or <code>null</code>
	 * @since 14.0
	 */
	private IFileStore findProject(HttpServletRequest request, IFileStore file, IFileInfo fileInfo, HashMap<String, Boolean> names, String workspacePath) {
		IFileStore parent = file;
		try {
			File parentFile = parent.toLocalFile(EFS.NONE, null);
			if (parentFile == null) {
				Logger logger = LoggerFactory.getLogger(ServletFileStoreHandler.class);
				logger.error("Unable to get the parent local file from: " + parent);
				return null;
			}
			while(parent != null && parentFile != null && parentFile.getAbsolutePath().startsWith(workspacePath)) {
				IFileInfo[] children = parent.childInfos(EFS.NONE, null);
				for (IFileInfo childInfo : children) {
					if(childInfo.exists() && names.containsKey(childInfo.getName())) {
						if(childInfo.isDirectory() && names.get(childInfo.getName())) {
							return parent;
						} else if(!names.get(childInfo.getName())) {
							return parent;
						}
					}
				}
				parent = parent.getParent();
				parentFile = parent.toLocalFile(EFS.NONE, null);
			}
		} catch (CoreException ce) {
			Logger logger = LoggerFactory.getLogger(ServletFileStoreHandler.class);
			logger.error("Exception computing parent folder from: " + parent);
			return null;
		}
		return null;
	}
}