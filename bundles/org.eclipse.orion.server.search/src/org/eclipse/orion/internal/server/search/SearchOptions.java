/*******************************************************************************
 * Copyright (c) 2014, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Represents the possible options provided to the search.
 * 
 * @author Aidan Redpath
 * @author Anthony Hunter
 */
public class SearchOptions {

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
	 * The list of file / folder names to exclude while searching
	 * @since 13.0
	 */
	private HashSet<String> exclude = new HashSet<String>();

	/**
	 * The default search of the filename pattern is not case sensitive.
	 */
	private boolean filenamePatternCaseSensitive = false;

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
	 * The default search term is not case sensitive.
	 */
	private boolean searchTermCaseSensitive = false;

	/**
	 * The username of the user running the search.
	 */
	private String username = null;
	
	/**
	 * The default search is not whole word.
	 */
	private boolean searchWholeWord = false;

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
	 * Returns if the given file / folder name is to be excluded from searching
	 * @param name The name of the file or folder to check
	 * @return <code>true</code> if it should be excluded, <code>false</code> otherwise
	 * @since 13.0
	 */
	public boolean isExcluded(String name) {
		if(this.exclude.contains(name)) {
			for (String ex : this.exclude) {
				if(ex.matches(name)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Returns if the search needs to search the file contents
	 */
	public boolean isFileContentsSearch() {
		return fileContentsSearch;
	}

	/**
	 * Returns if the search should be case sensitive.
	 */
	public boolean isFilenamePatternCaseSensitive() {
		return filenamePatternCaseSensitive;
	}

	/**
	 * Returns if the search is regex.
	 */
	public boolean isRegEx() {
		return regEx;
	}

	/**
	 * Returns if the search should be case sensitive.
	 */
	public boolean isSearchTermCaseSensitive() {
		return searchTermCaseSensitive;
	}

	/**
	 * Returns if the search should be for whole word.
	 */
	public boolean isSearchWholeWord() {
		return searchWholeWord;
	}
	
	public void setDefaultLocation(String defaultLocation) {
		this.defaultLocation = defaultLocation;
	}

	public void setFilenamePattern(String pattern) {
		filenamePattern = pattern;
	}
	/**
	 * Sets the file / folder name to be excluded while searching
	 * @param name The name of the file / folder to exclude
	 * @since 13.0
	 */
	public void setExcluded(String name) {
		this.exclude.add(name);
	}
	
	public void setFileSearch(boolean fileSearch) {
		this.fileContentsSearch = fileSearch;
	}

	public void setIsFilenamePatternCaseSensitive(boolean filenamePatternCaseSensitive) {
		this.filenamePatternCaseSensitive = filenamePatternCaseSensitive;
	}

	public void setIsSearchTermCaseSensitive(boolean searchTermCaseSensitive) {
		this.searchTermCaseSensitive = searchTermCaseSensitive;
	}

	public void setIsSearchWholeWord(boolean searchWholeWord) {
		this.searchWholeWord = searchWholeWord;
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
