/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.treewalk.filter.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Diff;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A handler for Git Diff operation.
 */
public class GitDiffHandlerV1 extends AbstractGitHandler {

	/**
	 * The end of line sequence expected by HTTP.
	 */
	private static final String EOL = "\r\n"; //$NON-NLS-1$

	private HttpClient httpClient;

	GitDiffHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {
		String gitSegment = requestInfo.gitSegment;
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		String relativePath = requestInfo.relativePath;
		try {
			String parts = request.getParameter("parts"); //$NON-NLS-1$
			String pattern = relativePath;
			pattern = pattern.isEmpty() ? null : pattern;
			if (parts == null || "uris,diff".equals(parts) || "diff,uris".equals(parts)) //$NON-NLS-1$ //$NON-NLS-2$
				return handleMultiPartGet(request, response, db, gitSegment, pattern);
			if ("uris".equals(parts)) { //$NON-NLS-1$
				OrionServlet.writeJSONResponse(request, response, new Diff(getURI(request), db).toJSON());
				return true;
			}
			if ("diff".equals(parts)) //$NON-NLS-1$
				return handleGetDiff(request, response, db, gitSegment, pattern, response.getOutputStream());
			return false; // unknown part
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured while getting a diff.", e));
		}
	}

	private boolean handleGetDiff(HttpServletRequest request, HttpServletResponse response, Repository db, String scope, String pattern, OutputStream out) throws Exception {
		Git git = new Git(db);
		DiffCommand diff = git.diff();
		diff.setOutputStream(new BufferedOutputStream(out));
		AbstractTreeIterator oldTree;
		AbstractTreeIterator newTree = new FileTreeIterator(db);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				String msg = NLS.bind("Failed to generate diff for {0}", scope);
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
			}
			oldTree = getTreeIterator(db, commits[0]);
			newTree = getTreeIterator(db, commits[1]);
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			ObjectId head = db.resolve(Constants.HEAD + "^{tree}"); //$NON-NLS-1$
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

		String[] paths = request.getParameterValues(ProtocolConstants.KEY_PATH);
		TreeFilter filter = null;
		TreeFilter pathFilter = null;
		if (paths != null) {
			if (paths.length > 1) {
				Set<TreeFilter> pathFilters = new HashSet<TreeFilter>(paths.length);
				for (String path : paths) {
					pathFilters.add(PathFilter.create(path));
				}
				pathFilter = OrTreeFilter.create(pathFilters);
			} else if (paths.length == 1) {
				pathFilter = PathFilter.create(paths[0]);
			}
		}
		if (pattern != null) {
			PathFilter patternFilter = PathFilter.create(pattern);
			if (pathFilter != null)
				filter = AndTreeFilter.create(patternFilter, pathFilter);
			else
				filter = patternFilter;
		} else {
			filter = pathFilter;
		}
		if (filter != null)
			diff.setPathFilter(filter);

		diff.setOldTree(oldTree);
		diff.setNewTree(newTree);
		diff.call();
		return true;
	}

	private boolean handleMultiPartGet(HttpServletRequest request, HttpServletResponse response, Repository db, String scope, String pattern) throws Exception {
		String boundary = createBoundaryString();
		response.setHeader(ProtocolConstants.HEADER_CONTENT_TYPE, "multipart/related; boundary=\"" + boundary + '"'); //$NON-NLS-1$
		OutputStream outputStream = response.getOutputStream();
		Writer out = new OutputStreamWriter(outputStream);
		try {
			out.write("--" + boundary + EOL); //$NON-NLS-1$
			out.write(ProtocolConstants.HEADER_CONTENT_TYPE + ": " + ProtocolConstants.CONTENT_TYPE_JSON + EOL + EOL); //$NON-NLS-1$
			out.flush();
			JSONObject getURIs = new Diff(getURI(request), db).toJSON();
			JsonURIUnqualificationStrategy.ALL.run(request, getURIs);

			out.write(getURIs.toString());
			out.write(EOL + "--" + boundary + EOL); //$NON-NLS-1$
			out.write(ProtocolConstants.HEADER_CONTENT_TYPE + ": plain/text" + EOL + EOL); //$NON-NLS-1$
			out.flush();
			handleGetDiff(request, response, db, scope, pattern, outputStream);
			out.write(EOL);
			out.flush();
		} finally {
			IOUtilities.safeClose(out);
		}
		return true;
	}

	private String createBoundaryString() {
		return new UniversalUniqueIdentifier().toBase64String();
	}

	@Override
	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		String contentType = request.getHeader(ProtocolConstants.HEADER_CONTENT_TYPE);
		if (contentType.startsWith("multipart")) {//$NON-NLS-1$
			return applyPatch(request, response, db, contentType);
		} else {
			return identifyNewDiffResource(request, response);
		}
	}

	private boolean applyPatch(HttpServletRequest request, HttpServletResponse response, Repository db, String contentType) throws ServletException {
		try {
			String patch = readPatch(request.getInputStream(), contentType);
			Git git = new Git(db);
			ApplyCommand applyCommand = git.apply();
			applyCommand.setPatch(IOUtilities.toInputStream(patch));
			// TODO: ignore all errors for now, see bug 366008
			try {
				ApplyResult applyResult = applyCommand.call();
				JSONObject resp = new JSONObject();
				JSONArray modifiedFieles = new JSONArray();
				for (File file : applyResult.getUpdatedFiles()) {
					modifiedFieles.put(file.getName());
				}
				resp.put("modifiedFieles", modifiedFieles);
				return statusHandler.handleRequest(request, response, new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, resp));
				// OrionServlet.writeJSONResponse(request, response, toJSON(applyResult));
			} catch (PatchFormatException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e));
			} catch (PatchApplyException e) {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e));
			}
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when applying a patch.", e));
		}
	}

	private boolean identifyNewDiffResource(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		try {
			StringWriter writer = new StringWriter();
			IOUtilities.pipe(request.getReader(), writer, false, false);
			JSONObject requestObject = new JSONObject(writer.toString());
			URI u = getURI(request);
			IPath p = new Path(u.getPath());
			IPath np = new Path("/"); //$NON-NLS-1$
			for (int i = 0; i < p.segmentCount(); i++) {
				String s = p.segment(i);
				if (i == 2) {
					s += ".."; //$NON-NLS-1$
					s += GitUtils.encode(requestObject.getString(GitConstants.KEY_COMMIT_NEW));
				}
				np = np.append(s);
			}
			if (p.hasTrailingSeparator())
				np = np.addTrailingSeparator();
			URI nu = new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), np.toString(), u.getQuery(), u.getFragment());
			JSONObject result = new JSONObject();
			result.put(ProtocolConstants.KEY_LOCATION, nu.toString());
			OrionServlet.writeJSONResponse(request, response, result);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, resovleOrionURI(request, nu).toString());
			return true;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occured when identifying a new Diff resource.", e));
		}
	}

	//	private Object toJSON(ApplyResult applyResult) throws JSONException {
	//		JSONObject result = new JSONObject();
	//		if (applyResult.getApplyErrors().isEmpty() && applyResult.getFormatErrors().isEmpty()) {
	//			result.put(GitConstants.KEY_RESULT, "Ok");
	//		} else {
	//			if (!applyResult.getFormatErrors().isEmpty())
	//				result.put("FormatErrors", applyResult.getFormatErrors());
	//			if (!applyResult.getApplyErrors().isEmpty())
	//				result.put("ApplyErrors", applyResult.getApplyErrors());
	//		}
	//		return result;
	//	}

	private String readPatch(ServletInputStream requestStream, String contentType) throws IOException {
		//fast forward stream past multi-part header
		int boundaryOff = contentType.indexOf("boundary="); //$NON-NLS-1$
		String boundary = contentType.substring(boundaryOff + "boundary=".length(), contentType.length()); //$NON-NLS-1$
		Map<String, String> parts = IOUtilities.parseMultiPart(requestStream, boundary);
		if ("fileRadio".equals(parts.get("radio"))) //$NON-NLS-1$ //$NON-NLS-2$
			return parts.get("uploadedfile"); //$NON-NLS-1$
		if ("urlRadio".equals(parts.get("radio"))) //$NON-NLS-1$ //$NON-NLS-2$
			return fetchPatchContentFromUrl(parts.get("url")); //$NON-NLS-1$
		return null;
	}

	private String fetchPatchContentFromUrl(final String url) throws IOException {
		GetMethod m = new GetMethod(url);
		getHttpClient().executeMethod(m);
		if (m.getStatusCode() == HttpStatus.SC_OK) {
			return IOUtilities.toString(m.getResponseBodyAsStream());
		}
		return null;
	}

	private HttpClient getHttpClient() {
		if (this.httpClient == null)
			this.httpClient = new HttpClient();
		return this.httpClient;
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
