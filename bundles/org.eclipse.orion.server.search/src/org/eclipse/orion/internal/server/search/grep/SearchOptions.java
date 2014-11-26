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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Aidan Redpath
 */
public class SearchOptions {

	private String searchTerm;
	private List<String> filenames;

	private boolean caseSensitive;
	private boolean regEx;
	private boolean fileSearch;

	private String scope;

	public SearchOptions() {
		filenames = new LinkedList<String>();
	}

	/**
	 * Creates a search option from the servlet parameters. Builds the options from the request parameter 'q'.
	 * All the search terms are seperated by a space or plus ('+') character.
	 * @param req The HTTP request to the servlet.
	 * @param resp The HTTP response from the servlet.
	 * @throws GrepException If there is a problem parsing the query data
	 */
	public SearchOptions(HttpServletRequest req, HttpServletResponse resp) throws GrepException {
		try {
			filenames = new LinkedList<String>();
			String queryString = req.getParameter("q");
			if (queryString != null)
				searchTerm = URLDecoder.decode(queryString, "UTF-8");

			fileSearch = searchTerm != null && !searchTerm.isEmpty();

			queryString = req.getParameter("qf");
			if (queryString != null) {
				String[] tempNames = URLDecoder.decode(queryString, "UTF-8").split(",");
				for (int i = 0; i < tempNames.length; i++) {
					filenames.add(tempNames[i]);
				}
			}

			queryString = req.getParameter("qs");
			if (queryString != null)
				scope = URLDecoder.decode(queryString, "UTF-8");

			queryString = req.getParameter("qr");
			regEx = queryString != null;

			queryString = req.getParameter("qcs");
			caseSensitive = queryString != null;

		} catch (UnsupportedEncodingException e) {
			throw new GrepException(e);
		}
	}

	/**
	 * Returns the search term.
	 */
	public String getSearchTerm() {
		return searchTerm;
	}

	public void setSearchTerm(String searchTerm) {
		this.searchTerm = searchTerm;
		setFileSearch(true);
	}

	/**
	 * Returns the list of filename patterns
	 */
	public List<String> getFilenamePatterns() {
		return filenames;
	}

	public void addFilenamePatterns(String filename) {
		filenames.add(filename);
	}

	/**
	 * Returns if the search should be case sensitive.
	 */
	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	public void setIsCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}

	/**
	 * Returns if the search is regex.
	 */
	public boolean isRegEx() {
		return regEx;
	}

	public void setRegEx(boolean regEx) {
		this.regEx = regEx;
	}

	/**
	 * Returns if the search needs to search the file contents
	 */
	public boolean isFileSearch() {
		return fileSearch;
	}

	public void setFileSearch(boolean fileSearch) {
		this.fileSearch = fileSearch;
	}

	/**
	 * Returns the scope.
	 */
	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}
}
