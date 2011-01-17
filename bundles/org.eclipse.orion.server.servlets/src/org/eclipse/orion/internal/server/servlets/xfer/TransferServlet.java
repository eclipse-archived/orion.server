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
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
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
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		String uuid = new UniversalUniqueIdentifier().toBase64String();
		Import newImport = new Import(uuid);
		newImport.setPath(path);
		newImport.setLength(length);
		newImport.save();
		resp.setStatus(HttpServletResponse.SC_OK);
		URI requestURI = ServletResourceHandler.getURI(req);
		String responsePath = "/" + new Path(requestURI.getPath()).segment(0) + "/import/" + uuid;
		URI responseURI;
		try {
			responseURI = new URI(requestURI.getScheme(), requestURI.getAuthority(), responsePath, null, null);
		} catch (URISyntaxException e) {
			//should not be possible
			throw new ServletException(e);
		}
		resp.setHeader("Location", responseURI.toString());
	}
}
