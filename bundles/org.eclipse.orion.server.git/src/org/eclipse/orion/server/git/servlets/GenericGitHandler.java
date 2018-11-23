/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.osgi.util.NLS;

/**
 * A git handler suitable for use by a generic HTTP client, such as a web browser.
 */
public class GenericGitHandler extends ServletResourceHandler<String> {

	GenericGitHandler(ServletResourceHandler<IStatus> statusHandler) {
		super();
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String gitPathInfo) throws ServletException {
		// can only generically handle get
		if (getMethod(request) != Method.GET)
			return false;

		try {
			PrintWriter writer = response.getWriter();
			writer.println("<!DOCTYPE HTML>"); //$NON-NLS-1$
			writer.println("<html>"); //$NON-NLS-1$
			writer.println(" <head>"); //$NON-NLS-1$
			writer.println("<title>Git</title>"); //$NON-NLS-1$
			writer.println("</head>"); //$NON-NLS-1$
			writer.println("<body>"); //$NON-NLS-1$
			writer.println("<h1>Git</h1>"); //$NON-NLS-1$
			writer.println("</body></html>"); //$NON-NLS-1$
		} catch (Exception e) {
			throw new ServletException(NLS.bind("Error retrieving git result: {0}", gitPathInfo), e); //$NON-NLS-1$
		}
		return true;
	}
}
