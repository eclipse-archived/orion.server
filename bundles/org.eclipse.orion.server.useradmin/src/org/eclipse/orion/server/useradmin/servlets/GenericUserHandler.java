/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin.servlets;

import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.eclipse.osgi.util.NLS;

/**
 * A user handler suitable for use by a generic HTTP client, such as a web browser.
 */
public class GenericUserHandler extends ServletResourceHandler<String> {

	private UserServiceHelper userServiceHelper;

	GenericUserHandler(UserServiceHelper userServiceHelper, ServletResourceHandler<IStatus> statusHandler) {
		this.userServiceHelper = userServiceHelper;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String userPathInfo) throws ServletException {
		// can only generically handle GET /users/[userId]
		if (getMethod(request) != Method.GET || userPathInfo == null || userPathInfo.equals("/"))
			return false;

		String userId = userPathInfo.split("\\/")[1]; //$NON-NLS-1$
		if (UserServiceHelper.getDefault().getUserProfileService().getUserProfileNode(userId, false) == null)
			return false;

		try {
			PrintWriter writer = response.getWriter();
			writer.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">"); //$NON-NLS-1$
			writer.println("<html>"); //$NON-NLS-1$
			writer.println(" <head>"); //$NON-NLS-1$
			writer.println("<title>Details of " + userId + "</title>"); //$NON-NLS-1$ //$NON-NLS-2$
			writer.println("</head>"); //$NON-NLS-1$
			writer.println("<body>"); //$NON-NLS-1$
			writer.println("<h1>Details of " + userId + "</h1>"); //$NON-NLS-1$ //$NON-NLS-2$

			IOrionUserProfileNode userProfileNode = userServiceHelper.getUserProfileService().getUserProfileNode(userId, false);
			for (String partName : userProfileNode.childrenNames()) {
				writer.println("<h2>Part : " + partName + "</h2>"); //$NON-NLS-1$ //$NON-NLS-2$
				IOrionUserProfileNode partNode = userProfileNode.getUserProfileNode(partName);
				for (String key : partNode.keys()) {
					writer.println(key + " : " + partNode.get(key, "") + "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
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
