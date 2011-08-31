/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;

/**
 * Servlet for performing searches against files in the workspace.
 */
public class SearchServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		SolrQuery query = buildSolrQuery(req);
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
		query.setParam(CommonParams.FL, "Id,Name,Length,Directory,LastModified,Location"); //$NON-NLS-1$
		query.setParam("hl", "true"); //$NON-NLS-1$ //$NON-NLS-2$
		String queryString = ClientUtils.escapeQueryChars(req.getParameter(CommonParams.Q));
		if (queryString.trim().length() > 0)
			queryString += " AND "; //$NON-NLS-1$
		queryString += ProtocolConstants.KEY_USER_NAME + ':' + ClientUtils.escapeQueryChars(req.getRemoteUser());
		query.setQuery(queryString);
		return query;
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
		solrResponse.setAllValues(queryResponse.getResponse());
		QueryResponseWriter writer = core.getQueryResponseWriter("json"); //$NON-NLS-1$
		writer.write(httpResponse.getWriter(), solrRequest, solrResponse);
	}
}
