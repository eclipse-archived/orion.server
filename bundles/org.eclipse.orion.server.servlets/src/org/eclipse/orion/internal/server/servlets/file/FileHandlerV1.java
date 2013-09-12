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
package org.eclipse.orion.internal.server.servlets.file;

import java.io.*;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
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
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * Handles files in version 1 of eclipse web protocol syntax.
 */
class FileHandlerV1 extends GenericFileHandler {
	final ServletResourceHandler<IStatus> statusHandler;

	FileHandlerV1(ServletResourceHandler<IStatus> statusHandler, ServletContext context) {
		super(context);
		this.statusHandler = statusHandler;
	}

	/**
	 * The end of line sequence expected by HTTP.
	 */
	private static final String EOL = "\r\n"; //$NON-NLS-1$

	// responseWriter is used, as in some cases response should be
	// appended to response generated earlier (i.e. multipart get)
	protected void handleGetMetadata(HttpServletRequest request, HttpServletResponse response, Writer responseWriter, IFileStore file) throws IOException, NoSuchAlgorithmException, JSONException, CoreException {
		JSONObject result = ServletFileStoreHandler.toJSON(file, file.fetchInfo(EFS.NONE, null), getURI(request));
		String etag = generateFileETag(file);
		result.put(ProtocolConstants.KEY_ETAG, etag);
		response.setHeader(ProtocolConstants.KEY_ETAG, etag);
		OrionServlet.decorateResponse(request, result, JsonURIUnqualificationStrategy.ALL);
		responseWriter.append(result.toString());
	}

	private void handleMultiPartGet(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws IOException, CoreException, NoSuchAlgorithmException, JSONException {
		String boundary = createBoundaryString();
		response.setHeader(ProtocolConstants.HEADER_ACCEPT_PATCH, ProtocolConstants.CONTENT_TYPE_JSON_PATCH);
		response.setHeader(ProtocolConstants.HEADER_CONTENT_TYPE, "multipart/related; boundary=\"" + boundary + '"'); //$NON-NLS-1$
		OutputStream outputStream = response.getOutputStream();
		Writer out = new OutputStreamWriter(outputStream);
		out.write("--" + boundary + EOL); //$NON-NLS-1$
		out.write("Content-Type: application/json" + EOL + EOL); //$NON-NLS-1$
		handleGetMetadata(request, response, out, file);
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

	private void handlePutContents(HttpServletRequest request, BufferedReader requestReader, HttpServletResponse response, IFileStore file) throws IOException, CoreException, NoSuchAlgorithmException, JSONException {
		String source = request.getParameter(ProtocolConstants.PARM_SOURCE);
		if (source != null) {
			//if source is specified, read contents from different URL rather than from this request stream
			IOUtilities.pipe(new URL(source).openStream(), file.openOutputStream(EFS.NONE, null), true, true);
		} else {
			//read from the request stream
			Writer fileWriter = new BufferedWriter(new OutputStreamWriter(file.openOutputStream(EFS.NONE, null), "UTF-8"));
			IOUtilities.pipe(requestReader, fileWriter, false, true);
		}

		// return metadata with the new Etag
		handleGetMetadata(request, response, response.getWriter(), file);
	}

	private void handlePatchContents(HttpServletRequest request, BufferedReader requestReader, HttpServletResponse response, IFileStore file) throws IOException, CoreException, NoSuchAlgorithmException, JSONException, ServletException {
		//read from the request stream
		StringBuffer buf = new StringBuffer();
		String line;
		while ((line = requestReader.readLine()) != null)
			buf.append(line);
		JSONObject changes = new JSONObject(buf.toString());
		//read file to memory
		Reader fileReader = new BufferedReader(new InputStreamReader(file.openInputStream(EFS.NONE, null)));
		StringBuffer oldFile = new StringBuffer();
		char[] buffer = new char[4096];
		int read = 0;
		while ((read = fileReader.read(buffer)) != -1)
			oldFile.append(buffer, 0, read);
		IOUtilities.safeClose(fileReader);

		JSONArray changeList = changes.getJSONArray("diff");
		for (int i = 0; i < changeList.length(); i++) {
			JSONObject change = changeList.getJSONObject(i);
			long start = change.getLong("start");
			long end = change.getLong("end");
			String text = change.getString("text");
			oldFile.replace((int) start, (int) end, text);
		}

		String newContents = oldFile.toString();
		boolean failed = false;
		if (changes.has("contents")) {
			String contents = changes.getString("contents");
			if (!newContents.equals(contents)) {
				failed = true;
				newContents = contents;
			}
		}
		Writer fileWriter = new BufferedWriter(new OutputStreamWriter(file.openOutputStream(EFS.NONE, null), "UTF-8"));
		IOUtilities.pipe(new StringReader(newContents), fileWriter, false, true);
		if (failed) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_ACCEPTABLE, "Bad File Diffs. Please paste this content in a bug report: \u00A0\u00A0 	" + changes.toString(), null));
			return;
		}

		// return metadata with the new Etag
		handleGetMetadata(request, response, response.getWriter(), file);
	}

	private void handleMultiPartPut(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws IOException, CoreException, JSONException, NoSuchAlgorithmException {
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
		handlePutContents(request, requestReader, response, file);
	}

	private void handlePutMetadata(BufferedReader reader, String boundary, IFileStore file) throws IOException, CoreException, JSONException {
		StringBuffer buf = new StringBuffer();
		String line;
		while ((line = reader.readLine()) != null && !line.equals(boundary))
			buf.append(line);
		//merge with existing metadata
		FileInfo info = (FileInfo) file.fetchInfo(EFS.NONE, null);
		ServletFileStoreHandler.copyJSONToFileInfo(new JSONObject(buf.toString()), info);
		file.putInfo(info, EFS.SET_ATTRIBUTES, null);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		try {
			String receivedETag = request.getHeader(ProtocolConstants.HEADER_IF_MATCH);
			if (receivedETag != null && !receivedETag.equals(generateFileETag(file))) {
				response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
				return true;
			}
			String parts = IOUtilities.getQueryParameter(request, "parts");
			if (parts == null || "body".equals(parts)) { //$NON-NLS-1$
				switch (getMethod(request)) {
					case DELETE :
						file.delete(EFS.NONE, null);
						break;
					case PUT :
						handlePutContents(request, request.getReader(), response, file);
						break;
					case POST :
						if ("PATCH".equals(request.getHeader(ProtocolConstants.HEADER_METHOD_OVERRIDE))) {
							handlePatchContents(request, request.getReader(), response, file);
						}
						break;
					default :
						return handleFileContents(request, response, file);
				}
				return true;
			}
			if ("meta".equals(parts)) { //$NON-NLS-1$
				switch (getMethod(request)) {
					case GET :
						response.setCharacterEncoding("UTF-8");
						handleGetMetadata(request, response, response.getWriter(), file);
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
						return true;
				}
				return false;
			}
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		} catch (Exception e) {
			if (!handleAuthFailure(request, response, e))
				throw new ServletException(NLS.bind("Error retrieving file: {0}", file), e);
		}
		return false;
	}
}