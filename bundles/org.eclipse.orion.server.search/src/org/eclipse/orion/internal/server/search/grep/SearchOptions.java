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

/**
 * @author Aidan Redpath
 */
public class SearchOptions {

	private boolean caseSensitive = false;
	private String defaultScope = null;

	private boolean fileContentsSearch = false;
	private String filenamePattern = null;
	private boolean regEx = false;

	private String scope = null;
	private String searchTerm = null;
	private String username = null;

	public String getDefaultScope() {
		return defaultScope;
	}

	/**
	 * Returns the filename pattern
	 */
	public String getFilenamePattern() {
		return filenamePattern;
	}

	/**
	 * Returns the scope.
	 */
	public String getScope() {
		return scope;
	}

	/**
	 * Returns the search term.
	 */
	public String getSearchTerm() {
		return searchTerm;
	}

	public String getUsername() {
		return username;
	}

	/**
	 * Returns if the search should be case sensitive.
	 */
	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	/**
	 * Returns if the search needs to search the file contents
	 */
	public boolean isFileContentsSearch() {
		return fileContentsSearch;
	}

	/**
	 * Returns if the search is regex.
	 */
	public boolean isRegEx() {
		return regEx;
	}

	public void setDefaultScope(String defaultScope) {
		this.defaultScope = defaultScope;
	}

	public void setFilenamePattern(String pattern) {
		filenamePattern = pattern;
	}

	public void setFileSearch(boolean fileSearch) {
		this.fileContentsSearch = fileSearch;
	}

	public void setIsCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}

	public void setRegEx(boolean regEx) {
		this.regEx = regEx;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public void setSearchTerm(String searchTerm) {
		this.searchTerm = searchTerm;
		setFileSearch(true);
	}

	public void setUsername(String username) {
		this.username = username;
	}
}
