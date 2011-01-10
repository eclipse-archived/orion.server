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
package org.eclipse.e4.webide.server.authentication.formopenid.servlets;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.e4.webide.server.authentication.formopenid.FormOpenIdAuthenticationService;

public class AuthInitServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	public static final String CSS_LINK_PROPERTY = "STYLES"; //$NON-NLS-1$

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
		resp.setHeader("WWW-Authenticate", HttpServletRequest.FORM_AUTH); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		String putStyle = properties.getProperty(CSS_LINK_PROPERTY) == null ? "" //$NON-NLS-1$
				: "&styles=" + properties.getProperty(CSS_LINK_PROPERTY); //$NON-NLS-1$
		req.setAttribute(FormOpenIdAuthenticationService.OPENIDS_PROPERTY, properties.get(FormOpenIdAuthenticationService.OPENIDS_PROPERTY));
		RequestDispatcher rd = req.getRequestDispatcher("/mixlogin/checkuser?redirect=" //$NON-NLS-1$
				+ req.getRequestURI() + putStyle);
		try {
			rd.forward(req, resp);
		} catch (ServletException e) {
			throw new IOException(e);
		} finally {
			resp.flushBuffer();
		}
	}
}
