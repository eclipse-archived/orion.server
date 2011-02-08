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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;

/**
 * This class performs exports of files from the workspace.
 */
public class Export {

	private final IPath sourcePath;

	public Export(IPath path, ServletResourceHandler<IStatus> statusHandler) {
		this.sourcePath = path;
		//		this.statusHandler = statusHandler;
	}

	public void doExport(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		IFileStore source = NewFileServlet.getFileStore(sourcePath, req.getRemoteUser());

		try {
			if (source.fetchInfo().isDirectory() && source.childNames(EFS.NONE, null).length == 0) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "The folder is empty");
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
		IFileInfo info = source.fetchInfo();
		if (info.isDirectory()) {
			for (IFileStore child : source.childStores(EFS.NONE, null))
				write(child, path.append(child.getName()), zout);
		} else {
			ZipEntry entry = new ZipEntry(path.toString());
			zout.putNextEntry(entry);
			IOUtilities.pipe(source.openInputStream(EFS.NONE, null), zout, true, false);
		}
	}
}
