/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.ApplyCommand;
import org.eclipse.jgit.api.ApplyResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.jobs.DiffCommand;
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
				OrionServlet.writeJSONResponse(request, response, new Diff(getURI(request), db).toJSON(), JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			}
			if ("diff".equals(parts)) //$NON-NLS-1$
				return handleGetDiff(request, response, db, gitSegment, pattern, response.getOutputStream());
			if ("diffs".equals(parts)) //$NON-NLS-1$
				return handleGetDiffs(request, response, db, gitSegment, pattern);
			return false; // unknown part
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured while getting a diff.", e));
		}
	}

	private boolean handleGetDiffs(HttpServletRequest request, HttpServletResponse response, Repository db, String scope, String pattern) throws Exception {
		DiffCommand command = getDiff(request, response, db, scope, pattern, NullOutputStream.INSTANCE);
		if (command == null)
			return true;

		List<DiffEntry> l = command.call();
		JSONArray diffs = new JSONArray();
		URI diffLocation = getURI(request);
		if (pattern != null) {
			IPath patternPath = new Path(pattern);
			IPath diffPath = new Path(diffLocation.getPath());
			diffPath = diffPath.removeLastSegments(patternPath.segmentCount());
			diffLocation = new URI(diffLocation.getScheme(), diffLocation.getAuthority(), diffPath.toPortableString(), null, null);
		}
		URI cloneLocation = BaseToCloneConverter.getCloneLocation(diffLocation, BaseToCloneConverter.DIFF);

		int page = request.getParameter("page") != null ? new Integer(request.getParameter("page")).intValue() : 0; //$NON-NLS-1$ //$NON-NLS-2$
		int pageSize = request.getParameter("pageSize") != null ? new Integer(request.getParameter("pageSize")).intValue() : Integer.MAX_VALUE; //$NON-NLS-1$ //$NON-NLS-2$
		int start = pageSize * (page - 1);
		int end = Math.min(pageSize + start, l.size());
		int i = start;
		for (i = start; i < end; i++) {
			DiffEntry entr = l.get(i);
			JSONObject diff = new JSONObject();
			diff.put(ProtocolConstants.KEY_TYPE, org.eclipse.orion.server.git.objects.Diff.TYPE);
			diff.put(GitConstants.KEY_COMMIT_DIFF_NEWPATH, entr.getNewPath());
			diff.put(GitConstants.KEY_COMMIT_DIFF_OLDPATH, entr.getOldPath());
			diff.put(GitConstants.KEY_COMMIT_DIFF_CHANGETYPE, entr.getChangeType().toString());

			// add diff location for the commit
			String path = entr.getChangeType() != ChangeType.DELETE ? entr.getNewPath() : entr.getOldPath();
			diff.put(GitConstants.KEY_DIFF, createDiffLocation(diffLocation, path));
			diff.put(ProtocolConstants.KEY_CONTENT_LOCATION, createContentLocation(cloneLocation, entr, path));

			diffs.put(diff);
		}

		JSONObject result = new JSONObject();
		result.put(ProtocolConstants.KEY_TYPE, org.eclipse.orion.server.git.objects.Diff.TYPE);
		result.put(ProtocolConstants.KEY_CHILDREN, diffs);
		result.put(ProtocolConstants.KEY_LENGTH, l.size());
		if (i < l.size()) {
			URI nextLocation = new URI(diffLocation.getScheme(), diffLocation.getUserInfo(), diffLocation.getHost(), diffLocation.getPort(),
					diffLocation.getPath(), "pageSize=" + pageSize + "&page=" + (page + 1), diffLocation.getFragment()); //$NON-NLS-1$ //$NON-NLS-2$
			result.put(ProtocolConstants.KEY_NEXT_LOCATION, nextLocation);
		}
		OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);

		return true;
	}

	private URI createDiffLocation(URI diffLocation, String path) throws URISyntaxException {
		if (path == null)
			return diffLocation;
		IPath diffPath = new Path(diffLocation.getPath());
		diffPath = diffPath.append(path);
		return new URI(diffLocation.getScheme(), diffLocation.getAuthority(), diffPath.toString(), null, null);
	}

	private URI createContentLocation(URI cloneLocation, final DiffEntry entr, String path) throws URISyntaxException {
		// remove /gitapi/clone from the start of path
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);
		IPath result;
		if (path == null) {
			result = clonePath;
		} else {
			// need to start from the project root
			// project path is of the form /file/{workspaceId}/{projectName}
			result = clonePath.uptoSegment(3).append(path);
		}
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), result.makeAbsolute()
				.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	private boolean handleGetDiff(HttpServletRequest request, HttpServletResponse response, Repository db, String scope, String pattern, OutputStream out)
			throws Exception {
		response.setContentType(ProtocolConstants.CONTENT_TYPE_PLAIN_TEXT);
		DiffCommand command = getDiff(request, response, db, scope, pattern, new BufferedOutputStream(out));
		if (command != null)
			command.call();
		return true;
	}

	private DiffCommand getDiff(HttpServletRequest request, HttpServletResponse response, Repository db, String scope, String pattern, OutputStream out)
			throws Exception {
		boolean ignoreWS = Boolean.parseBoolean(request.getParameter("ignoreWS")); //$NON-NLS-1$
		// Git git = new Git(db);
		DiffCommand diff = new DiffCommand(db);
		diff.setOutputStream(out);
		diff.setIgnoreWhiteSpace(ignoreWS);
		AbstractTreeIterator oldTree;
		AbstractTreeIterator newTree = new FileTreeIterator(db);
		response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$
		response.setHeader("Content-Disposition", "attachment; filename=\"changes.patch\"");
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				String msg = NLS.bind("Failed to generate diff for {0}", scope);
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				return null;
			}
			oldTree = getTreeIterator(db, commits[0]);
			newTree = getTreeIterator(db, commits[1]);
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			ObjectId head = db.resolve(Constants.HEAD + "^{tree}"); //$NON-NLS-1$
			if (head == null) {
				String msg = NLS.bind("Failed to generate diff for {0}, no HEAD", scope);
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				return null;
			}
			CanonicalTreeParser p = new CanonicalTreeParser();
			ObjectReader reader = db.newObjectReader();
			try {
				p.reset(reader, head);
			} finally {
				reader.close();
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
		return diff;
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

	private String stripGlobalPaths(Repository db, String message) {
		return message.replaceAll("(?i)" + Pattern.quote(db.getDirectory().getParentFile().getAbsolutePath().toLowerCase()), "");
	}

	private boolean applyPatch(HttpServletRequest request, HttpServletResponse response, Repository db, String contentType) throws ServletException {
		try {
			String patch = readPatch(request.getInputStream(), contentType);
			Git git = Git.wrap(db);
			ApplyCommand applyCommand = git.apply();
			applyCommand.setPatch(IOUtilities.toInputStream(patch));
			// TODO: ignore all errors for now, see bug 366008
			try {
				ApplyResult applyResult = applyCommand.call();
				if (applyResult.getUpdatedFiles().size() == 0) {
					return statusHandler.handleRequest(request, response,
							new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "No files changed", null));
				}
				JSONObject resp = new JSONObject();
				JSONArray modifiedFieles = new JSONArray();
				for (File file : applyResult.getUpdatedFiles()) {
					modifiedFieles.put(stripGlobalPaths(db, file.getAbsolutePath()));
				}
				resp.put("modifiedFieles", modifiedFieles);
				return statusHandler.handleRequest(request, response, new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, resp));
				// OrionServlet.writeJSONResponse(request, response, toJSON(applyResult));
			} catch (PatchFormatException e) {
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, stripGlobalPaths(db, e.getMessage()), null));
			} catch (PatchApplyException e) {
				return statusHandler.handleRequest(request, response,
						new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, stripGlobalPaths(db, e.getMessage()), null));
			}
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when reading the patch.", e));
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
			OrionServlet.writeJSONResponse(request, response, result, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, resovleOrionURI(request, nu).toString());
			return true;
		} catch (Exception e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An error occured when identifying a new Diff resource.", e));
		}
	}

	// private Object toJSON(ApplyResult applyResult) throws JSONException {
	// JSONObject result = new JSONObject();
	// if (applyResult.getApplyErrors().isEmpty() && applyResult.getFormatErrors().isEmpty()) {
	// result.put(GitConstants.KEY_RESULT, "Ok");
	// } else {
	// if (!applyResult.getFormatErrors().isEmpty())
	// result.put("FormatErrors", applyResult.getFormatErrors());
	// if (!applyResult.getApplyErrors().isEmpty())
	// result.put("ApplyErrors", applyResult.getApplyErrors());
	// }
	// return result;
	// }

	private String readPatch(ServletInputStream requestStream, String contentType) throws IOException {
		// fast forward stream past multi-part header
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
		try {
			getHttpClient().executeMethod(m);
			if (m.getStatusCode() == HttpStatus.SC_OK) {
				return IOUtilities.toString(m.getResponseBodyAsStream());
			}
		} finally {
			m.releaseConnection();
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
			or.close();
		}
	}
}
