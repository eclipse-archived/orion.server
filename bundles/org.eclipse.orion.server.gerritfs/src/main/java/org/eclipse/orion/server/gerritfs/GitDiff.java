/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.server.gerritfs;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.eclipse.orion.server.gerritfs.GitConstants;
import org.eclipse.orion.server.gerritfs.IOUtilities;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Export("/diff/*")
@Singleton
public class GitDiff extends HttpServlet {
	static final String KEY_LOCATION = "Location"; //$NON-NLS-1$
	static final String KEY_PATH = "Path"; //$NON-NLS-1$
	static final String KEY_TYPE = "Type"; //$NON-NLS-1$
	static final String KEY_CHILDREN = "Children"; //$NON-NLS-1$
	static final String KEY_LENGTH = "Length"; //$NON-NLS-1$
	static final String KEY_CONTENT_LOCATION = "ContentLocation"; //$NON-NLS-1$
	static final String KEY_NEXT_LOCATION = "NextLocation"; //$NON-NLS-1$
	static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";//$NON-NLS-1$
	static final String TYPE = "Diff"; //$NON-NLS-1$

	private static final long serialVersionUID = 8817903877306987723L;
	private final GitRepositoryManager repoManager;
	private final ProjectControl.Factory projControlFactory;
	private final Provider<WebSession> session;
	private final AccountCache accountCache;
	private final Config config;
	private final AccountManager accountManager;

	private Repository repo;

	private static Logger log = LoggerFactory.getLogger(GitDiff.class);

	@Inject
	public GitDiff(final GitRepositoryManager repoManager,
			final ProjectControl.Factory project, Provider<WebSession> session,
			AccountCache accountCache, @GerritServerConfig Config config,
			final AccountManager accountManager) {
		this.repoManager = repoManager;
		this.projControlFactory = project;
		this.session = session;
		this.accountCache = accountCache;
		this.config = config;
		this.accountManager = accountManager;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		handleAuth(req);
		identifyNewDiffResource(req, resp);
	}

	private boolean identifyNewDiffResource(HttpServletRequest request,
			HttpServletResponse response) throws ServletException {
		try {
			StringWriter writer = new StringWriter();
			IOUtilities.pipe(request.getReader(), writer, false, false);
			JSONObject requestObject = new JSONObject(writer.toString());
			//URI u = getURI(request);
			String path = request.getServletPath();
			String pathInfo = request.getPathInfo();
			if (pathInfo != null) {
				path += request.getPathInfo();
			}
			String[] p = path.split("/"); //$NON-NLS-1$
			StringBuffer np = new StringBuffer(); //$NON-NLS-1$
			for (int i = 0; i < p.length; i++) {
				String s = p[i];
				if (i == 2) {
					s += ".."; //$NON-NLS-1$
					s += encode(((String) requestObject
							.get(GitConstants.KEY_COMMIT_NEW)));
				}
				np = np.append(s);
				np.append("/");
			}
			if (p[p.length - 1].equals("/"))
				np = np.append("/");
			URI nu = new URI(request.getScheme(), null, np.toString(), null, null);
			JSONObject result = new JSONObject();
			result.put(KEY_LOCATION, nu.toString());
			writeJSONResponse(request, response, result);
			// OrionServlet.writeJSONResponse(request, response, result,
			// JsonURIUnqualificationStrategy.ALL_NO_GIT);
			// response.setHeader(ProtocolConstants.HEADER_LOCATION,
			// resovleOrionURI(request, nu).toString());
			return true;
		} catch (Exception e) {
		}
		return false;
	}

	void writeJSONResponse(HttpServletRequest req, HttpServletResponse resp,
			JSONObject result) throws IOException {
		// Assert.isLegal(result instanceof JSONObject || result instanceof
		// JSONArray);
		resp.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
		resp.setHeader("Cache-Control", "no-store"); //$NON-NLS-1$ //$NON-NLS-2$
		// if (result instanceof JSONObject) {
		// decorateResponse(req, (JSONObject) result);
		// }
		// if (strategy == null) {
		// strategy = JsonURIUnqualificationStrategy.ALL;
		// }
		IURIUnqualificationStrategy strategy = JsonURIUnqualificationStrategy.ALL_NO_GIT;
		strategy.run(req, result);

		// TODO look at accept header and chose appropriate response
		// representation
		resp.setContentType(CONTENT_TYPE_JSON);
		String response;
		try {
			response = result.toString(2);
			resp.getWriter().print(response);
		} catch (JSONException e) {
		}
	}

	protected static URI unqualifyURI(URI uri, String scheme, String hostname,
			int port) {
		URI simpleURI = uri;
		int uriPort = uri.getPort();
		if (uriPort == -1) {
			uriPort = getDefaultPort(uri.getScheme());
		}
		if (scheme.equals(uri.getScheme()) && hostname.equals(uri.getHost())
				&& port == uriPort) {
			try {
				simpleURI = new URI(null, null, null, -1, uri.getPath(),
						uri.getQuery(), uri.getFragment());
			} catch (URISyntaxException e) {
				simpleURI = uri;
			}
		}
		return simpleURI;
	}

	private static int getDefaultPort(String scheme) {
		if ("http".equalsIgnoreCase(scheme)) {
			return 80;
		}
		if ("https".equalsIgnoreCase(scheme)) {
			return 443;
		}
		return -1;
	}

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		handleAuth(request);
		try {
			//
			String parts = request.getParameter("parts"); //$NON-NLS-1$
			String pathInfo = request.getPathInfo();
			Pattern ptrn = Pattern.compile("/([^/]*)(?:/([^/]*)(?:/(.*))?)?");
			Matcher matcher = ptrn.matcher(pathInfo);
			matcher.matches();
			String sha = null;
			String projectName = null;
			String refName = null;
			if (matcher.groupCount() > 0) {
				sha = matcher.group(1);
				projectName = matcher.group(2);
				refName = matcher.group(3);
				if (sha == null || sha.equals("")) {
					sha = null;
				} else {
					sha = java.net.URLDecoder.decode(sha,
							"UTF-8");
				}
				if (projectName == null || projectName.equals("")) {
					projectName = null;
				} else {
					projectName = java.net.URLDecoder.decode(projectName,
							"UTF-8");
				}
				if (refName == null || refName.equals("")) {
					refName = null;
				} else {
					refName = java.net.URLDecoder.decode(refName, "UTF-8");
				}
			}
			NameKey projName = NameKey.parse(projectName);
			repo = repoManager.openRepository(projName);
			//
			if ("uris".equals(parts)) { //$NON-NLS-1$
			writeJSONResponse(request, response, getDiff(request));
				return;
			}
			if ("diff".equals(parts)) {//$NON-NLS-1$
				handleGetDiff(request, response, repo, sha, refName,
						response.getOutputStream());

			} else {
				handleGetDiffs(request, response, repo, sha, refName,
						NullOutputStream.INSTANCE);
			}
			//			if ("diffs".equals(parts)) //$NON-NLS-1$
			// return handleGetDiffs(request, response, db, gitSegment,
			// pattern);
		} catch (Exception e) {

		}
		return; // unknown part

	}
	
	protected JSONObject getDiff(HttpServletRequest request) {
		URI uri = getURI(request);
		String pathInfo = uri.getPath();
		pathInfo = pathInfo.substring(pathInfo.indexOf("/diff"));
		Pattern ptrn = Pattern.compile("/([^/]*)(?:/([^/]*)(?:/(.*))?)?");
		Matcher matcher = ptrn.matcher(pathInfo);
		matcher.matches();
		String sha = null;
		String scope = null;
		String refName = null;
		if (matcher.groupCount() > 0) {
//			sha = matcher.group(1);
			scope = matcher.group(2);
			refName = matcher.group(3);
		}
		JSONObject json = new JSONObject();
			if (scope.contains("..")) { //$NON-NLS-1$
				String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
//						if (commits.length != 2) {
//							throw new IllegalArgumentException(NLS.bind("Illegal scope format, expected {old}..{new}, was {0}", scope));
//						}
				IPath oldLocation = new Path(request.getContextPath() + "/gitcontents/").append(commits[0]).append("/" + refName);
				IPath newLocation = new Path(request.getContextPath() + "/gitcontents/").append(commits[1]).append("/" + refName);
				
				try {
					json.put("New", newLocation.toString());
					json.put("Old", oldLocation.toString());
					json.put("Type", "Diff");
				} catch (JSONException e) {
				}
				
			}
		return json;
	}

	private void handleGetDiff(HttpServletRequest request,
			HttpServletResponse response, Repository db, String scope,
			String pattern, OutputStream out) throws Exception {
		DiffCommand command = getDiff(request, response, db, scope, pattern,
				new BufferedOutputStream(out));
		if (command != null)
			command.call();
	}

	protected void handleGetDiffs(HttpServletRequest request,
			HttpServletResponse response, Repository repo, String sha,
			String refName, OutputStream out) {
		try {

			DiffCommand command = getDiff(request, response, repo, sha,
					refName, out);
			if (command == null)
				return;

			List<DiffEntry> l = command.call();
			JSONArray diffs = new JSONArray();
			URI diffLocation = getURI(request);
			if (refName != null) {
				IPath patternPath = new Path(refName);
				IPath diffPath = new Path(diffLocation.getPath());
				diffPath = diffPath.removeLastSegments(patternPath
						.segmentCount());
				diffLocation = new URI(diffLocation.getScheme(),
						diffLocation.getAuthority(),
						diffPath.toPortableString(), null, null);
			}
			URI cloneLocation = getCloneLocation(diffLocation);

			int page = request.getParameter("page") != null ? new Integer(request.getParameter("page")).intValue() : 0; //$NON-NLS-1$ //$NON-NLS-2$
			int pageSize = request.getParameter("pageSize") != null ? new Integer(request.getParameter("pageSize")).intValue() : Integer.MAX_VALUE; //$NON-NLS-1$ //$NON-NLS-2$
			int start = pageSize * (page - 1);
			int end = Math.min(pageSize + start, l.size());
			int i = start;
			for (i = start; i < end; i++) {
				DiffEntry entr = l.get(i);
				JSONObject diff = new JSONObject();
				diff.put(KEY_TYPE, TYPE);
				diff.put(GitConstants.KEY_COMMIT_DIFF_NEWPATH,
						entr.getNewPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_OLDPATH,
						entr.getOldPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_CHANGETYPE, entr
						.getChangeType().toString());

				// add diff location for the commit
				String path = entr.getChangeType() != ChangeType.DELETE ? entr
						.getNewPath() : entr.getOldPath();
				diff.put(GitConstants.KEY_DIFF,
						createDiffLocation(diffLocation, request, path));
				diff.put(KEY_CONTENT_LOCATION,
						createContentLocation(cloneLocation, entr, path));

				diffs.put(diff);
			}

			JSONObject result = new JSONObject();
			result.put(KEY_TYPE, TYPE);
			result.put(KEY_CHILDREN, diffs);
			result.put(KEY_LENGTH, l.size());
			if (i < l.size()) {
				URI nextLocation = new URI(
						diffLocation.getScheme(),
						diffLocation.getUserInfo(),
						diffLocation.getHost(),
						diffLocation.getPort(),
						diffLocation.getPath(),
						"pageSize=" + pageSize + "&page=" + (page + 1), diffLocation.getFragment()); //$NON-NLS-1$ //$NON-NLS-2$
				result.put(KEY_NEXT_LOCATION, nextLocation);
			}
			writeJSONResponse(request, response, result);

		} catch (Exception e) {
			// return statusHandler.handleRequest(request, response, new
			// ServerStatus(IStatus.ERROR,
			// HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
			// "An error occured while getting a diff.", e));
			System.out.println(e);
		} finally {
			if (repo != null) {
				repo.close();
			}
		}
	}

	private URI getCloneLocation(URI diffLocation) {
		return null;
	}

	private URI createDiffLocation(URI diffLocation, HttpServletRequest request, String path)
			throws URISyntaxException {
		if (path == null)
			return diffLocation;
		IPath diffPath = new Path(request.getContextPath() + "/" 
				+ diffLocation.getPath());
		diffPath = diffPath.append(path);
		return new URI(diffLocation.getScheme(), diffLocation.getAuthority(),
				diffPath.toString(), null, null);
	}

	private URI createContentLocation(URI cloneLocation, final DiffEntry entr,
			String path) throws URISyntaxException {
		// // remove /gitapi/clone from the start of path
		// //IPath clonePath = new Path("contents/" + projectName + "/" +
		// refName + "/" + path);
		// IPath result;
		// if (path == null) {
		// result = clonePath;
		// } else {
		// // need to start from the project root
		// // project path is of the form /file/{workspaceId}/{projectName}
		// result = clonePath.uptoSegment(3).append(path);
		// }
		// return new URI(cloneLocation.getScheme(),
		// cloneLocation.getUserInfo(), cloneLocation.getHost(),
		// cloneLocation.getPort(), result.makeAbsolute()
		// .toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
		return new URI("orion", "hub.jazz.net", "", "");
	}

	private DiffCommand getDiff(HttpServletRequest request,
			HttpServletResponse response, Repository db, String scope,
			String pattern, OutputStream out) throws Exception {
		boolean ignoreWS = Boolean.parseBoolean(request
				.getParameter("ignoreWS")); //$NON-NLS-1$
		// Git git = new Git(db);
		DiffCommand diff = new DiffCommand(db);
		diff.setOutputStream(out);
		diff.setIgnoreWhiteSpace(ignoreWS);
		AbstractTreeIterator oldTree;
		AbstractTreeIterator newTree = null;// = new
											// FileTreeIterator(currentTree.getT);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				// String msg = NLS.bind("Failed to generate diff for {0}",
				// scope);
				// statusHandler.handleRequest(request, response, new
				// ServerStatus(IStatus.ERROR,
				// HttpServletResponse.SC_BAD_REQUEST, msg, null));
				return null;
			}
			
			oldTree = getTreeIterator(db, java.net.URLDecoder.decode(commits[0],"UTF-8"));
			newTree = getTreeIterator(db, java.net.URLDecoder.decode(commits[1],"UTF-8"));
		} else {
			oldTree = getTreeIterator(db, scope); 
		}

		String[] paths = request.getParameterValues(GitDiff.KEY_PATH);
		TreeFilter filter = null;
		TreeFilter pathFilter = null;
		if (paths != null) {
			if (paths.length > 1) {
				Set<TreeFilter> pathFilters = new HashSet<TreeFilter>(
						paths.length);
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
		if (newTree != null)
			diff.setNewTree(newTree);
		return diff;
	}

	private void handleAuth(HttpServletRequest req) {
		String username = req.getRemoteUser();
		if (username != null) {
			if (config.getBoolean("auth", "userNameToLowerCase", false)) {
				username = username.toLowerCase(Locale.US);
			}
			log.debug("User name: " + username);
			AccountState who = accountCache.getByUsername(username);
			log.debug("AccountState " + who);
			if (who == null
					&& username
							.matches("^([a-zA-Z0-9][a-zA-Z0-9._-]*[a-zA-Z0-9]|[a-zA-Z0-9])$")) {
				log.debug("User is not registered with Gerrit. Register now."); // This
																				// approach
																				// assumes
																				// an
																				// auth
																				// type
																				// of
																				// HTTP_LDAP
				final AuthRequest areq = AuthRequest.forUser(username);
				try {
					accountManager.authenticate(areq);
					who = accountCache.getByUsername(username);
					if (who == null) {
						log.warn("Unable to register user \"" + username
								+ "\". Continue as anonymous.");
					} else {
						log.debug("User registered.");
					}
				} catch (AccountException e) {
					log.warn("Exception registering user \"" + username
							+ "\". Continue as anonymous.", e);
				}
			}
			if (who != null && who.getAccount().isActive()) {
				log.debug("Not anonymous user");
				WebSession ws = session.get();
				ws.setUserAccountId(who.getAccount().getId());
				ws.setAccessPathOk(AccessPath.REST_API, true);
			} else {
				log.debug("Anonymous user");
			}
		}
	}

	public static String getImageLink(String emailAddress) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			// without MD5 we can't compute gravatar hashes
			return null;
		}
		digest.update(emailAddress.trim().toLowerCase().getBytes());
		byte[] digestValue = digest.digest();
		StringBuffer result = new StringBuffer(
				"https://www.gravatar.com/avatar/"); //$NON-NLS-1$
		for (int i = 0; i < digestValue.length; i++) {
			String current = Integer.toHexString((digestValue[i] & 0xFF));
			// left pad with zero
			if (current.length() == 1)
				result.append('0');
			result.append(current);
		}
		// Default to "mystery man" icon if the user has no gravatar, and use a
		// 40 pixel image
		result.append("?d=mm"); //$NON-NLS-1$
		return result.toString();
	}

	/**
	 * Convenience method to obtain the URI of the request
	 */
	public static URI getURI(HttpServletRequest request) {
		String path = request.getServletPath();
		String pathInfo = request.getPathInfo();
		if (pathInfo != null) {
			path += request.getPathInfo();
		}
		try {
			path = java.net.URLDecoder.decode(path,
					"UTF-8");
			// Note: no query string!
			return new URI(request.getScheme(), null, path, null, null);
		} catch (URISyntaxException e) {
			// location not properly encoded
			return null;
		} catch (UnsupportedEncodingException e) {
			// location not properly encoded
			return null;
		}
	}

	public static String encode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			// should never happen since "UTF-8" is used
		}
		return s;
	}

	private AbstractTreeIterator getTreeIterator(Repository db, String name)
			throws IOException {
		final ObjectId id = db.resolve(name);
		if (id == null)
			throw new IllegalArgumentException(name);
		final CanonicalTreeParser p = new CanonicalTreeParser();
		final ObjectReader or = db.newObjectReader();
		RevWalk rw = new RevWalk(db);
		try {
			p.reset(or, rw.parseTree(id));
			return p;
		} finally {
			or.release();
			rw.release();
		}
	}

}
