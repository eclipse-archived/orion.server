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
package org.eclipse.orion.server.git.servlets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.osgi.util.NLS;

/**
 * A handler for Git Index operation.
 */
public class GitIndexHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;
	private Repository db;
	private ObjectId blobId;

	GitIndexHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request,
			HttpServletResponse response, String gitPathInfo)
			throws ServletException {
		try {
			Path path = new Path(gitPathInfo);
			File gitDir = GitUtils.getGitDir(path.uptoSegment(2),
					request.getRemoteUser());
			if (gitDir == null)
				return false; // TODO: or a error response code, 405?
			db = new FileRepository(gitDir);
			DirCache cache = db.readDirCache();
			DirCacheEntry entry = cache.getEntry(path.removeFirstSegments(2)
					.toString());
			blobId = entry.getObjectId();
			IOUtilities.pipe(open(), response.getOutputStream(), true, false);
			return true;

		} catch (Exception e) {
			String msg = NLS.bind("Failed to get index for {0}", gitPathInfo); //$NON-NLS-1$
			statusHandler.handleRequest(request, response, new ServerStatus(
					IStatus.ERROR,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private InputStream open() throws IOException, CoreException,
			IncorrectObjectTypeException {
		return db.open(blobId, Constants.OBJ_BLOB).openStream();
	}

}
