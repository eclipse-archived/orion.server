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
package org.eclipse.e4.webide.internal.server.useradmin.servlets;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.e4.webide.server.configurator.authentication.IAuthenticationService;
import org.eclipse.e4.webide.server.useradmin.UserAdminActivator;
import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.UserAdmin;

public class AdminHttpContext implements HttpContext {

	private Bundle bundle;
	private String bundlePath;

	public AdminHttpContext(Bundle bundle) {
		super();
		this.bundle = bundle;
	}

	public AdminHttpContext(Bundle bundle, String bundlePath) {
		super();
		this.bundle = bundle;
		this.bundlePath = bundlePath;
	}

	private static final String ADMIN_ROLE = "admin";

	@Override
	public boolean handleSecurity(HttpServletRequest req,
			HttpServletResponse resp) throws IOException {

		if (req.getRequestURI().startsWith("/usersstatic")) {
			return true; // access to static content: css, forms etc
		}

		if ("POST".equals(req.getMethod())) { // everyone can create a user
			return true;
		}

		if ("GET".equals(req.getMethod())
				&& req.getPathInfo().startsWith("/create")) {
			return true; // display add user form to everyone
		}

		String user = bundle
				.getBundleContext()
				.getService(
						bundle.getBundleContext().getServiceReference(
								IAuthenticationService.class))
				.authenticateUser(req, resp, null);

		UserAdmin userAdmin = UserAdminActivator.getDefault()
				.getUserAdminService();

		Authorization authorization = userAdmin.getAuthorization(userAdmin
				.getUser("login", user));

		if (authorization.hasRole(ADMIN_ROLE)) {
			return true;
		}

		resp.sendError(HttpServletResponse.SC_FORBIDDEN);
		resp.flushBuffer();
		return false;
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
			URL entryURL = (URL) entryPaths.nextElement();
			String entryPath = entryURL.getFile();

			if (bundlePath == null)
				result.add(entryPath);
			else
				result.add(entryPath.substring(bundlePath.length()));
		}
		return result;
	}

	@Override
	public String getMimeType(String name) {
		return null;
	}

}
