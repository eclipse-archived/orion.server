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
package org.eclipse.orion.server.tests;

import org.eclipse.orion.server.core.resources.Base64;

import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;

import com.meterware.httpunit.WebRequest;
import java.io.*;
import org.eclipse.core.runtime.CoreException;

/**
 * Base class for all Eclipse Web server tests. Providers helper methods common
 * to all server tests.
 */
public class AbstractServerTest {
	protected void setAuthentication(WebRequest request) {
		setAuthentication(request, "test", "test");
	}

	public static InputStream getJsonAsStream(String json) throws UnsupportedEncodingException {
		return new ByteArrayInputStream(json.getBytes("UTF-8")); //$NON-NLS-1$
	}

	public void setUpAuthorization() throws CoreException {
		//by default allow tests to modify anything
		AuthorizationService.addUserRight("test", "/");
	}

	protected void setAuthentication(WebRequest request, String user, String pass) {

		try {
			request.setHeaderField("Authorization", "Basic " + new String(Base64.encode((user + ':' + pass).getBytes()), "UTF8"));
		} catch (UnsupportedEncodingException e) {
			// this should never happen
			e.printStackTrace();
		}

	}

}
