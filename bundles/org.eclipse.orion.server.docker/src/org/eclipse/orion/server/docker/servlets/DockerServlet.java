/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.docker.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.servlets.OrionServlet;

/**
 * Servlet to handle requests to the docker server.
 *  
 * @author Anthony Hunter
 * @author Bogdan Gheorghe
 */
public class DockerServlet extends OrionServlet {

	private static final long serialVersionUID = -8184697425851833225L;

	private ServletResourceHandler<String> dockerHandler;

	public DockerServlet() {
		dockerHandler = new DockerHandler(getStatusHandler());
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		// path is the format /docker/{request}
		String pathInfo = req.getPathInfo();
		if (pathInfo != null) {
			String[] pathInfoParts = pathInfo.split("\\/", 2);
			if (pathInfoParts.length == 2) {
				if (dockerHandler.handleRequest(req, resp, pathInfoParts[1])) {
					return;
				}
			}
		}
		// finally invoke super to return an error for requests we don't know how to handle
		super.doGet(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}
}
