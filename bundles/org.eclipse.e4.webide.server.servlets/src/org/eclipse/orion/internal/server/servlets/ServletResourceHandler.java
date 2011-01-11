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
package org.eclipse.orion.internal.server.servlets;

import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet resource handler processes an HTTP request for a given resource.
 * The handler is given a resource object that has been constructed based on the
 * request URL and headers. The handler is responsible for filling in the response content type, headers,
 * and body as appropriate for a given result object.
 * 
 * @param <T> The type of resource this handler processes.
 */
public abstract class ServletResourceHandler<T> {
	/**
	 * An enumeration of the HTTP method types.
	 */
	public enum Method {
		GET, HEAD, OPTIONS, POST, PUT, DELETE;
		/**
		 * Convenience method to convert an HTTP method string into an
		 * enumerated type.
		 */
		public static Method fromString(String methodName) {
			if ("GET".equals(methodName)) //$NON-NLS-1$
				return GET;
			if ("PUT".equals(methodName)) //$NON-NLS-1$
				return PUT;
			if ("POST".equals(methodName)) //$NON-NLS-1$
				return POST;
			if ("HEAD".equals(methodName)) //$NON-NLS-1$
				return HEAD;
			if ("OPTIONS".equals(methodName)) //$NON-NLS-1$
				return OPTIONS;
			if ("DELETE".equals(methodName)) //$NON-NLS-1$
				return DELETE;
			return null;
		}
	}

	/**
	 * Convenience method to convert an HTTP method string into an
	 * enumerated type.
	 */
	public static Method getMethod(HttpServletRequest request) {
		return Method.fromString(request.getMethod());
	}

	/**
	 * Convenience method to obtain the URI of the request
	 */
	public static URI getURI(HttpServletRequest request) {
		StringBuffer result = request.getRequestURL();
		try {
			return new URI(result.toString());
		} catch (URISyntaxException e) {
			//location not properly encoded
			return null;
		}
	}

	/**
	 * Handles the given HTTP request for the provided resource.
	 * 
	 * @param request The HTTP request object
	 * @param response The servlet response object
	 * @param object The object that is the target resource for the request
	 */
	public abstract boolean handleRequest(HttpServletRequest request, HttpServletResponse response, T object) throws ServletException;
}
