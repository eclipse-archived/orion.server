/*******************************************************************************
 * Copyright (c) 2008, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.internal.server.servlets.about;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.servlets.OrionServlet;

/**
 * Servlet to handle requests for information about the orion server.
 * 
 * @author Anthony Hunter
 */
public class AboutServlet extends OrionServlet {

	private ServletResourceHandler<String> aboutHandler;

	public AboutServlet() {
		aboutHandler = new AboutHandler(getStatusHandler());
	}

	private static final long serialVersionUID = -1426745453574711075L;

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		// path should be /about/about.html
		String pathInfo = req.getPathInfo();
		if (pathInfo != null) {
			String[] pathInfoParts = pathInfo.split("\\/", 2);
			if (pathInfoParts.length == 2 && pathInfoParts[1].equals("about.html")) {
				if (aboutHandler.handleRequest(req, resp, pathInfoParts[1])) {
					return;
				}
			}
		}
		// finally invoke super to return an error for requests we don't know how to handle
		super.doGet(req, resp);
	}

}
