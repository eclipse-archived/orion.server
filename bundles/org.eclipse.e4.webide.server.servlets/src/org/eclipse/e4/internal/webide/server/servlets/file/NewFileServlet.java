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
package org.eclipse.e4.internal.webide.server.servlets.file;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.e4.internal.webide.server.IAliasRegistry;
import org.eclipse.e4.internal.webide.server.servlets.*;
import org.eclipse.e4.webide.server.LogHelper;
import org.eclipse.e4.webide.server.servlets.EclipseWebSecureServlet;
import org.eclipse.osgi.util.NLS;

/**
 * Servlet to handle file system access.
 */
public class NewFileServlet extends EclipseWebSecureServlet {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private ServletResourceHandler<IFileStore> fileSerializer;
	private final URI rootStoreURI;

	private IAliasRegistry aliasRegistry;

	public NewFileServlet() {
		aliasRegistry = Activator.getDefault();
		rootStoreURI = Activator.getDefault().getRootLocationURI();
		fileSerializer = new ServletFileStoreHandler(rootStoreURI, getStatusHandler());
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		IFileStore file = getFileStore(path, req.getRemoteUser());
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
		traceRequest(req);
		IPath path = new Path(req.getPathInfo());
		IFileStore file = getFileStore(path, req.getRemoteUser());
		if (file == null) {
			handleException(resp, new ServerStatus(IStatus.ERROR, 404, NLS.bind("File not found: {0}", path), null));
			return;
		}
		if (fileSerializer.handleRequest(req, resp, file))
			return;
		// finally invoke super to return an error for requests we don't know how to handle
		super.doDelete(req, resp);
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
	protected IFileStore getFileStore(IPath path, String authority) {
		//first check if we have an alias registered
		if (path.segmentCount() > 0) {
			URI alias = aliasRegistry.lookupAlias(path.segment(0));
			if (alias != null)
				try {
					return EFS.getStore(Util.getURIWithAuthority(alias, authority)).getFileStore(path.removeFirstSegments(1));
				} catch (CoreException e) {
					LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, "An error occured when getting file store for path '" + path + "' and alias '" + alias + "'", e));
					// fallback is to try the same path relatively to the root
				}
		}
		//assume it is relative to the root
		try {
			return EFS.getStore(Util.getURIWithAuthority(rootStoreURI, authority)).getFileStore(path);
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, "An error occured when getting file store for path '" + path + "' and root '" + rootStoreURI + "'", e));
			// fallback and return null
		}

		return null;
	}
}
