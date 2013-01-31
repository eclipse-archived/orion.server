/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;

/**
 * Servlet for performing searches against files in the workspace.
 */
public class SearchServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;
	private static final String FIELD_NAMES = "Id,Name,NameLower,Length,Directory,LastModified,Location,Path"; //$NON-NLS-1$
	private static final List<String> FIELD_LIST = Arrays.asList(FIELD_NAMES.split(",")); //$NON-NLS-1$

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
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
		String queryString = req.getParameter(CommonParams.Q);
		if (queryString == null)
			return null;
		if (queryString.length() > 0) {
			String processedQuery = ""; //$NON-NLS-1$
			//divide into search terms delimited by plus ('+') character
			List<String> terms = new ArrayList<String>(Arrays.asList(queryString.split("[\\+]+"))); //$NON-NLS-1$
			while (!terms.isEmpty()) {
				String term = terms.remove(0);
				if (term.length() == 0)
					continue;
				if (isSearchField(term)) {
					if (term.startsWith("NameLower:")) { //$NON-NLS-1$
						//solr does not lowercase queries containing wildcards
						//https://issues.apache.org/jira/browse/SOLR-219
						processedQuery += "NameLower:" + term.substring(10).toLowerCase(); //$NON-NLS-1$
					} else {
						//all other field searches are case sensitive
						processedQuery += term;
					}
				} else {
					boolean isPhrase = term.charAt(0) == '"';
					//solr does not lowercase queries containing wildcards
					//see https://bugs.eclipse.org/bugs/show_bug.cgi?id=359766
					String processedTerm = ClientUtils.escapeQueryChars(term.toLowerCase());
					//add leading and trailing wildcards to match word segments
					if (!isPhrase) {
						if (processedTerm.charAt(0) != '*')
							processedTerm = '*' + processedTerm;
						if (processedTerm.charAt(processedTerm.length() - 1) != '*')
							processedTerm += '*';
					}
					processedQuery += processedTerm;
				}
				processedQuery += " AND "; //$NON-NLS-1$
			}
			queryString = processedQuery;
		}
		queryString += ProtocolConstants.KEY_USER_NAME + ':' + ClientUtils.escapeQueryChars(req.getRemoteUser());
		query.setQuery(queryString);
		//other common fields
		setField(req, query, CommonParams.ROWS);
		setField(req, query, CommonParams.START);
		setField(req, query, CommonParams.SORT);
		return query;
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
		SolrQueryRequest solrRequest = new LocalSolrQueryRequest(core, query.toNamedList());
		SolrQueryResponse solrResponse = new SolrQueryResponse();
		//bash the query in the response to remove user info
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
