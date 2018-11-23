/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.xfer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.Slug;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.servlets.OrionServlet;

/**
 * A servlet for doing imports and exports of large files.
 */
public class TransferServlet extends OrionServlet {
	/**
	 * The servlet path prefix for export operations.
	 */
	static final String PREFIX_EXPORT = "export";//$NON-NLS-1$
	/**
	 * The servlet path prefix for import operations.
	 */
	static final String PREFIX_IMPORT = "import";//$NON-NLS-1$

	private static final long serialVersionUID = 1L;

	public TransferServlet() {
		super();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		//first segment must be either "import" or "export"
		if (path.segmentCount() > 0) {
			if (PREFIX_IMPORT.equals(path.segment(0))) {
				doPostImport(req, resp);
				return;
			} else if (PREFIX_EXPORT.equals(path.segment(0))) {
				doPostExport(req, resp);
				return;
			}
		}
		//we don't know how to interpret this request
		super.doPost(req, resp);

	}

	private void doPostExport(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String optionString = req.getHeader(ProtocolConstants.HEADER_XFER_OPTIONS);
		List<String> options = getOptions(optionString);
		if (options.contains("sftp")) { //$NON-NLS-1$
			new SFTPTransfer(req, resp, getStatusHandler(), options).doTransfer();
			return;
		}
		//don't know how to handle this
		super.doPost(req, resp);
	}

	static List<String> getOptions(String optionString) {
		if (optionString == null)
			optionString = ""; //$NON-NLS-1$
		return Arrays.asList(optionString.split(",")); //$NON-NLS-1$
	}

	protected void doPostImport(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//initiating a new file transfer
		String optionString = req.getHeader(ProtocolConstants.HEADER_XFER_OPTIONS);
		List<String> options = getOptions(optionString);
		if (options.contains("sftp")) { //$NON-NLS-1$
			new SFTPTransfer(req, resp, getStatusHandler(), options).doTransfer();
			return;
		}
		String sourceURL = req.getParameter(ProtocolConstants.PARM_SOURCE);
		long length = -1;
		try {
			//length must be provided unless we are importing from another URL
			if (sourceURL == null) {
				//a chunked upload indicates the length to be uploaded in future calls
				String lengthHeader = req.getHeader(ProtocolConstants.HEADER_XFER_LENGTH);
				//a regular content length indicates the file to be uploaded is included in the post
				if (lengthHeader == null)
					lengthHeader = req.getHeader(ProtocolConstants.HEADER_CONTENT_LENGTH);
				length = Long.parseLong(lengthHeader);
			}
		} catch (NumberFormatException e) {
			handleException(resp, "Transfer request must indicate transfer size", e, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		boolean unzip = !options.contains("raw"); //$NON-NLS-1$
		String slugHeader = req.getHeader(ProtocolConstants.HEADER_SLUG);
		String fileName = Slug.decode(slugHeader);

		//if file name is not provided we can guess from the source URL
		if (fileName == null && sourceURL != null) {
			int lastSlash = sourceURL.lastIndexOf('/');
			if (lastSlash > 0)
				fileName = sourceURL.substring(lastSlash + 1);
		}

		if (fileName == null && !unzip) {
			handleException(resp, "Transfer request must indicate target filename", null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		//chop "import" segment off the front
		IPath path = new Path(req.getPathInfo()).removeFirstSegments(1);
		String uuid = new UniversalUniqueIdentifier().toBase64String();
		ClientImport newImport = new ClientImport(uuid, getStatusHandler());
		newImport.setPath(path);
		newImport.setLength(length);
		newImport.setFileName(fileName);
		try {
			if (sourceURL != null)
				newImport.setSourceURL(sourceURL);
		} catch (MalformedURLException e) {
			handleException(resp, "Invalid input URL", e, HttpServletResponse.SC_BAD_REQUEST);
		}
		newImport.setOptions(optionString);
		newImport.doPost(req, resp);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		if (path.segmentCount() >= 2) {
			if (PREFIX_EXPORT.equals(path.segment(0)) && "zip".equals(path.getFileExtension())) { //$NON-NLS-1$
				ClientExport export = new ClientExport(path.removeFirstSegments(1).removeFileExtension(), getStatusHandler());
				export.doExport(req, resp);
				return;
			}
		}
		super.doGet(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		String id;
		//format is /xfer/import/<uuid>
		if (path.segmentCount() == 2) {
			id = path.segment(1);
			ClientImport importOp = new ClientImport(id, getStatusHandler());
			importOp.doPut(req, resp);
			return;
		}
		super.doPut(req, resp);
	}

}
