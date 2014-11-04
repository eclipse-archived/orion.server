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
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants2;

/**
 * @author Aidan Redpath
 */
public class FileGrepper extends DirectoryWalker<File> {

	private Pattern pattern;
	private Matcher matcher;
	private File scope;

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
		pattern = buildSearchPattern();
		matcher = pattern.matcher("");
		setScope(req, resp);
	}

	/**
	 * Performs the search from the HTTP request
	 * @return A list of files which contain the search term, and pass the filename patterns.
	 * @throws GrepException If there is a problem accessing any of the files.
	 */
	public List<File> search() throws GrepException {
		List<File> files = new LinkedList<File>();
		try {
			super.walk(scope, files);
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

		LineIterator lineIterator = FileUtils.lineIterator(file);
		try {
			while (lineIterator.hasNext()) {
				String line = lineIterator.nextLine();
				matcher.reset(line);
				if (matcher.find()) {
					results.add(file);
					return;
				}
			}
		} finally {
			if (lineIterator != null)
				lineIterator.close();
		}
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
	private void setScope(HttpServletRequest req, HttpServletResponse resp) throws GrepException {
		// Check if a scope was specified
		if (options.getScope() != null) {
			scope = options.getScope();
			return;
		}
		// Get the home dir of the user
		String login = req.getRemoteUser();
		IFileStore defaultFileStore;
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, login, false, false);
			defaultFileStore = OrionConfiguration.getMetaStore().getUserHome(userInfo.getUniqueId());
		} catch (CoreException e) {
			throw (new GrepException(e));
		}
		scope = new File(defaultFileStore.toURI());
	}
}
