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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.e4.webide.server.authentication.form.core.FormAuthHelper;
import org.eclipse.e4.webide.server.openid.core.OpenIdHelper;

public class FormOpenIdLogoutServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1143015041529139868L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		FormAuthHelper.performLogout(req);
		OpenIdHelper.performLogout(req);
	}
}
