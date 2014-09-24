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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.osgi.framework.Version;

/**
 * General handler for Git.
 */
public class ServletGitHandler extends ServletResourceHandler<String> {

	public static VersionRange VERSION1 = new VersionRange("[1,2)"); //$NON-NLS-1$

	private final ServletResourceHandler<String> genericGitHandler;
	private final ServletResourceHandler<String> gitHandlerV1;

	final ServletResourceHandler<IStatus> statusHandler;

	ServletGitHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
		genericGitHandler = new GenericGitHandler(statusHandler);
		gitHandlerV1 = new GitHandlerV1(statusHandler);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String gitPathInfo) throws ServletException {
		String versionString = request.getHeader(ProtocolConstants.HEADER_ORION_VERSION);
		Version version = versionString == null ? null : new Version(versionString);

		ServletResourceHandler<String> handler;
		if (version == null || VERSION1.isIncluded(version))
			handler = gitHandlerV1;
		else
			handler = genericGitHandler;
		return handler.handleRequest(request, response, gitPathInfo);
	}
}