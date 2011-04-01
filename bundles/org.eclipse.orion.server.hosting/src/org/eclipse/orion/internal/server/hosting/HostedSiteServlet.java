/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.hosting;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IAliasRegistry;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.file.ServletFileStoreHandler;
import org.eclipse.orion.internal.server.servlets.hosting.IHostedSite;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.mortbay.servlet.ProxyServlet;

/**
 * Handles requests for URIs that are part of a running hosted site. Requests must 
 * have the desired hosted site's Host as the first segment in the pathInfo, for example: 
 * <code>/<u>127.0.0.2:8080</u>/foo/bar.html</code> or <code>/<u>mysite.foo.net:8080</u>/bar/baz.html</code>.
 */
public class HostedSiteServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	private static final String WORKSPACE_SERVLET_ALIAS = "/workspace"; //$NON-NLS-1$
	private static final String FILE_SERVLET_ALIAS = "/file"; //$NON-NLS-1$

	// FIXME these variables are copied from fileservlet
	private ServletResourceHandler<IFileStore> fileSerializer;
	private final URI rootStoreURI;
	private IAliasRegistry aliasRegistry;

	public HostedSiteServlet() {
		aliasRegistry = Activator.getDefault();
		rootStoreURI = Activator.getDefault().getRootLocationURI();
		fileSerializer = new ServletFileStoreHandler(rootStoreURI, getStatusHandler());
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Handled by service()
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Handled by service()
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Handled by service()
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Handled by service()
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfoString = req.getPathInfo();
		IPath pathInfo = new Path(null /*don't parse host:port as device*/, pathInfoString == null ? "" : pathInfoString); //$NON-NLS-1$
		if (pathInfo.segmentCount() > 0) {
			String hostedHost = pathInfo.segment(0);
			IHostedSite site = HostingActivator.getDefault().getHostingService().get(hostedHost);
			if (site != null) {
				IPath path = pathInfo.removeFirstSegments(1).makeAbsolute();
				IPath mappedPath = getMapped(site, path);
				if (mappedPath != null) {
					serve(req, resp, site, mappedPath);
				} else {
					handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("No mappings matched {0}", path), null));
				}
			} else {
				String msg = NLS.bind("Hosted site {0} not found", hostedHost);
				handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		} else {
			super.doGet(req, resp);
		}
	}

	/**
	 * Returns a path constructed by rewriting pathInfo using the most specific rule from the hosted
	 * site's mappings.
	 * @param site The hosted site.
	 * @param pathInfo Path to be rewritten.
	 * @return The rewritten path. May be either:<ul>
	 * <li>A path to a file in the Orion workspace, eg. <code>/ProjectA/foo/bar.txt</code></li>
	 * <li>An absolute URL pointing to another site, eg. <code>http://foo.com/bar.txt</code></li>
	 * </ul>
	 */
	private IPath getMapped(IHostedSite site, IPath pathInfo) {
		final Map<String, String> map = site.getMappings();
		final IPath originalPath = pathInfo;
		IPath path = originalPath;
		String base = null;
		String rest = null;
		final int count = path.segmentCount();
		for (int i = 0; i <= count; i++) {
			base = map.get(path.toString());
			if (base != null) {
				rest = originalPath.removeFirstSegments(count - i).toString();
				break;
			}
			path = path.removeLastSegments(1);
		}

		if (base != null) {
			return new Path(base).append(rest);
		}
		// No mapping for /
		return null;
	}

	private void serve(HttpServletRequest req, HttpServletResponse resp, IHostedSite site, IPath path) throws ServletException, IOException {
		if (path.getDevice() == null) {
			if ("GET".equals(req.getMethod())) { //$NON-NLS-1$
				serveOrionFile(req, resp, site, path);
			} else {
				String message = "Only GET method is supported for workspace paths";
				handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_METHOD_NOT_ALLOWED, NLS.bind(message, path), null));
			}
		} else {
			proxyRemotePath(req, resp, path);
		}
	}

	private void serveOrionFile(HttpServletRequest req, HttpServletResponse resp, IHostedSite site, IPath path) throws ServletException {
		String userName = site.getUserName();
		String workspaceId = site.getWorkspaceId();
		String workspaceUri = WORKSPACE_SERVLET_ALIAS + "/" + workspaceId; //$NON-NLS-1$
		boolean allow = false;
		// Check that user who launched the hosted site really has access to the workspace
		try {
			if (AuthorizationService.checkRights(userName, workspaceUri, "GET")) { //$NON-NLS-1$
				allow = true;
			}
		} catch (JSONException e) {
			throw new ServletException(e);
		}

		if (allow) {
			// FIXME: this code is copied from NewFileServlet, fix it
			// start copied
			String pathInfo = path.toString();
			IPath filePath = pathInfo == null ? Path.ROOT : new Path(pathInfo);
			IFileStore file = tempGetFileStore(filePath);
			if (file == null) {
				handleException(resp, new ServerStatus(IStatus.ERROR, 404, NLS.bind("File not found: {0}", filePath), null));
				//return;
			}
			if (fileSerializer.handleRequest(req, resp, file)) {
				//return;
			}
			// end copied

			if (file != null) {
				addEditHeaders(resp, site, path);
			}
		} else {
			String msg = NLS.bind("No rights to access {0}", workspaceUri);
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, msg, null));
		}
	}

	private void addEditHeaders(HttpServletResponse resp, IHostedSite site, IPath path) {
		resp.addHeader("X-Edit-Server", site.getEditServerUrl() + "/coding.html#"); //$NON-NLS-1$ //$NON-NLS-2$
		resp.addHeader("X-Edit-Token", FILE_SERVLET_ALIAS + path.toString()); //$NON-NLS-1$
	}

	// temp code for grabbing files from filesystem
	protected IFileStore tempGetFileStore(IPath path) {
		//first check if we have an alias registered
		if (path.segmentCount() > 0) {
			URI alias = aliasRegistry.lookupAlias(path.segment(0));
			if (alias != null)
				try {
					return EFS.getStore(alias).getFileStore(path.removeFirstSegments(1));
				} catch (CoreException e) {
					LogHelper.log(new Status(IStatus.WARNING, HostingActivator.PI_SERVER_HOSTING, 1, "An error occured when getting file store for path '" + path + "' and alias '" + alias + '\'', e)); //$NON-NLS-1$ //$NON-NLS-2$
					// fallback is to try the same path relatively to the root
				}
		}
		//assume it is relative to the root
		try {
			return EFS.getStore(rootStoreURI).getFileStore(path);
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.WARNING, HostingActivator.PI_SERVER_HOSTING, 1, "An error occured when getting file store for path '" + path + "' and root '" + rootStoreURI + '\'', e)); //$NON-NLS-1$ //$NON-NLS-2$
			// fallback and return null
		}

		return null;
	}

	private void proxyRemotePath(HttpServletRequest req, HttpServletResponse resp, IPath path) throws IOException, ServletException, UnknownHostException {
		try {
			URL url = new URL(path.toString());
			proxyRemoteUrl(req, resp, url);
		} catch (MalformedURLException e) {
			String message = NLS.bind("Malformed remote URL: {0}", path.toString());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, message, e));
			return;
		} catch (UnknownHostException e) {
			String message = NLS.bind("Unknown host {0}", e.getMessage());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, message, e));
		} catch (Exception e) {
			String message = NLS.bind("An error occurred while retrieving {0}", path.toString());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, e));
		}
	}

	private void proxyRemoteUrl(HttpServletRequest req, HttpServletResponse resp, final URL url) throws IOException, ServletException, UnknownHostException {
		ProxyServlet proxy = new RemoteURLProxyServlet(url);
		proxy.init(getServletConfig());
		try {
			// TODO: May want to avoid console noise from 4xx response codes?
			traceRequest(req);
			proxy.service(req, resp);
		} finally {
			proxy.destroy();
		}
	}
}
