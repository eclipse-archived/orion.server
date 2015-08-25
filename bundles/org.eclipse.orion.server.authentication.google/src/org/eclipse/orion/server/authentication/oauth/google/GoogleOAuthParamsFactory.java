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
package org.eclipse.orion.server.authentication.oauth.google;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.orion.server.authentication.oauth.OAuthException;
import org.eclipse.orion.server.authentication.oauth.OAuthParams;
import org.eclipse.orion.server.authentication.oauth.OAuthParamsFactory;

/**
 * Provide GoogleOAuthParams.
 *
 * @author aaudibert
 *
 */
public class GoogleOAuthParamsFactory implements OAuthParamsFactory {

	@Override
	public OAuthParams getOAuthParams(HttpServletRequest req, boolean login) throws OAuthException {
		return new GoogleOAuthParams(req, login);
	}

	@Override
	public String getOAuthProviderName() {
		return "google";
	}

}
