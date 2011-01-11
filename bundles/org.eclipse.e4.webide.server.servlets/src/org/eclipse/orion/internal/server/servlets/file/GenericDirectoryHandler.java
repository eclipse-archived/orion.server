/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.file;

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;

import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.osgi.util.NLS;

/**
 * A directory handler suitable for use by a generic HTTP client, such as a web browser.
 */
public class GenericDirectoryHandler extends ServletResourceHandler<IFileStore> {
	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IFileStore dir) throws ServletException {
		//can only generically handle get
		if (getMethod(request) != Method.GET)
			return false;
		try {
			String path = request.getPathInfo();
			IFileStore[] children = dir.childStores(EFS.NONE, null);
			PrintWriter writer = response.getWriter();
			writer.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">");
			writer.println("<html>");
			writer.println(" <head>");
			writer.println("<title>Index of " + path + "</title>");
			writer.println("</head>");
			writer.println("<body>");
			writer.println("<h1>Index of " + path + "</h1>");
			writer.println("<pre>Name                          Last modified      Size  ");
			writer.println("<hr>");
			for (IFileStore child : children) {
				IFileInfo childInfo = child.fetchInfo();
				String childName = child.getName();
				if (childInfo.isDirectory())
					childName += "/";
				writer.print("<a href=\"" + childName + "\">" + childName + "</a>");
				for (int i = childName.length(); i < 30; i++)
					writer.print(" ");
				writer.println(childInfo.getLastModified() + "      " + childInfo.getLength());
			}
			writer.println("<hr>");
			writer.println("</pre>");
			writer.println("</body></html>");
		} catch (Exception e) {
			throw new ServletException(NLS.bind("Error retrieving directory: {0}", dir), e);
		}
		return true;
	}
}
