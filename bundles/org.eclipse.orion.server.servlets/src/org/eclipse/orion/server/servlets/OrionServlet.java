/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.ServletStatusHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.IURIUnqualificationStrategy;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

/**
 * Common base class for servlets that defines convenience API for (de)serialization
 * of requests and responses.
 */
public abstract class OrionServlet extends HttpServlet {
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
		writeJSONResponse(req, resp, result, JsonURIUnqualificationStrategy.ALL);
	}

	public static void writeJSONResponse(HttpServletRequest req, HttpServletResponse resp, Object result, IURIUnqualificationStrategy strategy) throws IOException {
		Assert.isLegal(result instanceof JSONObject || result instanceof JSONArray);
		resp.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
		resp.setHeader("Cache-Control", "no-store"); //$NON-NLS-1$ //$NON-NLS-2$
		if (result instanceof JSONObject) {
			decorateResponse(req, (JSONObject) result);
		}
		if (strategy == null) {
			strategy = JsonURIUnqualificationStrategy.ALL;
		}
		strategy.run(req, result);

		//TODO look at accept header and chose appropriate response representation
		resp.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
		String response = prettyPrint(result);
		resp.getWriter().print(response);
		LoggerFactory.getLogger(OrionServlet.class).debug(response);
	}

	/**
	 * Decorates a JSON response and rewrites URLs found in it.
	 * @param req
	 * @param result
	 * @param strategy
	 */
	public static void decorateResponse(HttpServletRequest req, JSONObject result, IURIUnqualificationStrategy strategy) {
		decorateResponse(req, result);
		strategy.run(req, result);
		//if ("XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) { //$NON-NLS-1$ //$NON-NLS-2$
		// In JSON that is sent to in-Browser clients, remove scheme/userInfo/port information from URLs.
		//strategy.run(req, result);
		//}
	}

	/**
	 * If there is a search provider for this request resource, then add the search
	 * service location to the result object.
	 */
	public static void decorateResponse(HttpServletRequest req, JSONObject result) {
		decorateResponse(req, result, (IWebResourceDecorator)null);
	}
	
	/**
	 * If there is a search provider for this request resource, then add the search
	 * service location to the result object.
	 */
	public static void decorateResponse(HttpServletRequest req, JSONObject result, IWebResourceDecorator skip) {
		Collection<IWebResourceDecorator> decorators = Activator.getDefault().getWebResourceDecorators();
		URI requestURI = ServletResourceHandler.getURI(req);
		for (IWebResourceDecorator decorator : decorators) {
			if (decorator == skip) continue;
			decorator.addAtributesFor(req, requestURI, result);
		}
	}

	/**
	 * Returns the JSON object that is serialized in the request stream. Returns an
	 * empty JSON object if the request body is empty. Never returns null.
	 */
	public static JSONObject readJSONRequest(HttpServletRequest request) throws IOException, JSONException {
		JSONObject jsonRequest = (JSONObject) request.getAttribute("JSONRequest");
		if (jsonRequest == null) {
			StringWriter writer = new StringWriter();
			IOUtilities.pipe(request.getReader(), writer, false, false);
			String resultString = writer.toString();
			jsonRequest = (resultString.length() == 0) ? new JSONObject() : new JSONObject(resultString);
			request.setAttribute("JSONRequest", jsonRequest);
		}
		return jsonRequest;
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

	protected void printHeaders(HttpServletRequest req, StringBuffer out) {
		for (String header : Collections.<String> list(req.getHeaderNames()))
			out.append(header + ": " + req.getHeader(header) + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Helper method to print the request when debugging.
	 */
	protected void traceRequest(HttpServletRequest req) {
		StringBuffer result = new StringBuffer(req.getMethod());
		result.append(' ');
		result.append(req.getRequestURI());
		String query = req.getQueryString();
		if (query != null)
			result.append('?').append(query);
		if (DEBUG_VEBOSE)
			printHeaders(req, result);
		LoggerFactory.getLogger("org.eclipse.orion.server.config").info(result.toString());
	}
}
