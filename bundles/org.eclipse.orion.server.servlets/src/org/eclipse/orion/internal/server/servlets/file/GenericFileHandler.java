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
package org.eclipse.orion.internal.server.servlets.file;

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;

import org.eclipse.orion.internal.server.core.IOUtilities;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.osgi.util.NLS;

/**
 * Serializes IFileStore responses in a format suitable for a generic client,
 * such as a web browser.
 */
class GenericFileHandler extends ServletResourceHandler<IFileStore> {
	protected void handleFileContents(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws CoreException, IOException {
		switch (getMethod(request)) {
			case GET :
				IOUtilities.pipe(file.openInputStream(EFS.NONE, null), response.getOutputStream(), true, false);
				break;
			case PUT :
				IOUtilities.pipe(request.getInputStream(), file.openOutputStream(EFS.NONE, null), false, true);
				break;
		}
		response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore file) throws ServletException {
		// generic variant doesn't handle queries
		if (request.getQueryString() != null)
			return false;
		try {
			handleFileContents(request, response, file);
		} catch (Exception e) {
			throw new ServletException(NLS.bind("Error retrieving file: {0}", file), e);
		}
		return true;
	}
}