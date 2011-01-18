/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.servlets.OrionServlet;

/**
 * A servlet for doing imports and exports of large files.
 */
public class TransferServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	public TransferServlet() {
		super();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//initiating a new file transfer
		traceRequest(req);
		long length;
		try {
			length = Long.parseLong(req.getHeader(ProtocolConstants.HEADER_XFER_LENGTH));
		} catch (NumberFormatException e) {
			handleException(resp, "Transfer request must indicate transfer size", e, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		String options = req.getHeader(ProtocolConstants.HEADER_XFER_OPTIONS);
		if (options == null)
			options = ""; //$NON-NLS-1$
		boolean unzip = options.contains("unzip");
		String fileName = req.getHeader(ProtocolConstants.HEADER_SLUG);
		if (fileName == null && !unzip) {
			handleException(resp, "Transfer request must indicate target filename", null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		String uuid = new UniversalUniqueIdentifier().toBase64String();
		Import newImport = new Import(uuid, getStatusHandler());
		newImport.setPath(path);
		newImport.setLength(length);
		newImport.setFileName(fileName);
		newImport.setOptions(options);
		newImport.doPost(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		String id;
		//format is /xfer/import/<uuid>
		if (path.segmentCount() != 2) {
			handleException(resp, "Malformed request", null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		id = path.segment(1);
		Import importOp = new Import(id, getStatusHandler());
		importOp.doPut(req, resp);
	}
}
