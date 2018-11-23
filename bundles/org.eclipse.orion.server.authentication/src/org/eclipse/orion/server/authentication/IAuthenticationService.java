/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This interface should be provided by an authentication plugin.
 * 
 */
public interface IAuthenticationService {

	static final String ADMIN_LOGIN_VALUE = "admin"; //$NON-NLS-1$
	static final String ADMIN_NAME_VALUE = "Administrator"; //$NON-NLS-1$

	static final String ANONYMOUS_LOGIN_VALUE = "anonymous"; //$NON-NLS-1$
	static final String ANONYMOUS_NAME_VALUE = "Anonymous"; //$NON-NLS-1$

	/**
	 * This method verifies the user identity send in the
	 * {@link HttpServletRequest}. This method returns only information and does
	 * not modify entry parameters to notify user about the authentication
	 * failure, method
	 * {@link #authenticateUser(HttpServletRequest, HttpServletResponse)}
	 * should be used to achieve this.
	 * 
	 * @param req
	 * @param resp
	 * @return authenticated username or <code>null</code> if users could not be
	 *         authenticated.
	 * @throws IOException
	 */
	public String getAuthenticatedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException;

	/**
	 * This method is called before any request is passed to a servlet.
	 * Implementation of this method should do whatever is necessary to
	 * authenticate the user. If any redirection or setting the headers is
	 * required the implementation should handle it.<br>
	 * When this method returns <code>null</code> the request is identified as
	 * unauthenticated and it's not passed to the servlet. When return value is
	 * different than <code>null</code> it is set as remote user name and may be
	 * obtained by {@link HttpServletRequest#getRemoteUser()}.
	 * 
	 * @param req
	 * @param resp
	 * @return authenticated username or <code>null</code> if users could not be
	 *         authenticated.
	 * @throws IOException
	 */
	public String authenticateUser(HttpServletRequest req, HttpServletResponse resp) throws IOException;

	/**
	 * The string representation of authentication type. It is used to set
	 * {@link HttpServletRequest#getAuthType()}.
	 * 
	 * @return String representation of authentication type.
	 */
	public String getAuthType();

	public void setRegistered(boolean registered);

	public boolean isRegistered();
}
