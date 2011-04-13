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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;

/**
 * Implements import into workspace over SFTP.
 */
class SFTPImport {

	private final ServletResourceHandler<IStatus> statusHandler;
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	SFTPImport(HttpServletRequest req, HttpServletResponse resp, ServletResourceHandler<IStatus> statusHandler) {
		this.request = req;
		this.response = resp;
		this.statusHandler = statusHandler;
	}

	public void doImport() throws ServletException {
		String fileName = request.getHeader(ProtocolConstants.HEADER_SLUG);
		if (fileName == null) {
			handleException("Transfer request must indicate target filename", null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		String pathInfo = request.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);

	}

	private void handleException(String string, Exception exception, int httpCode) throws ServletException {
		statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, httpCode, string, exception));
	}
}