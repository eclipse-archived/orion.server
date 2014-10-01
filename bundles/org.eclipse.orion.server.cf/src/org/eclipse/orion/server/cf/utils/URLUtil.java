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
package org.eclipse.orion.server.cf.utils;

import java.net.MalformedURLException;
import java.net.URL;

public class URLUtil {
	public static URL normalizeURL(URL url) {
		if (url == null)
			return null;

		String urlString = url.toString();
		if (urlString.endsWith("/")) {
			urlString = urlString.substring(0, urlString.length() - 1);
		}

		try {
			return new URL(urlString);
		} catch (MalformedURLException e) {
			return null;
		}
	}
}
