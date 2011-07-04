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
package org.eclipse.orion.internal.server.servlets.file;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles files in version 1 of eclipse web protocol syntax.
 */
class FileHandlerV1 extends GenericFileHandler {
	final ServletResourceHandler<IStatus> statusHandler;

	FileHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	/**
	 * The end of line sequence expected by HTTP.
	 */
	private static final String EOL = "\r\n"; //$NON-NLS-1$

	protected void handleGetMetadata(HttpServletRequest request, Writer response, IFileStore file) throws IOException {
		JSONObject result = ServletFileStoreHandler.toJSON(file, file.fetchInfo(), getURI(request));
		OrionServlet.decorateResponse(request, result);
		response.append(result.toString());
	}

	private void handleMultiPartGet(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws IOException, CoreException {
		String boundary = createBoundaryString();
		response.setHeader(ProtocolConstants.HEADER_CONTENT_TYPE, "multipart/related; boundary=\"" + boundary + '"'); //$NON-NLS-1$
		OutputStream outputStream = response.getOutputStream();
		Writer out = new OutputStreamWriter(outputStream);
		out.write("--" + boundary + EOL); //$NON-NLS-1$
		out.write("Content-Type: application/json" + EOL + EOL); //$NON-NLS-1$
		handleGetMetadata(request, out, file);
		out.write(EOL + "--" + boundary + EOL); //$NON-NLS-1$
		// headers for file contents go here
		out.write(EOL);
		out.flush();
		IOUtilities.pipe(file.openInputStream(EFS.NONE, null), outputStream, true, false);
		out.write(EOL + "--" + boundary + EOL); //$NON-NLS-1$
		out.flush();
	}

	String createBoundaryString() {
		return new UniversalUniqueIdentifier().toBase64String();
	}

	private void handleMultiPartPut(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws IOException, CoreException, JSONException {
		String typeHeader = request.getHeader(ProtocolConstants.HEADER_CONTENT_TYPE);
		String boundary = typeHeader.substring(typeHeader.indexOf("boundary=\"") + 10, typeHeader.length() - 1); //$NON-NLS-1$
		BufferedReader requestReader = request.getReader();
		handlePutMetadata(requestReader, boundary, file);
		// next come the headers for the content
		Map<String, String> contentHeaders = new HashMap<String, String>();
		String line;
		while ((line = requestReader.readLine()) != null && line.length() > 0) {
			String[] header = line.split(":"); //$NON-NLS-1$
			if (header.length == 2)
				contentHeaders.put(header[0], header[1]);
		}
		// now for the file contents
		Writer fileWriter = new BufferedWriter(new OutputStreamWriter(file.openOutputStream(EFS.NONE, null)));
		IOUtilities.pipe(requestReader, fileWriter, false, true);
	}

	private void handlePutMetadata(BufferedReader reader, String boundary, IFileStore file) throws IOException, CoreException, JSONException {
		StringBuffer buf = new StringBuffer();
		String line;
		while ((line = reader.readLine()) != null && !line.equals(boundary))
			buf.append(line);
		//merge with existing metadata
		FileInfo info = (FileInfo) file.fetchInfo();
		ServletFileStoreHandler.copyJSONToFileInfo(new JSONObject(buf.toString()), info);
		file.putInfo(info, EFS.SET_ATTRIBUTES, null);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		try {
			String parts = IOUtilities.getQueryParameter(request, "parts");
			if (parts == null || "body".equals(parts)) { //$NON-NLS-1$
				switch (getMethod(request)) {
					case DELETE :
						file.delete(EFS.NONE, null);
						break;
					default :
						handleFileContents(request, response, file);
				}
				return true;
			}
			if ("meta".equals(parts)) { //$NON-NLS-1$
				switch (getMethod(request)) {
					case GET :
						handleGetMetadata(request, response.getWriter(), file);
						return true;
					case PUT :
						handlePutMetadata(request.getReader(), null, file);
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						return true;
				}
				return false;
			}
			if ("meta,body".equals(parts) || "body,meta".equals(parts)) { //$NON-NLS-1$ //$NON-NLS-2$
				switch (getMethod(request)) {
					case GET :
						handleMultiPartGet(request, response, file);
						return true;
					case PUT :
						handleMultiPartPut(request, response, file);
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						return true;
				}
				return false;
			}
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		} catch (Exception e) {
			throw new ServletException(NLS.bind("Error retrieving file: {0}", file), e);
		}
		return false;
	}
}