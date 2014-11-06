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
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.users.UserConstants2;

/**
 * @author Aidan Redpath
 */
public class FileGrepper extends DirectoryWalker<File> {

	private Pattern pattern;
	private Matcher matcher;
	private List<File> scopes;

	private SearchOptions options;

	/**
	 * The constructor for FileGrepper that sets the search options from the HTTP request and HTTP response/
	 * @param req The HTTP request to the servlet.
	 * @param resp The HTTP response from the servlet.
	 * @throws GrepException If there was a syntax error with the search term.
	 */
	public FileGrepper(HttpServletRequest req, HttpServletResponse resp) throws GrepException {
		super();
		options = new SearchOptions(req, resp);
		if (options.isFileSearch()) {
			pattern = buildSearchPattern();
			matcher = pattern.matcher("");
		}
		setScopes(req, resp);
	}

	/**
	 * Performs the search from the HTTP request
	 * @return A list of files which contain the search term, and pass the filename patterns.
	 * @throws GrepException If there is a problem accessing any of the files.
	 */
	public List<File> search() throws GrepException {
		List<File> files = new LinkedList<File>();
		try {
			for (File scope : scopes) {
				super.walk(scope, files);
			}
		} catch (IOException e) {
			throw (new GrepException(e));
		}
		return files;
	}

	/**
	 * Handles each file in the file walk.
	 */
	protected void handleFile(File file, int depth, Collection<File> results) throws IOException {
		// Check if the path is acceptable
		if (!acceptFilePath(file.getPath()))
			return;
		// Add if it is a filename search or search the file contents.
		if (!options.isFileSearch() || searchFile(file))
			results.add(file);
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
	 * @param path The file path string.
	 * @return True is the file passes all the filename patterns (with wildcards)
	 */
	private boolean acceptFilePath(String path) {
		if (options.getFileNamePatterns() == null)
			return true;
		for (int i = 0; i < options.getFileNamePatterns().length; i++) {
			if (!FilenameUtils.wildcardMatch(path, options.getFileNamePatterns()[i])) {
				return false;
			}
		}
		return true;
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
			searchTerm = Pattern.quote(searchTerm);
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
		scopes = new LinkedList<File>();
		String pathInfo = options.getScope();

		if (pathInfo != null) {
			pathInfo = pathInfo.replaceFirst("/file/", "");
		}
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		IFileStore file = NewFileServlet.getFileStore(req, path);
		if (file == null) {
			// Get the directories of the projects.
			String login = req.getRemoteUser();
			try {
				UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, login, false, false);
				List<String> workspaceIds = userInfo.getWorkspaceIds();
				for (String workspaceId : workspaceIds) {
					WorkspaceInfo workspace = OrionConfiguration.getMetaStore().readWorkspace(workspaceId);
					List<String> projectnames = workspace.getProjectNames();
					for (String projectName : projectnames) {
						ProjectInfo projectInfo = OrionConfiguration.getMetaStore().readProject(workspaceId, projectName);
						scopes.add(new File(projectInfo.getContentLocation()));
					}
				}
			} catch (CoreException e) {
				throw (new GrepException(e));
			}
		} else {
			scopes.add(new File(file.toURI()));
		}
	}
}
