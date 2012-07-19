/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.basic;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;

public class BundleEntryHttpContext implements HttpContext {
	private Bundle bundle;
	private String bundlePath;

	public BundleEntryHttpContext(Bundle bundle) {
		this.bundle = bundle;
	}

	public BundleEntryHttpContext(Bundle bundle, Properties authProperties) {
		this.bundle = bundle;
	}

	public BundleEntryHttpContext(Bundle bundle, String bundlePath, Properties authProperties) {
		this(bundle, authProperties);
		if (bundlePath != null) {
			if (bundlePath.endsWith("/")) //$NON-NLS-1$
				bundlePath = bundlePath.substring(0, bundlePath.length() - 1);

			if (bundlePath.length() == 0)
				bundlePath = null;
		}
		this.bundlePath = bundlePath;
	}

	public String getMimeType(String arg0) {
		return null;
	}

	public boolean handleSecurity(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		return true;
	}

	public URL getResource(String resourceName) {
		if (bundlePath != null)
			resourceName = bundlePath + resourceName;

		int lastSlash = resourceName.lastIndexOf('/');
		if (lastSlash == -1)
			return null;

		if (resourceName.endsWith("/"))
			resourceName += "index.html";

		String path = resourceName.substring(0, lastSlash);
		if (path.length() == 0)
			path = "/"; //$NON-NLS-1$
		String file = resourceName.substring(lastSlash + 1);
		Enumeration<URL> entryPaths = bundle.findEntries(path, file, false);

		if (entryPaths != null && entryPaths.hasMoreElements())
			return entryPaths.nextElement();

		return null;
	}

	public Set<String> getResourcePaths(String path) {
		if (bundlePath != null)
			path = bundlePath + path;

		Enumeration<URL> entryPaths = bundle.findEntries(path, null, false);
		if (entryPaths == null)
			return null;

		Set<String> result = new HashSet<String>();
		while (entryPaths.hasMoreElements()) {
			URL entryURL = entryPaths.nextElement();
			String entryPath = entryURL.getFile();

			if (bundlePath == null)
				result.add(entryPath);
			else
				result.add(entryPath.substring(bundlePath.length()));
		}
		return result;
	}
}