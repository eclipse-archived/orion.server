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
package org.eclipse.e4.internal.webide.server.servlets.file;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.e4.internal.webide.server.servlets.*;
import org.eclipse.e4.webide.server.servlets.EclipseWebServlet;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Version;

/**
 * General responder for IFileStore. This class redirects the serialization to an appropriate
 * serializer for the particular request and response.
 */
public class ServletFileStoreHandler extends ServletResourceHandler<IFileStore> {
	public static VersionRange VERSION1 = new VersionRange("[1,2)"); //$NON-NLS-1$

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
	}

	public static IFileInfo fromJSON(HttpServletRequest request) throws IOException, JSONException {
		return fromJSON(EclipseWebServlet.readJSONRequest(request));
	}

	public static JSONObject toJSON(IFileInfo info, URI location) {
		JSONObject result = new JSONObject();
		try {
			result.put(ProtocolConstants.KEY_NAME, info.getName());
			result.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, info.getLastModified());
			result.put(ProtocolConstants.KEY_DIRECTORY, info.isDirectory());
			result.put(ProtocolConstants.KEY_LENGTH, info.getLength());
			if (location != null) {
				result.put(ProtocolConstants.KEY_LOCATION, location);
				if (info.isDirectory())
					result.put(ProtocolConstants.KEY_CHILDREN_LOCATION, location + "?depth=1"); //$NON-NLS-1$
			}
		} catch (JSONException e) {
			//cannot happen because the key is non-null and the values are strings
			throw new RuntimeException(e);
		}
		return result;
	}

	ServletFileStoreHandler(URI rootStoreURI, ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
		fileSerializerV1 = new FileHandlerV1(statusHandler);
		genericFileSerializer = new GenericFileHandler();
		directorySerializerV1 = new DirectoryHandlerV1(rootStoreURI, statusHandler);
		genericDirectorySerializer = new GenericDirectoryHandler();

	}

	private boolean handleDirectory(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		String versionString = request.getHeader("EclipseWeb-Version");
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
		String versionString = request.getHeader("EclipseWeb-Version");
		Version version = versionString == null ? null : new Version(versionString);
		ServletResourceHandler<IFileStore> handler;
		if (version != null && VERSION1.isIncluded(version))
			handler = fileSerializerV1;
		else
			handler = genericFileSerializer;
		return handler.handleRequest(request, response, file);
	}

	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		IFileInfo fileInfo = file.fetchInfo();
		if (!fileInfo.exists())
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, 404, NLS.bind("File not found: {0}", request.getPathInfo()), null));
		if (fileInfo.isDirectory())
			return handleDirectory(request, response, file);
		return handleFile(request, response, file);
	}

}