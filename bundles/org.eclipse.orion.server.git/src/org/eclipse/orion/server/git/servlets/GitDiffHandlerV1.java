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

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A handler for Git Diff operation.
 */
public class GitDiffHandlerV1 extends ServletResourceHandler<String> {

	/**
	 * The end of line sequence expected by HTTP.
	 */
	private static final String EOL = "\r\n"; //$NON-NLS-1$

	private ServletResourceHandler<IStatus> statusHandler;

	GitDiffHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String gitPathInfo) throws ServletException {
		Repository db = null;
		try {
			Path path = new Path(gitPathInfo);
			IPath filePath = path.hasTrailingSeparator() ? path : path.removeLastSegments(1);
			Set<Entry<IPath, File>> set = GitUtils.getGitDirs(filePath.removeFirstSegments(1), Traverse.GO_UP).entrySet();
			File gitDir = set.iterator().next().getValue();
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?
			db = new FileRepository(gitDir);

			switch (getMethod(request)) {
				case GET :
					String parts = request.getParameter("parts"); //$NON-NLS-1$
					String pattern = GitUtils.getRelativePath(path.removeFirstSegments(1), set.iterator().next().getKey());
					pattern = pattern.isEmpty() ? null : pattern;
					if (parts == null || "uris,diff".equals(parts) || "diff,uris".equals(parts)) //$NON-NLS-1$ //$NON-NLS-2$
						return handleMultiPartGet(request, response, db, path, pattern);
					if ("uris".equals(parts)) { //$NON-NLS-1$
						OrionServlet.writeJSONResponse(request, response, new org.eclipse.orion.server.git.objects.Diff(getURI(request), db).toJSON());
						return true;
					}
					if ("diff".equals(parts)) //$NON-NLS-1$
						return handleGetDiff(request, response, db, path.segment(0), pattern, response.getOutputStream());
				case POST :
					return handlePost(request, response, db, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to generate diff for {0}", gitPathInfo);
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	private boolean handleGetDiff(HttpServletRequest request, HttpServletResponse response, Repository db, String scope, String pattern, OutputStream out) throws Exception {
		DiffFormatter diff = new DiffFormatter(new BufferedOutputStream(out));
		diff.setRepository(db);
		AbstractTreeIterator oldTree;
		AbstractTreeIterator newTree = new FileTreeIterator(db);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				String msg = NLS.bind("Failed to generate diff for {0}", scope);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			// TODO: decode
			oldTree = getTreeIterator(db, commits[0]);
			newTree = getTreeIterator(db, commits[1]);
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			ObjectId head = db.resolve(Constants.HEAD + "^{tree}");
			if (head == null) {
				String msg = NLS.bind("Failed to generate diff for {0}, no HEAD", scope);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			CanonicalTreeParser p = new CanonicalTreeParser();
			ObjectReader reader = db.newObjectReader();
			try {
				p.reset(reader, head);
			} finally {
				reader.release();
			}
			oldTree = p;
			newTree = new DirCacheIterator(db.readDirCache());
		} else if (scope.equals(GitConstants.KEY_DIFF_DEFAULT)) {
			oldTree = new DirCacheIterator(db.readDirCache());
		} else {
			oldTree = getTreeIterator(db, scope);
		}

		if (pattern != null)
			diff.setPathFilter(PathFilter.create(pattern));

		diff.format(oldTree, newTree);
		diff.flush();
		return true;
	}

	private boolean handleMultiPartGet(HttpServletRequest request, HttpServletResponse response, Repository db, Path path, String pattern) throws Exception {
		String boundary = createBoundaryString();
		response.setHeader(ProtocolConstants.HEADER_CONTENT_TYPE, "multipart/related; boundary=\"" + boundary + '"'); //$NON-NLS-1$
		OutputStream outputStream = response.getOutputStream();
		Writer out = new OutputStreamWriter(outputStream);
		try {
			out.write("--" + boundary + EOL); //$NON-NLS-1$
			out.write(ProtocolConstants.HEADER_CONTENT_TYPE + ": " + ProtocolConstants.CONTENT_TYPE_JSON + EOL + EOL); //$NON-NLS-1$
			out.flush();
			JSONObject getURIs = new org.eclipse.orion.server.git.objects.Diff(getURI(request), db).toJSON();
			out.write(getURIs.toString());
			out.write(EOL + "--" + boundary + EOL); //$NON-NLS-1$
			out.write(ProtocolConstants.HEADER_CONTENT_TYPE + ": plain/text" + EOL + EOL); //$NON-NLS-1$
			out.flush();
			handleGetDiff(request, response, db, path.segment(0), pattern, outputStream);
			out.write(EOL);
			out.flush();
		} finally {
			IOUtilities.safeClose(out);
		}
		return true;
	}

	String createBoundaryString() {
		return new UniversalUniqueIdentifier().toBase64String();
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws IOException, JSONException, URISyntaxException {
		JSONObject requestObject = OrionServlet.readJSONRequest(request);
		URI u = getURI(request);
		IPath p = new Path(u.getPath());
		IPath np = new Path("/"); //$NON-NLS-1$
		for (int i = 0; i < p.segmentCount(); i++) {
			String s = p.segment(i);
			if (i == 2) {
				s += ".." + requestObject.getString(GitConstants.KEY_COMMIT_NEW); //$NON-NLS-1$
			}
			np = np.append(s);
		}
		URI nu = new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), np.toString(), u.getQuery(), u.getFragment());
		response.setHeader(ProtocolConstants.HEADER_LOCATION, nu.toString());
		response.setStatus(HttpServletResponse.SC_OK);
		return true;
	}

	private AbstractTreeIterator getTreeIterator(Repository db, String name) throws IOException {
		final ObjectId id = db.resolve(name);
		if (id == null)
			throw new IllegalArgumentException(name);
		final CanonicalTreeParser p = new CanonicalTreeParser();
		final ObjectReader or = db.newObjectReader();
		try {
			p.reset(or, new RevWalk(db).parseTree(id));
			return p;
		} finally {
			or.release();
		}
	}
}
