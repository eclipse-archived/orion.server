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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

	/**
	 * Appends metadata to a Writer. Does not flush the Writer.
	 */
	protected void appendGetMetadata(HttpServletRequest request, HttpServletResponse response, Writer responseWriter, IFileStore file) throws IOException,
			NoSuchAlgorithmException, JSONException, CoreException {
		JSONObject metadata = getMetadata(request, file);
		response.setHeader(ProtocolConstants.KEY_ETAG, metadata.getString(ProtocolConstants.KEY_ETAG));
		response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
		OrionServlet.decorateResponse(request, metadata, JsonURIUnqualificationStrategy.ALL);
		responseWriter.append(metadata.toString());
	}

	/**
	 * Writes metadata to the response.
	 */
	protected void handleGetMetadata(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws IOException, NoSuchAlgorithmException,
			JSONException, CoreException {
		JSONObject metadata = getMetadata(request, file);
		response.setHeader(ProtocolConstants.KEY_ETAG, metadata.getString(ProtocolConstants.KEY_ETAG));
		OrionServlet.writeJSONResponse(request, response, metadata);
	}

	/**
	 * @return Metadata for the file. The returned object is guaranteed to have an ETag string.
	 */
	private JSONObject getMetadata(HttpServletRequest request, IFileStore file) throws CoreException, NoSuchAlgorithmException, IOException, JSONException {
		JSONObject metadata = ServletFileStoreHandler.toJSON(file, file.fetchInfo(EFS.NONE, null), getURI(request));
		metadata.put(ProtocolConstants.KEY_ETAG, generateFileETag(file));
		return metadata;
	}

	private void handleMultiPartGet(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws IOException, CoreException,
			NoSuchAlgorithmException, JSONException {
		String boundary = createBoundaryString();
		response.setHeader(ProtocolConstants.HEADER_ACCEPT_PATCH, ProtocolConstants.CONTENT_TYPE_JSON_PATCH);
		response.setHeader(ProtocolConstants.HEADER_CONTENT_TYPE, "multipart/related; boundary=\"" + boundary + '"'); //$NON-NLS-1$
		OutputStream outputStream = response.getOutputStream();
		Writer out = new OutputStreamWriter(outputStream, "UTF-8");
		out.write("--" + boundary + EOL); //$NON-NLS-1$
		out.write("Content-Type: application/json" + EOL + EOL); //$NON-NLS-1$
		appendGetMetadata(request, response, out, file);
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

	private void handlePutContents(HttpServletRequest request, ServletInputStream requestStream, HttpServletResponse response, IFileStore file)
			throws IOException, CoreException, NoSuchAlgorithmException, JSONException {
		String source = request.getParameter(ProtocolConstants.PARM_SOURCE);
		if (!file.getParent().fetchInfo().exists()) {
			// make sure the parent folder exists
			file.getParent().mkdir(EFS.NONE, null);
		}
		if (source != null) {
			// if source is specified, read contents from different URL rather than from this request stream
			IOUtilities.pipe(new URL(source).openStream(), file.openOutputStream(EFS.NONE, null), true, true);
		} else {
			// read from the request stream
			IOUtilities.pipe(requestStream, file.openOutputStream(EFS.NONE, null), false, true);
		}

		// return metadata with the new Etag
		handleGetMetadata(request, response, file);
	}

	private void handlePatchContents(HttpServletRequest request, BufferedReader requestReader, HttpServletResponse response, IFileStore file)
			throws IOException, CoreException, NoSuchAlgorithmException, JSONException, ServletException {
		JSONObject changes = OrionServlet.readJSONRequest(request);
		// read file to memory
		Reader fileReader = new InputStreamReader(file.openInputStream(EFS.NONE, null));
		StringWriter oldFile = new StringWriter();
		IOUtilities.pipe(fileReader, oldFile, true, false);
		StringBuffer oldContents = oldFile.getBuffer();
		// Remove the BOM character if it exists
		if (oldContents.length() > 0) {
			char firstChar = oldContents.charAt(0);
			if (firstChar == '\uFEFF' || firstChar == '\uFFFE') {
				oldContents.replace(0, 1, "");
			}
		}
		JSONArray changeList = changes.getJSONArray("diff");
		for (int i = 0; i < changeList.length(); i++) {
			JSONObject change = changeList.getJSONObject(i);
			long start = change.getLong("start");
			long end = change.getLong("end");
			String text = change.getString("text");
			oldContents.replace((int) start, (int) end, text);
		}

		String newContents = oldContents.toString();
		boolean failed = false;
		if (changes.has("contents")) {
			String contents = changes.getString("contents");
			if (!newContents.equals(contents)) {
				failed = true;
				newContents = contents;
			}
		}
		Writer fileWriter = new OutputStreamWriter(file.openOutputStream(EFS.NONE, null), "UTF-8");
		IOUtilities.pipe(new StringReader(newContents), fileWriter, false, true);
		if (failed) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_ACCEPTABLE,
					"Bad File Diffs. Please paste this content in a bug report: \u00A0\u00A0 	" + changes.toString(), null));
			return;
		}

		// return metadata with the new Etag
		handleGetMetadata(request, response, file);
	}

	private void handleMultiPartPut(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws IOException, CoreException,
			JSONException, NoSuchAlgorithmException {
		String typeHeader = request.getHeader(ProtocolConstants.HEADER_CONTENT_TYPE);
		String boundary = typeHeader.substring(typeHeader.indexOf("boundary=\"") + 10, typeHeader.length() - 1); //$NON-NLS-1$
		ServletInputStream requestStream = request.getInputStream();
		BufferedReader requestReader = new BufferedReader(new InputStreamReader(requestStream, "UTF-8")); //$NON-NLS-1$
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
		handlePutContents(request, requestStream, response, file);
	}

	private void handlePutMetadata(BufferedReader reader, String boundary, IFileStore file) throws IOException, CoreException, JSONException {
		StringBuffer buf = new StringBuffer();
		String line;
		while ((line = reader.readLine()) != null && !line.equals(boundary))
			buf.append(line);
		// merge with existing metadata
		FileInfo info = (FileInfo) file.fetchInfo(EFS.NONE, null);
		ServletFileStoreHandler.copyJSONToFileInfo(new JSONObject(buf.toString()), info);
		file.putInfo(info, EFS.SET_ATTRIBUTES, null);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		long time = System.currentTimeMillis();
		try {
			String fileETag = generateFileETag(file);
			if (handleIfMatchHeader(request, response, fileETag)) {
				return true;
			}
			if (handleIfNoneMatchHeader(request, response, fileETag)) {
				return true;
			}

			String parts = IOUtilities.getQueryParameter(request, "parts");
			if (parts == null || "body".equals(parts)) { //$NON-NLS-1$
				switch (getMethod(request)) {
				case DELETE:
					file.delete(EFS.NONE, null);
					break;
				case PUT:
					handlePutContents(request, request.getInputStream(), response, file);
					break;
				case POST:
					if ("PATCH".equals(request.getHeader(ProtocolConstants.HEADER_METHOD_OVERRIDE))) {
						handlePatchContents(request, request.getReader(), response, file);
					}
					break;
				default:
					return handleFileContents(request, response, file);
				}
				return true;
			}
			if ("meta".equals(parts)) { //$NON-NLS-1$
				switch (getMethod(request)) {
				case GET:
					handleGetMetadata(request, response, file);
					return true;
				case PUT:
					handlePutMetadata(request.getReader(), null, file);
					response.setStatus(HttpServletResponse.SC_NO_CONTENT);
					return true;
				default:
					return false;
				}
			}
			if ("meta,body".equals(parts) || "body,meta".equals(parts)) { //$NON-NLS-1$ //$NON-NLS-2$
				switch (getMethod(request)) {
				case GET:
					handleMultiPartGet(request, response, file);
					return true;
				case PUT:
					handleMultiPartPut(request, response, file);
					return true;
				default:
					return false;
				}
			}
		} catch (JSONException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
					"Syntax error in request", e));
		} catch (Exception e) {
			if (!handleAuthFailure(request, response, e))
				throw new ServletException(NLS.bind("Error retrieving file: {0}", file), e);
		} finally {
			response.addHeader("ServerTime", "" + (System.currentTimeMillis() - time));
		}
		return false;
	}
}
