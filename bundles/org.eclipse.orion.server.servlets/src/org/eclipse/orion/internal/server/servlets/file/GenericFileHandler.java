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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.HashUtilities;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.osgi.util.NLS;

/**
 * Serializes IFileStore responses in a format suitable for a generic client,
 * such as a web browser.
 */
class GenericFileHandler extends ServletResourceHandler<IFileStore> {
	private final ServletContext context;

	public GenericFileHandler(ServletContext context) {
		this.context = context;
	}

	protected void handleFileContents(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws CoreException, IOException, NoSuchAlgorithmException {
		String receivedETag = request.getHeader("If-Match");
		if (receivedETag != null && !receivedETag.equals(generateFileETag(file))) {
			response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
			return;
		}
		switch (getMethod(request)) {
			case GET :
				IOUtilities.pipe(file.openInputStream(EFS.NONE, null), response.getOutputStream(), true, false);
				response.setHeader(ProtocolConstants.HEADER_CONTENT_TYPE, context.getMimeType(file.getName()));
				break;
			case PUT :
				IOUtilities.pipe(request.getInputStream(), file.openOutputStream(EFS.NONE, null), false, true);
				break;
		}
		response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
		response.setHeader(ProtocolConstants.KEY_ETAG, generateFileETag(file));
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		// generic variant doesn't handle queries
		// acceptable to just ignore query parameters we do not understand
		//	if (request.getQueryString() != null)
		//		return false;
		try {
			handleFileContents(request, response, file);
		} catch (Exception e) {
			if (!handleAuthFailure(request, response, e))
				throw new ServletException(NLS.bind("Error retrieving file: {0}", file), e);
		}
		return true;
	}

	/**
	 * Returns an ETag calculated using SHA-1 hash function.
	 */
	public static String generateFileETag(IFileStore file) throws NoSuchAlgorithmException, IOException, CoreException {
		return HashUtilities.getHash(file.openInputStream(EFS.NONE, null), true, HashUtilities.SHA_1);
	}
}