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
package org.eclipse.orion.server.authentication.form.servlets;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AuthInitServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	public static final String CSS_LINK_PROPERTY = "STYLES";

	private Properties properties;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		sendAuthInit(req, resp, properties);
	}

	public AuthInitServlet(Properties properties) {
		super();
		this.properties = properties;
	}

	private void sendAuthInit(HttpServletRequest req, HttpServletResponse resp, Properties properties) throws IOException {
		if (properties == null) {
			properties = new Properties();
		}
		resp.setHeader("WWW-Authenticate", HttpServletRequest.FORM_AUTH);
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		String putStyle = properties.getProperty(CSS_LINK_PROPERTY) == null ? "" : "&styles=" + properties.getProperty(CSS_LINK_PROPERTY);
		RequestDispatcher rd = req.getRequestDispatcher("/loginform/checkuser?redirect=" + req.getRequestURI() + putStyle);
		try {
			rd.forward(req, resp);
		} catch (ServletException e) {
			throw new IOException(e);
		} finally {
			resp.flushBuffer();
		}
	}
}
