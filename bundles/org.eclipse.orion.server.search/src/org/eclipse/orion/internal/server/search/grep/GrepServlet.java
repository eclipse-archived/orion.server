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
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.common.params.CommonParams;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Aidan Redpath
 */
public class GrepServlet extends OrionServlet {

	private static final long serialVersionUID = 1L;

	private static final String FIELD_NAMES = "Name,NameLower,Length,Directory,LastModified,Location,Path"; //$NON-NLS-1$
	private static final List<String> FIELD_LIST = Arrays.asList(FIELD_NAMES.split(",")); //$NON-NLS-1$

	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			SearchOptions options = buildSearchOptions(req, resp);
			FileGrepper grepper = new FileGrepper(req, resp, options);
			List<File> files = grepper.search();
			writeResponse(req, resp, files);
		} catch (GrepException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	private SearchOptions buildSearchOptions(HttpServletRequest req, HttpServletResponse resp) {
		SearchOptions options = new SearchOptions();

		String queryString = getEncodedParameter(req, CommonParams.Q);
		if (queryString == null)
			return null;
		if (queryString.length() > 0) {
			//divide into search terms delimited by space or plus ('+') character
			List<String> terms = new ArrayList<String>(Arrays.asList(queryString.split("[\\s\\+]+"))); //$NON-NLS-1$
			while (!terms.isEmpty()) {
				String term = terms.remove(0);
				if (term.length() == 0)
					continue;
				if (isSearchField(term)) {
					if (term.startsWith("NameLower:")) { //$NON-NLS-1$
						//decode the search term, we do not want to decode the location
						try {
							term = URLDecoder.decode(term, "UTF-8"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException e) {
							//try with encoded term
						}
						options.setIsCaseSensitive(true);
					} else if (term.startsWith("Location:")) { //$NON-NLS-1${

						options.setScope(term.substring(9 + req.getContextPath().length()));
						continue;
					} else if (term.startsWith("Name:")) { //$NON-NLS-1$
						try {
							term = URLDecoder.decode(term, "UTF-8"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException e) {
							//try with encoded term
						}
						//options.setFilenamePatterns(term.substring(5));
					} else {

					}
				} else {
					//decode the term string now
					try {
						term = URLDecoder.decode(term, "UTF-8"); //$NON-NLS-1$
					} catch (UnsupportedEncodingException e) {
						//try with encoded term
					}
					options.setSearchTerm(term);
				}
			}
		}
		return options;
	}

	/**
	 * Returns a request parameter in encoded form. Returns <code>null</code>
	 * if no such parameter is defined or has an empty value.
	 */
	private String getEncodedParameter(HttpServletRequest req, String key) {
		//TODO need to get query string unencoded - maybe use req.getQueryString() and parse manually
		String query = req.getQueryString();
		for (String param : query.split("&")) { //$NON-NLS-1$
			String[] pair = param.split("=", 2); //$NON-NLS-1$
			if (pair.length == 2 && key.equals(pair[0]))
				return pair[1];
		}
		return null;
	}

	/**
	 * Returns whether the search term is against a particular field rather than the default field
	 * (search on name, location, etc).
	 */
	private boolean isSearchField(String term) {
		for (String field : FIELD_LIST) {
			if (term.startsWith(field + ":")) //$NON-NLS-1$
				return true;
		}
		return false;
	}

	private void writeResponse(HttpServletRequest req, HttpServletResponse resp, List<File> files) {
		try {
			JSONObject json = convertListToJson(files);
			PrintWriter writer = resp.getWriter();
			writer.write(json.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private JSONObject convertListToJson(List<File> files) {
		JSONObject json = new JSONObject();
		try {
			json.put("numFound", files.size());
			json.put("start", 0);

			JSONArray docs = new JSONArray();
			for (File file : files) {
				JSONObject doc = new JSONObject();
				doc.put("Name", file.getName());
				doc.put("Length", file.length());
				doc.put("Directory", file.isDirectory());
				doc.put("LastModified", file.lastModified());
				doc.put("Location", file.getAbsolutePath());
				doc.put("Path", file.getAbsolutePath());
				docs.put(doc);
			}
			json.put("docs", docs);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return json;
	}
}
