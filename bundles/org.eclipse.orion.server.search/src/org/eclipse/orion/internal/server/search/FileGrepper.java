/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A grep style search that walks the directories looking for files that contain occurrences of a search string.
 * 
 * @author Aidan Redpath
 * @author Anthony Hunter
 */
public class FileGrepper extends DirectoryWalker<SearchResult> {
	/**
	 * The project currently being searched by the directory walker.
	 */
	private ProjectInfo currentProject;
	/**
	 * The workspace currently being searched by the directory walker.
	 */
	private WorkspaceInfo currentWorkspace;

	private Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$

	private Matcher matcher;

	private SearchOptions options;

	private Pattern pattern;

	/**
	 * The constructor for FileGrepper
	 * @param options the search options
	 * @throws SearchException If there was a syntax error with the search term.
	 */
	public FileGrepper(SearchOptions options) throws SearchException {
		super();
		this.options = options;
		if (options.isFileContentsSearch()) {
			pattern = buildSearchPattern();
			matcher = pattern.matcher("");
		} else {
			// remove the Lucene escaped characters, see bugzilla 458450
			options.setFilenamePattern(undoLuceneEscape(options.getFilenamePattern()));
		}
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
		if (options.isFilenamePatternCaseSensitive()) {
			match = FilenameUtils.wildcardMatch(filename, filenamePattern);
		} else {
			match = FilenameUtils.wildcardMatch(filename.toLowerCase(), filenamePattern.toLowerCase());
		}
		return match;
	}

	/**
	 * Build a search pattern based on the search options.
	 * @return A new pattern of the search term.
	 * @throws SearchException If there was a syntax error with the search term.
	 */
	private Pattern buildSearchPattern() throws SearchException {
		int flags = 0;
		String searchTerm = options.getSearchTerm();
		if (!options.isRegEx()) {
			if (searchTerm.startsWith("\"")) {
				// remove the double quotes from the start and end of the search pattern
				searchTerm = searchTerm.substring(1, searchTerm.length() - 1);
			}
			// remove the Lucene escaped characters
			searchTerm = undoLuceneEscape(searchTerm);
			// change ? and * to regular expression wildcards
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
		if (!options.isSearchTermCaseSensitive()) {
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
			throw new SearchException(e);
		}
	}

	@Override
	protected boolean handleDirectory(File directory, int depth, Collection<SearchResult> results) {
		if (results.size() >= options.getRows()) {
			// stop if we already have the max number of results to return
			return false;
		} else if (directory.getName().startsWith(".")) {
			// ignore directories starting with a dot like '.git'
			return false;
		} else {
			return true;
		}
	}

	@Override
	protected void handleFile(File file, int depth, Collection<SearchResult> results) {
		if (results.size() >= options.getRows()) {
			// stop if we already have the max number of results to return
			return;
		}
		// Check if the path is acceptable
		if (!acceptFilename(file.getName()))
			return;
		// Add if it is a filename search or search the file contents.
		if (!options.isFileContentsSearch() || searchFile(file)) {
			IFileStore fileStore;
			try {
				fileStore = EFS.getStore(file.toURI());
			} catch (CoreException e) {
				logger.error("FileGrepper.handleFile: " + e.getLocalizedMessage(), e);
				return;
			}
			results.add(new SearchResult(fileStore, currentWorkspace, currentProject));
		}
	}

	/**
	 * Performs the search from the HTTP request
	 * @return A list of files which contain the search term, and pass the filename patterns.
	 * @throws SearchException If there is a problem accessing any of the files.
	 */
	public List<SearchResult> search(SearchOptions options) throws SearchException {
		List<SearchResult> files = new LinkedList<SearchResult>();
		try {
			for (SearchScope scope : options.getScopes()) {
				currentWorkspace = scope.getWorkspace();
				currentProject = scope.getProject();
				File file = scope.getFile();
				if (!file.isDirectory()) {
					file = file.getParentFile();
				}

				super.walk(file, files);
			}
		} catch (IOException e) {
			throw (new SearchException(e));
		}
		return files;
	}

	/**
	 * Searches the contents of a file
	 * @param file The file to search
	 * @return returns whether the search was successful
	 * @throws IOException thrown if there is an error reading the file
	 */
	private boolean searchFile(File file) {
		LineIterator lineIterator = null;
		try {
			lineIterator = FileUtils.lineIterator(file);
		} catch (IOException e) {
			logger.error("FileGrepper.searchFile: " + e.getLocalizedMessage());
			return false;
		}
		try {
			while (lineIterator.hasNext()) {
				String line = lineIterator.nextLine();
				if (line.contains("\0")) {
					// file contains binary content
					return false;
				}
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
	 * The Orion file client performs an operation that escapes all characters in the string that require escaping 
	 * in a Lucene queries. We need to undo since we are not Lucene. 
	 * @param searchTerm The search term with escaped characters
	 * @return the correct search term.
	 */
	private String undoLuceneEscape(String searchTerm) {
		String specialChars = "+-&|!(){}[]^\"~:\\";
		for (int i = 0; i < specialChars.length(); i++) {
			String character = specialChars.substring(i, i + 1);
			String escaped = "\\" + character;
			searchTerm = searchTerm.replaceAll(Pattern.quote(escaped), character);
		}
		return searchTerm;
	}

}
