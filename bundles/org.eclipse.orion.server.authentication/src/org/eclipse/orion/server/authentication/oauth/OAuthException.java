/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.oauth;

/**
 * An exception thrown if an error occurs while handling oauth requests.
 * @author Aidan Redpath
 *
 */
public class OAuthException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5063237215282170865L;

	public OAuthException(String message) {
		super(message);
	}

	public OAuthException(Throwable cause) {
		super(cause.getMessage(), cause);
	}

}
