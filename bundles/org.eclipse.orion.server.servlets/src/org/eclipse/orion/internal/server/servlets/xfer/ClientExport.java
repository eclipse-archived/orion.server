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
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;

/**
 * This class performs exports of files from the workspace to the servlet client
 */
public class ClientExport {

	private final IPath sourcePath;
	private List<String> excludedFiles = new ArrayList<String>();

	public ClientExport(IPath path, ServletResourceHandler<IStatus> statusHandler) {
		this.sourcePath = path;
		//		this.statusHandler = statusHandler;
	}

	public void doExport(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		IFileStore source = NewFileServlet.getFileStore(req, sourcePath);
		if (req.getParameter(ProtocolConstants.PARAM_EXCLUDE) != null) {
			excludedFiles = Arrays.asList(req.getParameter(ProtocolConstants.PARAM_EXCLUDE).split(","));
		}

		try {
			if (source.fetchInfo(EFS.NONE, null).isDirectory() && source.childNames(EFS.NONE, null).length == 0) {
				resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "You cannot export an empty folder");
				return;
			}

			ZipOutputStream zout = new ZipOutputStream(resp.getOutputStream());
			write(source, Path.EMPTY, zout);
			zout.finish();
		} catch (CoreException e) {
			//we can't return an error response at this point because the output stream has been used
			throw new ServletException(e);
		}
	}

	private void write(IFileStore source, IPath path, ZipOutputStream zout) throws IOException, CoreException {
		IFileInfo info = source.fetchInfo(EFS.NONE, null);
		if (info.isDirectory()) {
			if (!path.isEmpty()) {
				ZipEntry entry = new ZipEntry(path.toString() + "/"); //$NON-NLS-1$
				zout.putNextEntry(entry);
			}
			for (IFileStore child : source.childStores(EFS.NONE, null)) {
				if (!excludedFiles.contains(child.getName())) {
					write(child, path.append(child.getName()), zout);
				}
			}
		} else {
			ZipEntry entry = new ZipEntry(path.toString());
			zout.putNextEntry(entry);
			IOUtilities.pipe(source.openInputStream(EFS.NONE, null), zout, true, false);
		}
	}
}
