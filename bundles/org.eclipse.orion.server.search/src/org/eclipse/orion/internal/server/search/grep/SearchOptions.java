package org.eclipse.orion.internal.server.search.grep;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SearchOptions {

	private static final String[] FIELD_NAMES = {"Name", "Location", "RegEx", "CaseSensitive"}; //$NON-NLS-1$

	private String searchTerm;
	private String[] fileNames;

	private boolean caseSensitive;
	private boolean regEx;

	private File scope;

	/**
	 * Creates a search option from the servlet parameters. Builds the options from the request parameter 'q'.
	 * All the search terms are seperated by a space or plus ('+') character.
	 * @param req The HTTP request to the servlet.
	 * @param resp The HTTP response from the servlet.
	 */
	public SearchOptions(HttpServletRequest req, HttpServletResponse resp) {
		String queryString = req.getParameter("q");
		//divide into search terms delimited by space or plus ('+') character
		String[] terms = queryString.split("[\\s\\+]+"); //$NON-NLS-1$
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < terms.length; i++) {
			String term = terms[i];
			// If it is not a search field it is part of the search term.
			if (!isSearchField(term)) {
				try {
					sb.append(URLDecoder.decode(term, "UTF-8")); //$NON-NLS-1$
				} catch (UnsupportedEncodingException e) {

				}
				searchTerm = term;
				continue;
			}
			if (term.startsWith("Name:")) { //$NON-NLS-1$
				String cvs = getTermValue(term, "Name:");
				fileNames = cvs.split(",");
			} else if (term.startsWith("Location:")) { //$NON-NLS-1${
				String url = getTermValue(term, "Location:");
				scope = new File(url);
			} else if (term.startsWith("CaseSensitive:")) { //$NON-NLS-1$
				caseSensitive = true;
			} else if (term.startsWith("RegEx:")) { //$NON-NLS-1$
				regEx = true;
			}
		}
		searchTerm = sb.toString();
	}

	/**
	 * Gets the value from a search term. term='key'+'value'
	 * @param term The current search term.
	 * @param key The key of the term.
	 * @return All the characters after the key in the term.
	 */
	private String getTermValue(String term, String key) {
		return term.substring(key.length());
	}

	/**
	 * Checks if the current term is a search term.
	 * @param term The current search term to check.
	 * @return True if the current search term is a valid search field.
	 */
	private boolean isSearchField(String term) {
		for (String field : FIELD_NAMES) {
			if (term.startsWith(field + ":")) //$NON-NLS-1$
				return true;
		}
		return false;
	}

	/**
	 * Returns the search term.
	 */
	public String getSearchTerm() {
		return searchTerm;
	}

	/**
	 * Returns the list of filename patterns
	 */
	public String[] getFileNamePatterns() {
		return fileNames;
	}

	/**
	 * Returns if the search should be case sensitive.
	 */
	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	/**
	 * Returns if the search is regex.
	 */
	public boolean isRegEx() {
		return regEx;
	}

	/**
	 * Returns the scope.
	 */
	public File getScope() {
		return scope;
	}
}
