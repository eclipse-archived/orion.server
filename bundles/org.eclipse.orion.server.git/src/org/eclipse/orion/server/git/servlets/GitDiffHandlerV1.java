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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.git.GitConstants;
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
			File gitDir = GitUtils.getGitDir(path.removeFirstSegments(1).uptoSegment(2), request.getRemoteUser());
			if (gitDir == null)
				return false; // TODO: or an error response code, 405?
			db = new FileRepository(gitDir);

			switch (getMethod(request)) {
				case GET :
					String parts = request.getParameter("parts"); //$NON-NLS-1$
					if (parts == null || "uris,diff".equals(parts) || "diff,uris".equals(parts))
						return handleMultiPartGet(request, response, db, path);
					if ("uris".equals(parts))
						return handleGetUris(request, response, db, path, response.getOutputStream());
					if ("diff".equals(parts))
						return handleGetDiff(request, response, db, path, response.getOutputStream());
				case POST :
					return handlePost(request, response, db, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to generate diff for {0}", gitPathInfo); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	private boolean handleGetDiff(HttpServletRequest request, HttpServletResponse response, Repository db, Path path, OutputStream out) throws Exception {
		Diff diff = new Diff(out);
		diff.setRepository(db);
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				String msg = NLS.bind("Failed to generate diff for {0}", scope); //$NON-NLS-1$
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			// TODO: decode
			diff.setOldTree(getTreeIterator(db, commits[0]));
			diff.setNewTree(getTreeIterator(db, commits[1]));
		} else {
			diff.setCached(scope.equals(GitConstants.KEY_DIFF_CACHED));
		}
		if (path.segmentCount() > 3)
			diff.setPathFilter(PathFilter.create(path.removeFirstSegments(3).toString()));
		diff.run();
		return true;
	}

	private boolean handleGetUris(HttpServletRequest request, HttpServletResponse response, Repository db, Path path, OutputStream out) throws Exception {
		JSONObject o = new JSONObject();
		JSONObject gitSection = new JSONObject();
		URI link = getURI(request);
		gitSection.put(GitConstants.KEY_DIFF, link.toString());
		gitSection.put(GitConstants.KEY_DIFF_OLD, getOldLocation(link, path).toString());
		gitSection.put(GitConstants.KEY_DIFF_NEW, getNewLocation(link, path).toString());
		o.put(GitConstants.KEY_GIT, gitSection);
		out.write(o.toString().getBytes());
		return true;
	}

	private boolean handleMultiPartGet(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws Exception {
		String boundary = createBoundaryString();
		response.setHeader(ProtocolConstants.HEADER_CONTENT_TYPE, "multipart/related; boundary=\"" + boundary + '"'); //$NON-NLS-1$
		OutputStream outputStream = response.getOutputStream();
		Writer out = new OutputStreamWriter(outputStream);

		out.write("--" + boundary + EOL); //$NON-NLS-1$
		out.write(ProtocolConstants.HEADER_CONTENT_TYPE + ": " + ProtocolConstants.CONTENT_TYPE_JSON + EOL + EOL); //$NON-NLS-1$
		out.flush();
		handleGetUris(request, response, db, path, outputStream);
		out.write(EOL + "--" + boundary + EOL); //$NON-NLS-1$
		out.write(ProtocolConstants.HEADER_CONTENT_TYPE + ": plain/text" + EOL + EOL); //$NON-NLS-1$
		out.flush();
		handleGetDiff(request, response, db, path, outputStream);
		out.write(EOL);
		out.flush();
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
				s += ".." + requestObject.getString(GitConstants.KEY_DIFF_NEW); //$NON-NLS-1$
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

	private URI getOldLocation(URI location, Path path) throws URISyntaxException {
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				// TODO:
				throw new IllegalArgumentException();
			}
			// TODO: decode commits[0]
			IPath p = new Path(GitServlet.GIT_URI + '/' + GitConstants.COMMIT_RESOURCE).append(commits[0]).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getAuthority(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			IPath p = new Path(GitServlet.GIT_URI + '/' + GitConstants.COMMIT_RESOURCE).append(Constants.HEAD).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getAuthority(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else if (scope.equals(GitConstants.KEY_DIFF_DEFAULT)) {
			IPath p = new Path(GitServlet.GIT_URI + '/' + GitConstants.INDEX_RESOURCE).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getAuthority(), p.toString(), null, null);
		}
		// TODO: shouldn't get here
		throw new IllegalArgumentException();
	}

	private URI getNewLocation(URI location, Path path) throws URISyntaxException {
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				// TODO:
				throw new IllegalArgumentException();
			}
			// TODO: decode commits[1]
			IPath p = new Path(GitServlet.GIT_URI + '/' + GitConstants.COMMIT_RESOURCE).append(commits[1]).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getAuthority(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			IPath p = new Path(GitServlet.GIT_URI + '/' + GitConstants.INDEX_RESOURCE).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getAuthority(), p.toString(), null, null);
		} else if (scope.equals(GitConstants.KEY_DIFF_DEFAULT)) {
			return new URI(location.getScheme(), location.getAuthority(), path.removeFirstSegments(1).makeAbsolute().toString(), null, null);
		}
		// TODO: shouldn't get here
		throw new IllegalArgumentException();
	}

}
