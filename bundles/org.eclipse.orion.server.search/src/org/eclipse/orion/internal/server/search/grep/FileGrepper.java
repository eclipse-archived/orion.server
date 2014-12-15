/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search.grep;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.users.UserConstants2;

/**
 * @author Aidan Redpath
 */
public class FileGrepper extends DirectoryWalker<GrepResult> {
	private Pattern pattern;
	private Matcher matcher;
	private List<GrepResult> scopes;

	private SearchOptions options;

	/**
	 * The constructor for FileGrepper that sets the search options from the HTTP request and HTTP response/
	 * @param req The HTTP request to the servlet.
	 * @param resp The HTTP response from the servlet.
	 * @throws GrepException If there was a syntax error with the search term.
	 */
	public FileGrepper(HttpServletRequest req, HttpServletResponse resp, SearchOptions options) throws GrepException {
		super();
		this.options = options;
		if (options.isFileContentsSearch()) {
			pattern = buildSearchPattern();
			matcher = pattern.matcher("");
		}
		setScopes(req, resp);
	}

	private WorkspaceInfo workspace;
	private ProjectInfo project;

	/**
	 * Performs the search from the HTTP request
	 * @return A list of files which contain the search term, and pass the filename patterns.
	 * @throws GrepException If there is a problem accessing any of the files.
	 */
	public List<GrepResult> search() throws GrepException {
		List<GrepResult> files = new LinkedList<GrepResult>();
		try {
			for (GrepResult scope : scopes) {
				workspace = scope.getWorkspace();
				project = scope.getProject();
				File file = scope.getFile();
				if (!file.isDirectory()) {
					//options.addFilenamePatterns(file.getAbsolutePath());
					file = file.getParentFile();
				}

				super.walk(file, files);
			}
		} catch (IOException e) {
			throw (new GrepException(e));
		}
		return files;
	}

	/**
	 * Handles each file in the file walk.
	 */
	protected void handleFile(File file, int depth, Collection<GrepResult> results) throws IOException {
		// Check if the path is acceptable
		if (!acceptFilename(file.getName()))
			return;
		// Add if it is a filename search or search the file contents.
		if (!options.isFileContentsSearch() || searchFile(file)) {
			IFileStore fileStore;
			try {
				fileStore = EFS.getStore(file.toURI());
			} catch (CoreException e) {
				throw new IOException(e);
			}
			results.add(new GrepResult(fileStore, workspace, project));
		}
	}

	@Override
	protected boolean handleDirectory(File directory, int depth, Collection<GrepResult> results) throws IOException {
		if (directory.getName().startsWith(".")) {
			// ignore directories starting with a dot like '.git'
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Searches the contents of a file
	 * @param file The file to search
	 * @return returns whether the search was successful
	 * @throws IOException thrown if there is an error reading the file
	 */
	private boolean searchFile(File file) throws IOException {
		LineIterator lineIterator = FileUtils.lineIterator(file);
		try {
			while (lineIterator.hasNext()) {
				String line = lineIterator.nextLine();
				matcher.reset(line);
				if (matcher.find()) {
					return true;
				}
			}
		} finally {
			if (lineIterator != null)
				lineIterator.close();
		}
		return false;
	}

	/**
	 * Check if the file path is acceptable.
	 * @param filename The file path string.
	 * @return True is the file passes all the filename patterns (with wildcards)
	 */
	private boolean acceptFilename(String filename) {
		if (options.getFilenamePattern() == null) {
			return true;
		}
		String filenamePattern = options.getFilenamePattern();
		boolean match = false;
		if (options.isCaseSensitive()) {
			match = FilenameUtils.wildcardMatch(filename, filenamePattern);
		} else {
			match = FilenameUtils.wildcardMatch(filename.toLowerCase(), filenamePattern.toLowerCase());
		}
		return match;
	}

	/**
	 * Build a search pattern based on the search options.
	 * @return A new pattern of the search term.
	 * @throws GrepException If there was a syntax error with the search term.
	 */
	private Pattern buildSearchPattern() throws GrepException {
		int flags = 0;
		String searchTerm = options.getSearchTerm();
		if (!options.isRegEx()) {
			if (searchTerm.startsWith("\"")) {
				searchTerm = searchTerm.substring(1, searchTerm.length() - 1);
			}
			if (searchTerm.contains("?") || searchTerm.contains("*")) {
				if (searchTerm.startsWith("*")) {
					searchTerm = searchTerm.substring(1);
				}
				if (searchTerm.contains("?")) {
					searchTerm = searchTerm.replace('?', '.');
				}
				if (searchTerm.contains("*")) {
					searchTerm = searchTerm.replace("*", ".*");
				}
			} else {
				searchTerm = Pattern.quote(searchTerm);
			}
		}
		if (!options.isCaseSensitive()) {
			flags |= Pattern.CASE_INSENSITIVE;
		}
		/* Possible flags
		 * UNIX_LINES
		  CASE_INSENSITIVE
		 COMMENTS
		   MULTILINE LITERAL
		  DOTALL
		   UNICODE_CASE
		   CANON_E
		  UNICODE_CHARACTER_CLASS*/
		try {
			return Pattern.compile(searchTerm, flags);
		} catch (PatternSyntaxException e) {
			throw new GrepException(e);
		}
	}

	/**
	 * Set the scope of the search to the user home if the scope was not given.
	 * @param req The HTTP request to the servlet.
	 * @param resp The HTTP response from the servlet.
	 * @throws GrepException 
	 */
	private void setScopes(HttpServletRequest req, HttpServletResponse resp) throws GrepException {
		//NewFileServlet.getFileStore(req, path);
		scopes = new LinkedList<GrepResult>();
		if (!setScopeFromRequest(req, resp)) {
			setDefaultScopes(req, resp);
		}
	}

	private boolean setScopeFromRequest(HttpServletRequest req, HttpServletResponse resp) {
		try {
			String pathInfo = options.getScope();
			// Remove the file servlet prefix
			if (pathInfo != null) {
				pathInfo = pathInfo.replaceFirst("/file", "");
			}
			IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
			// prevent path canonicalization hacks
			if (pathInfo != null && !pathInfo.equals(path.toString())) {
				return false;
			}
			// don't allow anyone to mess with metadata
			if (path.segmentCount() > 0 && ".metadata".equals(path.segment(0))) { //$NON-NLS-1$
				return false;
			}
			// Must have a path
			if (path.segmentCount() == 0) {
				return false;
			}

			WorkspaceInfo workspaceInfo = OrionConfiguration.getMetaStore().readWorkspace(path.segment(0));
			if (workspaceInfo == null || workspaceInfo.getUniqueId() == null) {
				return false;
			}
			if (path.segmentCount() == 1) {
				// Bug 415700: handle path format /workspaceId 
				if (workspaceInfo != null && workspaceInfo.getUniqueId() != null) {
					addAllProjectsToScope(workspaceInfo);
					return true;
				}

				return false;
			}
			//path format is /workspaceId/projectName/[suffix]
			ProjectInfo projectInfo = OrionConfiguration.getMetaStore().readProject(workspaceInfo.getUniqueId(), path.segment(1));
			if (projectInfo != null) {
				IFileStore projectStore = projectInfo.getProjectStore();
				IFileStore scopeStore = projectStore.getFileStore(path.removeFirstSegments(2));
				GrepResult scope = new GrepResult(scopeStore, workspaceInfo, projectInfo);
				scopes.add(scope);
				return true;
			}
			// Bug 415700: handle path format /workspaceId/[file] 
			if (path.segmentCount() == 2) {
				IFileStore workspaceStore = OrionConfiguration.getMetaStore().getWorkspaceContentLocation(workspaceInfo.getUniqueId());
				IFileStore scopeStore = workspaceStore.getChild(path.segment(1));
				GrepResult scope = new GrepResult(scopeStore, workspaceInfo, null);
				scopes.add(scope);
				return true;
			}

			return false;
		} catch (CoreException e) {
			return false;
		}
	}

	/**
	 * Sets the scopes to the location of each project.
	 * @param req The request from the servlet.
	 * @param res The response to the servlet.
	 * @throws GrepException Thrown if there is an error reading a file.
	 */
	private void setDefaultScopes(HttpServletRequest req, HttpServletResponse resp) throws GrepException {
		String login = req.getRemoteUser();
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, login, false, false);
			List<String> workspaceIds = userInfo.getWorkspaceIds();
			for (String workspaceId : workspaceIds) {
				WorkspaceInfo workspaceInfo = OrionConfiguration.getMetaStore().readWorkspace(workspaceId);
				options.setDefaultScope("/file/" + workspaceId);
				addAllProjectsToScope(workspaceInfo);
			}
		} catch (CoreException e) {
			throw (new GrepException(e));
		}
	}

	private void addAllProjectsToScope(WorkspaceInfo workspaceInfo) throws CoreException {
		List<String> projectnames = workspaceInfo.getProjectNames();
		for (String projectName : projectnames) {
			ProjectInfo projectInfo = OrionConfiguration.getMetaStore().readProject(workspaceInfo.getUniqueId(), projectName);
			GrepResult scope = new GrepResult(projectInfo.getProjectStore(), workspaceInfo, projectInfo);
			scopes.add(scope);
		}
	}
}
