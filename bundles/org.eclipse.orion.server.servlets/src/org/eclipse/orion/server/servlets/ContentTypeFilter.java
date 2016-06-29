/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.core.ProtocolConstants;

/**
 * A filter to ensure that Orion responses always have a Content-Type header. 
 * 
 * @author ahunter
 */
public class ContentTypeFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// nothing to do
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		chain.doFilter(request, response);

		// Defect 491041: post check to ensure every response includes a Content-Type header.
		if (httpResponse.getStatus() != HttpServletResponse.SC_NO_CONTENT) {
			String contentType = httpResponse.getContentType();
			if (contentType == null) {
				String requestURI = httpRequest.getRequestURI();

				if (requestURI != null) {
					String[] pathInfoParts = requestURI.split("\\/");
					if (pathInfoParts.length == 0) {
						return;
					}

					String filename = pathInfoParts[pathInfoParts.length - 1];
					if (filename.equals("defaults.pref") || filename.endsWith(".json") || filename.endsWith(".launch")) {
						httpResponse.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
					} else if (filename.endsWith(".md") || filename.endsWith(".yml")) {
						httpResponse.setContentType(ProtocolConstants.CONTENT_TYPE_PLAIN_TEXT);
					} else if (filename.endsWith(".css")) {
						httpResponse.setContentType(ProtocolConstants.CONTENT_TYPE_CSS);
					} else if (filename.endsWith(".js")) {
						httpResponse.setContentType(ProtocolConstants.CONTENT_TYPE_JAVASCRIPT);
					} else if (filename.endsWith(".woff")) {
						httpResponse.setContentType(ProtocolConstants.CONTENT_TYPE_FONT);
					} else {
						// see if we have a mime type to use as the content type
						String mimeType = httpRequest.getServletContext().getMimeType(filename);
						if (mimeType != null) {
							String newContentType = mimeType + "; charset=UTF-8";
							httpResponse.setContentType(newContentType);
						} else {
							// fall back to using plain text content type
							httpResponse.setContentType(ProtocolConstants.CONTENT_TYPE_PLAIN_TEXT);
						}
					}
				}
			}
		}
	}

	@Override
	public void destroy() {
		// nothing to do
	}

}
