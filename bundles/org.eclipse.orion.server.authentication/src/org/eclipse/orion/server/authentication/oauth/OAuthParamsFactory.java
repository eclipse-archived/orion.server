/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.oauth;

import javax.servlet.http.HttpServletRequest;

/**
 * Interface for providing OAuthParams.
 *
 * @author aaudibert
 *
 */
public interface OAuthParamsFactory {

	OAuthParams getOAuthParams(HttpServletRequest req, boolean login) throws OAuthException;

	String getOAuthProviderName();

	public static final String PROVIDER = "provider";
}
