/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.webide.server.servlets;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.eclipse.core.runtime.*;
import org.eclipse.e4.internal.webide.server.IOUtilities;
import org.eclipse.e4.internal.webide.server.IWebResourceDecorator;
import org.eclipse.e4.internal.webide.server.servlets.*;
import org.json.*;

/**
 * Common base class for servlets that defines convenience API for (de)serialization
 * of requests and responses.
 */
public abstract class EclipseWebServlet extends HttpServlet {
	/**
	 * Global flag for enabling debug tracing
	 */
	public static final boolean DEBUG = true;
	/**
	 * Global flag for enabling detailed tracing of the protocol.
	 */
	protected static final boolean DEBUG_VEBOSE = false;

	private static final long serialVersionUID = 1L;
	private static final ServletResourceHandler<IStatus> statusHandler = new ServletStatusHandler();

	private static String prettyPrint(Object result) {
		try {
			if (result instanceof JSONObject)
				return ((JSONObject) result).toString(2);
		} catch (JSONException e) {
			//fall through below
		}
		return result.toString();
	}

	public static void writeJSONResponse(HttpServletRequest req, HttpServletResponse resp, Object result) throws IOException {
		Assert.isLegal(result instanceof JSONObject || result instanceof JSONArray);
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
		if (result instanceof JSONObject)
			decorateResponse(req, (JSONObject) result);
		//TODO look at accept header and chose appropriate response representation
		resp.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
		String response = prettyPrint(result);
		resp.getWriter().print(response);
		if (EclipseWebServlet.DEBUG)
			System.out.println(response);
	}

	/**
	 * If there is a search provider for this request resource, then add the search
	 * service location to the result object.
	 */
	public static void decorateResponse(HttpServletRequest req, JSONObject result) {
		Collection<IWebResourceDecorator> decorators = Activator.getDefault().getSearchProviders();
		for (IWebResourceDecorator decorator : decorators)
			decorator.addAtributesFor(ServletResourceHandler.getURI(req), result);
	}

	/**
	 * Returns the JSON object that is serialized in the request stream. Returns an
	 * empty JSON object if the request body is empty.
	 */
	public static JSONObject readJSONRequest(HttpServletRequest request) throws IOException, JSONException {
		StringWriter writer = new StringWriter();
		IOUtilities.pipe(request.getReader(), writer, false, false);
		String resultString = writer.toString();
		if (resultString.length() == 0)
			return new JSONObject();
		return new JSONObject(resultString);
	}

	/**
	 * Creates a URI using the base URL of the request, but with the provided
	 * query string as a suffix.
	 */
	protected String createQuery(HttpServletRequest req, String query) {
		StringBuffer requestURL = req.getRequestURL();
		int indexOfURI = requestURL.indexOf(req.getRequestURI().toString());
		return requestURL.replace(indexOfURI, requestURL.length(), query).toString();
	}

	protected ServletResourceHandler<IStatus> getStatusHandler() {
		return statusHandler;
	}

	/**
	 * Generic handler for exceptions.
	 */
	protected void handleException(HttpServletResponse response, IStatus status) throws ServletException {
		statusHandler.handleRequest(null, response, status);
	}

	/**
	 * Generic handler for exceptions.
	 */
	protected void handleException(HttpServletResponse response, IStatus status, int httpCode) throws ServletException {
		handleException(response, new ServerStatus(status, httpCode));
	}

	/**
	 * Generic handler for exceptions. 
	 */
	protected void handleException(HttpServletResponse resp, String msg, Exception e) throws ServletException {
		handleException(resp, msg, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}

	/**
	 * Generic handler for exceptions. 
	 */
	protected void handleException(HttpServletResponse resp, String msg, Exception e, int httpCode) throws ServletException {
		handleException(resp, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e), httpCode);
	}

	@SuppressWarnings("unchecked")
	protected void printHeaders(HttpServletRequest req) {
		for (String header : Collections.<String> list(req.getHeaderNames()))
			System.out.println(header + ": " + req.getHeader(header)); //$NON-NLS-1$
	}

	/**
	 * Helper method to print the request when debugging.
	 */
	protected void traceRequest(HttpServletRequest req) {
		if (!DEBUG)
			return;
		StringBuffer result = new StringBuffer(req.getMethod());
		result.append(' ');
		result.append(req.getRequestURI());
		String query = req.getQueryString();
		if (query != null)
			result.append('?').append(query);
		System.out.println(result);
		if (DEBUG_VEBOSE)
			printHeaders(req);
	}

}
