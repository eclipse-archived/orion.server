/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.useradmin;

import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.eclipse.osgi.util.NLS;

/**
 * A user handler suitable for use by a generic HTTP client, such as a web browser.
 */
public class GenericUserHandler extends ServletResourceHandler<String> {

	GenericUserHandler(ServletResourceHandler<IStatus> statusHandler) {
		super();
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String userPathInfo) throws ServletException {
		// can only generically handle GET /users/[userId]
		if (getMethod(request) != Method.GET || userPathInfo == null || userPathInfo.equals("/")) {
			return false;
		}

		String userId = userPathInfo.split("\\/")[1]; //$NON-NLS-1$
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, userId, false, false);
			if (userInfo == null) {
				return false;
			}

			PrintWriter writer = response.getWriter();
			writer.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">"); //$NON-NLS-1$
			writer.println("<html>"); //$NON-NLS-1$
			writer.println(" <head>"); //$NON-NLS-1$
			writer.println("<title>Details of " + userId + "</title>"); //$NON-NLS-1$ //$NON-NLS-2$
			writer.println("</head>"); //$NON-NLS-1$
			writer.println("<body>"); //$NON-NLS-1$
			writer.println("<h1>Details of " + userId + "</h1>"); //$NON-NLS-1$ //$NON-NLS-2$

			if (userInfo.getProperties().containsKey(UserConstants.LAST_LOGIN_TIMESTAMP)) {
				String lastLoginTimestamp = userInfo.getProperty(UserConstants.LAST_LOGIN_TIMESTAMP);
				writer.println(UserConstants.LAST_LOGIN_TIMESTAMP + " : " + lastLoginTimestamp + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (userInfo.getProperties().containsKey(UserConstants.DISK_USAGE)) {
				String diskUsage = userInfo.getProperty(UserConstants.DISK_USAGE);
				writer.println(UserConstants.DISK_USAGE + " : " + diskUsage + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (userInfo.getProperties().containsKey(UserConstants.DISK_USAGE_TIMESTAMP)) {
				String diskUsageTimestamp = userInfo.getProperty(UserConstants.DISK_USAGE_TIMESTAMP);
				writer.println(UserConstants.DISK_USAGE_TIMESTAMP + " : " + diskUsageTimestamp + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			writer.println("<hr>"); //$NON-NLS-1$
			writer.println("</pre>"); //$NON-NLS-1$
			writer.println("</body></html>"); //$NON-NLS-1$
		} catch (Exception e) {
			throw new ServletException(NLS.bind("Error retrieving user: {0}", userId), e); //$NON-NLS-1$
		}
		return true;
	}
}
