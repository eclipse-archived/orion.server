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
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.internal.server.servlets.workspace.WebWorkspace;
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
	}

	@Override
	public void init() throws ServletException {
		super.init();
		fileSerializer = new ServletFileStoreHandler(rootStoreURI, getStatusHandler(), getServletContext());
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		//don't allow anyone to mess with metadata
		if (path.segmentCount() > 0 && ".metadata".equals(path.segment(0))) { //$NON-NLS-1$
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, NLS.bind("Forbidden: {0}", path), null));
			return;
		}
		IFileStore file = getFileStore(path);
		if (file == null) {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("File not found: {0}", path), null));
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
		//path format is /workspaceId/projectName/[suffix]
		if (path.segmentCount() <= 1)
			return null;
		WebWorkspace workspace = WebWorkspace.fromId(path.segment(0));
		WebProject project = workspace.getProjectByName(path.segment(1));
		if (project == null)
			return null;
		try {
			return project.getProjectStore().getFileStore(path.removeFirstSegments(2));
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, NLS.bind("An error occurred when getting file store for path {0}", path), e));
			// fallback and return null
		}
		return null;
	}
}
