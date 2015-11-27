/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
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
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.HashUtilities;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
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

	protected boolean handleFileContents(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws CoreException, IOException, NoSuchAlgorithmException {
		switch (getMethod(request)) {
			case GET :
				setCacheHeaders(response, file);
				response.setHeader(ProtocolConstants.HEADER_CONTENT_TYPE, context.getMimeType(file.getName()));
				response.setHeader(ProtocolConstants.HEADER_ACCEPT_PATCH, ProtocolConstants.CONTENT_TYPE_JSON_PATCH);
				IOUtilities.pipe(file.openInputStream(EFS.NONE, null), response.getOutputStream(), true, false);
				break;
			case PUT :
				setCacheHeaders(response, file);
				IOUtilities.pipe(request.getInputStream(), file.openOutputStream(EFS.NONE, null), false, true);
				break;
			default :
				return false;
		}
		return true;
	}

	private void setCacheHeaders(HttpServletResponse response, IFileStore file) throws NoSuchAlgorithmException, IOException, CoreException {
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
			String fileETag = generateFileETag(file);
			if (handleIfMatchHeader(request, response, fileETag)) {
				return true;
			}
			if (handleIfNoneMatchHeader(request, response, fileETag)) {
				return true;
			}

			return handleFileContents(request, response, file);
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
		//give empty ETag if file does not exist
		if (!file.fetchInfo().exists())
			return ""; //$NON-NLS-1$
		return HashUtilities.getHash(file.openInputStream(EFS.NONE, null), true, HashUtilities.SHA_1);
	}

	/**
	 * Handles If-Match header precondition
	 *
	 * @param request The HTTP request object
	 * @param response The servlet response object
	 * @param etag The file's ETag
	 * @return {@code true} if the If-Match header precondition failed (doesn't match the file's ETag), {@code false} otherwise
	 */
	protected boolean handleIfMatchHeader(HttpServletRequest request, HttpServletResponse response, String etag) {
		String ifMatchHeader = request.getHeader(ProtocolConstants.HEADER_IF_MATCH);
		if (ifMatchHeader != null && !ifMatchHeader.equals(etag)) {
			response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
			return true;
		}
		return false;
	}

	/**
	 * Handles If-None-Match header precondition
	 *
	 * @param request The HTTP request object
	 * @param response The servlet response object
	 * @param etag The file's ETag
	 * @return {@code true} if the If-None-Match header precondition failed (matches the file's ETag), {@code false} otherwise
	 */
	protected boolean handleIfNoneMatchHeader(HttpServletRequest request, HttpServletResponse response, String etag) {
		String ifNoneMatchHeader = request.getHeader(ProtocolConstants.HEADER_IF_NONE_MATCH);
		if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etag)) {
			switch (getMethod(request)) {
				case HEAD :
					// fall through
				case GET :
					response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					break;
				case DELETE : //see Bug 450014
					return false;
				default :
					response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
					break;
			}
			response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
			response.setHeader(ProtocolConstants.KEY_ETAG, etag);
			return true;
		}
		return false;
	}
}