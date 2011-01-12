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
package org.eclipse.orion.server.openid.servlet;

import org.eclipse.orion.server.openid.core.OpenIdHelper;
import org.eclipse.orion.server.openid.core.OpenidConsumer;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class OpenidServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2395713347747299772L;
	private OpenidConsumer consumer;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String openid = req.getParameter(OpenIdHelper.OPENID);
		if (openid != null) {
			consumer = OpenIdHelper.redirectToOpenIdProvider(req, resp, consumer);
			return;
		}

		String op_return = req.getParameter(OpenIdHelper.OP_RETURN);
		if (op_return != null) {
			OpenIdHelper.handleOpenIdReturn(req, resp, consumer);
			return;
		}

		if (OpenIdHelper.getAuthenticatedUser(req) != null) {
			OpenIdHelper.writeLoginResponse(OpenIdHelper.getAuthenticatedUser(req), resp);
			return;
		}

		super.doGet(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (OpenIdHelper.getAuthenticatedUser(req) != null) {
			OpenIdHelper.writeLoginResponse(OpenIdHelper.getAuthenticatedUser(req), resp);
			return;
		}
	}

}
