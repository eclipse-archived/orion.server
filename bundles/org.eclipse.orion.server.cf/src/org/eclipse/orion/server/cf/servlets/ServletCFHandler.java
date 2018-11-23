/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.servlets;

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.cf.handlers.v1.CFHandlerV1;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.osgi.framework.Version;

/**
 * General handler for Jazz.
 */
public class ServletCFHandler extends ServletResourceHandler<String> {

	public static VersionRange VERSION1 = new VersionRange("[1,2)"); //$NON-NLS-1$

	private final ServletResourceHandler<String> cFHandlerV1;

	final ServletResourceHandler<IStatus> statusHandler;

	ServletCFHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
		cFHandlerV1 = new CFHandlerV1(statusHandler);
	}

	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		String versionString = request.getHeader(CFServlet.HEADER_CF_VERSION);
		Version version = versionString == null ? null : new Version(versionString);

		ServletResourceHandler<String> handler;
		if (VERSION1.isIncluded(version)) {
			handler = cFHandlerV1;
			return handler.handleRequest(request, response, path);
		}

		return false;
	}
}