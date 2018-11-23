/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.EncodingUtils;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GitIgnoreHandlerV1 extends ServletResourceHandler<String> {

	private final static String DOT_GIT_IGNORE = ".gitignore";
	private ServletResourceHandler<IStatus> statusHandler;

	GitIgnoreHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String pathInfo) throws ServletException {
		try {

			IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
			IPath filePath = path.hasTrailingSeparator() ? path : path.removeLastSegments(1);

			if (!AuthorizationService.checkRights(request.getRemoteUser(), "/" + filePath.toString(), request.getMethod())) {
				String msg = NLS.bind("Forbidden: {0}", EncodingUtils.encodeForHTML(filePath.toString()));
				ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, msg, null);
				return statusHandler.handleRequest(request, response, status);
			}

			/* TODO: Do not duplicate .gitignore entries. Traverse the working directory tree before adding .gitignore rules */
			Set<Entry<IPath, File>> set = GitUtils.getGitDirs(filePath, Traverse.GO_UP).entrySet();
			File gitDir = set.iterator().next().getValue();
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?

			switch (getMethod(request)) {
			case PUT:
				return handlePut(request, response, filePath);
			default:
				// fall through and return false below
			}

			return false;

		} catch (Exception e) {
			String msg = NLS.bind("Failed to process an ignore operation for {0}", EncodingUtils.encodeForHTML(pathInfo)); //$NON-NLS-1$
			ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
			LogHelper.log(status);
			return statusHandler.handleRequest(request, response, status);
		}
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, IPath filePath) throws JSONException, IOException, ServletException,
			CoreException {

		JSONObject toIgnore = OrionServlet.readJSONRequest(request);
		JSONArray paths = toIgnore.optJSONArray(ProtocolConstants.KEY_PATH);

		if (paths.length() < 1)
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST,
					"No paths to insert into .gitignore", null));

		/* remove /file */
		IFileStore projectStore = NewFileServlet.getFileStore(null, filePath.removeFirstSegments(1));

		for (int i = 0; i < paths.length(); ++i) {

			IPath path = new Path(paths.getString(i));
			IFileStore pathStore = projectStore.getFileStore(path);
			IFileStore gitignoreStore = pathStore.getParent().getFileStore(new Path(DOT_GIT_IGNORE));

			if (!pathStore.fetchInfo().exists() || !projectStore.isParentOf(gitignoreStore)) {
				String msg = NLS.bind("Invalid path: {0}", EncodingUtils.encodeForHTML(path.toString()));
				ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
				return statusHandler.handleRequest(request, response, status);
			}

			/* update the .gitignore file */
			appendGitignore(gitignoreStore, pathStore.getName());
		}

		return true;
	}

	/**
	 * Appends a single rule in the given .gitignore file store. If necessary, the .gitignore file is created.
	 * 
	 * @param gitignoreStore
	 *            The .gitignore file store.
	 * @param rule
	 *            The entry to be appended to the .gitignore.
	 * @throws CoreException
	 * @throws IOException
	 */
	private void appendGitignore(IFileStore gitignoreStore, String rule) throws CoreException, IOException {
		OutputStream out = null;
		try {
			out = gitignoreStore.openOutputStream(EFS.APPEND, null);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
			bw.write(System.getProperty("line.separator") + "/" + rule);
			bw.flush();
		} finally {
			if (out != null)
				out.close();
		}
	}
}
