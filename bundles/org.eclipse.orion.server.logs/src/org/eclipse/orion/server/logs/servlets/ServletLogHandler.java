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

package org.eclipse.orion.server.logs.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;

public class ServletLogHandler extends ServletResourceHandler<String> {
	private ServletResourceHandler<String> appenderHandler;

	public ServletLogHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.appenderHandler = new AppenderHandler(statusHandler);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response,
			String pathInfo) throws ServletException {

		if (pathInfo == null)
			return false;

		IPath path = new Path(pathInfo);
		if ("appenders".equals(path.segment(0))) {
			return appenderHandler.handleRequest(request, response, path.removeFirstSegments(1)
					.toString());
		}

		/* don't know what to do */
		return false;
	}
}
