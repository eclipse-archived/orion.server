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
package org.eclipse.orion.internal.server.servlets;

import java.net.URI;
import java.net.URISyntaxException;

public class Util {

	/**
	 * Converts URI to a URI with the given authority.
	 * 
	 * @param uri
	 * @param authority
	 * @return
	 */
	public static URI getURIWithAuthority(URI uri, String authority) {
		// TODO Local FS seems to fail when the authority is set
		try {
			if (uri.getScheme() == null)
				return new URI("file", uri.getPath(), null); //$NON-NLS-1$
			if ("gitfs".equals(uri.getScheme())) //$NON-NLS-1$
				return new URI(uri.getScheme(), authority, uri.getPath(), uri.getQuery(), uri.getFragment());
			return uri;
		} catch (URISyntaxException e) {
			// TODO:
			e.printStackTrace();
		}
		return null;
	}
}
