/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.file;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.sql.Date;
import java.text.SimpleDateFormat;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.osgi.util.NLS;

/**
 * A directory handler suitable for use by a generic HTTP client, such as a web browser.
 */
public class GenericDirectoryHandler extends ServletResourceHandler<IFileStore> {
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); //$NON-NLS-1$

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws ServletException {
		//can only generically handle get
		if (getMethod(request) != Method.GET)
			return false;
		try {
			response.setCharacterEncoding("UTF-8");
			response.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
			String path = request.getPathInfo();
			IFileStore[] children = dir.childStores(EFS.NONE, null);
			PrintWriter writer = response.getWriter();
			writer.println("<!DOCTYPE HTML>"); //$NON-NLS-1$
			writer.println("<html>"); //$NON-NLS-1$
			writer.println(" <head>"); //$NON-NLS-1$
			writer.println("<title>Index of " + path + "</title>"); //$NON-NLS-1$ //$NON-NLS-2$
			writer.println("</head>"); //$NON-NLS-1$
			writer.println("<body>"); //$NON-NLS-1$
			writer.println("<h1>Index of " + path + "</h1>"); //$NON-NLS-1$ //$NON-NLS-2$
			writer.println("<pre>Name                          Last modified		Size  "); //$NON-NLS-1$
			writer.println("<hr>"); //$NON-NLS-1$
			for (IFileStore child : children) {
				IFileInfo childInfo = child.fetchInfo(EFS.NONE, null);
				String childName = child.getName();
				String encodedChildName = URLEncoder.encode(childName, "UTF8").replace("+", "%20");
				if (childInfo.isDirectory()) {
					childName += '/';
					encodedChildName += '/';
				}
				writer.print("<a href=\"" + encodedChildName + "\">" + childName + "</a>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				for (int i = childName.length(); i < 30; i++)
					writer.print(' ');
				String formattedLastModified = dateFormat.format(new Date(childInfo.getLastModified()));
				writer.println(formattedLastModified + "      " + childInfo.getLength()); //$NON-NLS-1$
			}
			writer.println("<hr>"); //$NON-NLS-1$
			writer.println("</pre>"); //$NON-NLS-1$
			writer.println("</body></html>"); //$NON-NLS-1$
		} catch (Exception e) {
			if (!handleAuthFailure(request, response, e))
				throw new ServletException(NLS.bind("Error retrieving directory: {0}", dir), e);
		}
		return true;
	}
}
