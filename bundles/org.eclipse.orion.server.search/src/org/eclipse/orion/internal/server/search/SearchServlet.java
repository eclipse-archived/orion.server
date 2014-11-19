/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.eclipse.orion.internal.server.search.grep.GrepServlet;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.servlets.OrionServlet;

/**
 * Servlet for performing searches against files in the workspace.
 */
public class SearchServlet extends OrionServlet {
	/**
	 * Separator between query terms
	 */
	private static final String AND = " AND "; //$NON-NLS-1$
	private static final String OR = " OR "; //$NON-NLS-1$
	private static final long serialVersionUID = 1L;
	private static final String FIELD_NAMES = "Name,NameLower,Length,Directory,LastModified,Location,Path"; //$NON-NLS-1$
	private static final List<String> FIELD_LIST = Arrays.asList(FIELD_NAMES.split(",")); //$NON-NLS-1$

	private GrepServlet grepServlet = new GrepServlet();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);

		if (PreferenceHelper.getString(ServerConstants.CONFIG_GREP_SEARCH_ENABLED, "false").equals("true")) {
			grepServlet.doGet(req, resp);
			return;
		}

		SolrQuery query = buildSolrQuery(req);
		if (query == null) {
			handleException(resp, "Invalid search request", null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		try {
			QueryResponse solrResponse = SearchActivator.getInstance().getSolrServer().query(query);
			writeResponse(query, req, resp, solrResponse);
		} catch (SolrServerException e) {
			LogHelper.log(e);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	private SolrQuery buildSolrQuery(HttpServletRequest req) {
		SolrQuery query = new SolrQuery();
		query.setParam(CommonParams.WT, "json"); //$NON-NLS-1$
		query.setParam(CommonParams.FL, FIELD_NAMES);
		String queryString = getEncodedParameter(req, CommonParams.Q);
		if (queryString == null)
			return null;
		if (queryString.length() > 0) {
			String processedQuery = ""; //$NON-NLS-1$
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
						//solr does not lowercase queries containing wildcards
						//https://issues.apache.org/jira/browse/SOLR-219
						processedQuery += "NameLower:" + term.substring(10).toLowerCase(); //$NON-NLS-1$
					} else if (term.startsWith("Location:")) { //$NON-NLS-1${
						//Use Location as a filter query to improve query performence
						//See https://bugs.eclipse.org/bugs/show_bug.cgi?id=415874
						//processedQuery += "Location:" + term.substring(9 + req.getContextPath().length()); //$NON-NLS-1$
						query.addFilterQuery("Location:" + term.substring(9 + req.getContextPath().length()));
						continue;
					} else if (term.startsWith("Name:")) { //$NON-NLS-1$
						try {
							term = URLDecoder.decode(term, "UTF-8"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException e) {
							//try with encoded term
						}
						processedQuery += "Name:(" + term.substring(5).replaceAll("/", OR) + ")";
					} else {
						//all other field searches are case sensitive
						processedQuery += term;
					}
				} else {
					//decode the term string now
					try {
						term = URLDecoder.decode(term, "UTF-8"); //$NON-NLS-1$
					} catch (UnsupportedEncodingException e) {
						//try with encoded term
					}
					boolean isPhrase = term.charAt(0) == '"';
					boolean leadingWildCard = term.charAt(0) == '*';
					//see https://bugs.eclipse.org/bugs/show_bug.cgi?id=415874#c12
					//For a term starting with "*" we are using the leading wild card. 
					//Otherwise we only use tailing wild card to improve performance.
					if (leadingWildCard) {
						term = term.substring(1);
					}
					//solr does not lowercase queries containing wildcards
					//see https://bugs.eclipse.org/bugs/show_bug.cgi?id=359766
					String processedTerm = ClientUtils.escapeQueryChars(term.toLowerCase());
					//add leading and trailing wildcards to match word segments
					if (!isPhrase) {
						if (leadingWildCard)
							processedTerm = '*' + processedTerm;
						if (processedTerm.charAt(processedTerm.length() - 1) != '*')
							processedTerm += '*';
					}
					processedQuery += processedTerm;
				}
				processedQuery += AND;
			}
			queryString = processedQuery;
		}
		//remove trailing AND
		if (queryString.endsWith(AND))
			queryString = queryString.substring(0, queryString.length() - AND.length());
		query.setQuery(queryString);

		//filter to search only documents belonging to current user
		query.addFilterQuery(ProtocolConstants.KEY_USER_NAME + ':' + ClientUtils.escapeQueryChars(req.getRemoteUser()));

		//other common fields
		setField(req, query, CommonParams.ROWS);
		setField(req, query, CommonParams.START);
		setField(req, query, CommonParams.SORT);
		return query;
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

	private void setField(HttpServletRequest req, SolrQuery query, String parameter) {
		String value = req.getParameter(parameter);
		if (value != null)
			query.set(parameter, value);
	}

	/**
	 * Writes the response to the search query to the HTTP response's output stream.
	 */
	private void writeResponse(SolrQuery query, HttpServletRequest httpRequest, HttpServletResponse httpResponse, QueryResponse queryResponse) throws IOException {
		SolrCore core = SearchActivator.getInstance().getSolrCore();
		//this seems to be the only way to obtain the JSON response representation
		SolrQueryRequest solrRequest = null;
		try {
			solrRequest = new LocalSolrQueryRequest(core, query.toNamedList());
			SolrQueryResponse solrResponse = new SolrQueryResponse();
			// Added encoding check as per Bugzilla 406757
			if (httpRequest.getCharacterEncoding() == null) {
				httpRequest.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
				httpResponse.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
			}
			//bash the query in the response to remove user info
			@SuppressWarnings("unchecked")
			NamedList<Object> params = (NamedList<Object>) queryResponse.getHeader().get("params"); //$NON-NLS-1$
			params.remove(CommonParams.Q);
			params.add(CommonParams.Q, httpRequest.getParameter(CommonParams.Q));
			NamedList<Object> values = queryResponse.getResponse();
			String contextPath = httpRequest.getContextPath();
			if (contextPath.length() > 0)
				setSearchResultContext(values, contextPath);
			solrResponse.setAllValues(values);
			QueryResponseWriter writer = core.getQueryResponseWriter("json"); //$NON-NLS-1$
			writer.write(httpResponse.getWriter(), solrRequest, solrResponse);
		} finally {
			if (solrRequest != null) {
				solrRequest.close();
			}
		}
	}

	/**
	 * Prepend the server context path to the location of search result documents.
	 */
	private void setSearchResultContext(NamedList<Object> values, String contextPath) {
		//find the search result documents in the search response
		SolrDocumentList documents = (SolrDocumentList) values.get("response"); //$NON-NLS-1$
		if (documents == null)
			return;
		for (SolrDocument doc : documents) {
			String location = (String) doc.getFieldValue(ProtocolConstants.KEY_LOCATION);
			if (location != null) {
				//prepend the context path and update the document
				location = contextPath + location;
				doc.setField(ProtocolConstants.KEY_LOCATION, location);
			}
		}
	}
}
