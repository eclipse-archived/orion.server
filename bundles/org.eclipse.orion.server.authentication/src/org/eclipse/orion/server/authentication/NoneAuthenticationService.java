/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication;

import java.io.IOException;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This authentication service is registered when <code>configuration.xml</code>
 * points to "None" authentication. This authentication service passes though
 * all the requests returns empty strings for
 * {@link #getAuthenticatedUser(HttpServletRequest, HttpServletResponse, Properties)}
 * and {@link #getAuthType()}.
 * 
 */
public class NoneAuthenticationService implements IAuthenticationService {

	public static final String AUTH_TYPE = "None";
	private boolean registered = false;

	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		return getAuthenticatedUser(req, resp);
	}

	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		return "Anonymous";
	}

	public String getAuthType() {
		return AUTH_TYPE;
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}

	public boolean isRegistered() {
		return registered;
	}

}
