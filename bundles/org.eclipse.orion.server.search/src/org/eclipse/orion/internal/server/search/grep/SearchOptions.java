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
package org.eclipse.orion.internal.server.search.grep;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the possible options provided to the search.
 * 
 * @author Aidan Redpath
 * @author Anthony Hunter
 */
public class SearchOptions {

	/**
	 * The default search of the filename pattern is not case sensitive.
	 */
	private boolean caseSensitive = false;

	/** 
	 * A default search location is used if the search location is not provided.
	 */
	private String defaultLocation = null;

	/**
	 * True is we have a search term that should be searched against file contents.
	 */
	private boolean fileContentsSearch = false;

	/**
	 * The filename pattern
	 */
	private String filenamePattern = null;

	/**
	 * The search location provided in the search request.
	 */
	private String location = null;

	/**
	 * True if the search term is a regular expression.
	 */
	private boolean regEx = false;

	/**
	 * The default is to limit the search to 10000 file matches.
	 */
	private int rows = 10000;

	/**
	 * The list of search scopes.
	 */
	private List<SearchScope> scopes = new ArrayList<SearchScope>();

	/**
	 * The search term used to match within file contents.
	 */
	private String searchTerm = null;

	/**
	 * The username of the user running the search.
	 */
	private String username = null;

	public String getDefaultLocation() {
		return defaultLocation;
	}

	/**
	 * Returns the filename pattern
	 */
	public String getFilenamePattern() {
		return filenamePattern;
	}

	/**
	 * Returns the location.
	 */
	public String getLocation() {
		return location;
	}

	public int getRows() {
		return rows;
	}

	public List<SearchScope> getScopes() {
		return scopes;
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

	public void setDefaultLocation(String defaultLocation) {
		this.defaultLocation = defaultLocation;
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

	public void setLocation(String location) {
		this.location = location;
	}

	public void setRegEx(boolean regEx) {
		this.regEx = regEx;
	}

	public void setRows(int rows) {
		this.rows = rows;
	}

	public void setSearchTerm(String searchTerm) {
		this.searchTerm = searchTerm;
		setFileSearch(true);
	}

	public void setUsername(String username) {
		this.username = username;
	}
}
