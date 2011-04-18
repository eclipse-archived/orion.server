/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
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
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;

/**
 * Servlet to handle file system access.
 */
public class NewFileServlet extends OrionServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private ServletResourceHandler<IFileStore> fileSerializer;
	private final URI rootStoreURI;

	public NewFileServlet() {
		rootStoreURI = Activator.getDefault().getRootLocationURI();
		fileSerializer = new ServletFileStoreHandler(rootStoreURI, getStatusHandler());
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		IFileStore file = getFileStore(path);
		if (file == null) {
			handleException(resp, new ServerStatus(IStatus.ERROR, 404, NLS.bind("File not found: {0}", path), null));
			return;
		}
		if (fileSerializer.handleRequest(req, resp, file))
			return;
		// finally invoke super to return an error for requests we don't know how to handle
		super.doGet(req, resp);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	/**
	 * Returns the store representing the file to be retrieved for the given
	 * request or <code>null</code> if an error occurred.
	 */
	public static IFileStore getFileStore(IPath path) {
		//first check if we have an alias registered
		if (path.segmentCount() > 0) {
			URI alias = Activator.getDefault().lookupAlias(path.segment(0));
			if (alias != null)
				try {
					return EFS.getStore(alias).getFileStore(path.removeFirstSegments(1));
				} catch (CoreException e) {
					LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, "An error occured when getting file store for path '" + path + "' and alias '" + alias + "'", e));
					// fallback is to try the same path relatively to the root
				}
		}
		//assume it is relative to the root
		URI rootStoreURI = Activator.getDefault().getRootLocationURI();
		try {
			return EFS.getStore(rootStoreURI).getFileStore(path);
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, "An error occured when getting file store for path '" + path + "' and root '" + rootStoreURI + "'", e));
			// fallback and return null
		}

		return null;
	}
}
