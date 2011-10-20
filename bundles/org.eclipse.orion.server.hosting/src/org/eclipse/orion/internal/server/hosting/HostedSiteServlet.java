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
import java.util.*;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IAliasRegistry;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.ServletFileStoreHandler;
import org.eclipse.orion.internal.server.servlets.hosting.IHostedSite;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.configurator.ConfiguratorActivator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.mortbay.servlet.ProxyServlet;

/**
 * Handles requests for URIs that are part of a running hosted site.
 */
public class HostedSiteServlet extends OrionServlet {

	static class LocationHeaderServletResponseWrapper extends HttpServletResponseWrapper {

		private static final String LOCATION = "Location";
		private HttpServletRequest request;
		private IHostedSite site;

		public LocationHeaderServletResponseWrapper(HttpServletRequest request, HttpServletResponse response, IHostedSite site) {
			super(response);
			this.request = request;
			this.site = site;
		}

		private String mapLocation(String location) {
			Map<String, List<String>> mappings = site.getMappings();
			String bestMatch = "";
			String prefix = null;
			for (Iterator<Entry<String, List<String>>> iterator = mappings.entrySet().iterator(); iterator.hasNext();) {
				Entry<String, List<String>> entry = iterator.next();
				List<String> candidates = entry.getValue();
				for (Iterator<String> candidateIt = candidates.iterator(); candidateIt.hasNext();) {
					String candidate = candidateIt.next();
					if (location.startsWith(candidate) && candidate.length() > bestMatch.length()) {
						bestMatch = candidate;
						prefix = entry.getKey();
					}
				}
			}
			if (prefix != null) {
				String reverseMappedPath = prefix + location.substring(bestMatch.length());
				try {
					URI pathlessRequestURI = new URI(request.getScheme(), null, request.getServerName(), request.getServerPort(), null, null, null);
					return pathlessRequestURI.toString() + reverseMappedPath;
				} catch (URISyntaxException t) {
					// best effort
					System.err.println(t);
				}
			}
			return location;
		}

		@Override
		public void addHeader(String name, String value) {
			if (name.equals(LOCATION)) {
				String newLocation = mapLocation(value.trim());
				super.addHeader(name, newLocation);
			} else {
				super.addHeader(name, value);
			}
		}

		@Override
		public void setHeader(String name, String value) {
			if (name.equals(LOCATION)) {
				String newLocation = mapLocation(value.trim());
				super.setHeader(name, newLocation);
			} else {
				super.setHeader(name, value);
			}
		}
	}

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
		String queryString = req.getQueryString();
		IPath pathInfo = new Path(null /*don't parse host:port as device*/, pathInfoString == null ? "" : pathInfoString); //$NON-NLS-1$
		if (pathInfo.segmentCount() > 0) {
			String hostedHost = pathInfo.segment(0);
			IHostedSite site = HostingActivator.getDefault().getHostingService().get(hostedHost);
			if (site != null) {
				IPath path = pathInfo.removeFirstSegments(1);
				IPath contextPath = new Path(req.getContextPath());
				IPath contextlessPath = path.makeRelativeTo(contextPath).makeAbsolute();
				URI[] mappedPaths;
				try {
					mappedPaths = getMapped(site, contextlessPath, queryString);
				} catch (URISyntaxException e) {
					handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not create target URI	", e));
					return;
				}
				if (mappedPaths != null) {
					serve(req, resp, site, mappedPaths);
				} else {
					handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, NLS.bind("No mappings matched {0}", path), null));
				}
			} else {
				String msg = NLS.bind("Hosted site {0} is stopped", hostedHost);
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
	 * @param queryString 
	 * @return The rewritten path. May be either:<ul>
	 * <li>A path to a file in the Orion workspace, eg. <code>/ProjectA/foo/bar.txt</code></li>
	 * <li>An absolute URL pointing to another site, eg. <code>http://foo.com/bar.txt</code></li>
	 * </ul>
	 * @throws URISyntaxException 
	 */
	private URI[] getMapped(IHostedSite site, IPath pathInfo, String queryString) throws URISyntaxException {
		final Map<String, List<String>> map = site.getMappings();
		final IPath originalPath = pathInfo;
		IPath path = originalPath.removeTrailingSeparator();
		List<String> base = null;
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
			URI[] result = new URI[base.size()];
			for (int i = 0; i < result.length; i++) {
				URI uri = rest.equals("") ? new URI(base.get(i)) : URIUtil.append(new URI(base.get(i)), rest);
				result[i] = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), queryString, uri.getFragment());
			}
			return result;
		}
		// No mapping for /
		return null;
	}

	private void serve(HttpServletRequest req, HttpServletResponse resp, IHostedSite site, URI[] mappedPaths) throws ServletException, IOException {
		if (mappedPaths[0].getScheme() == null) {
			if ("GET".equals(req.getMethod())) { //$NON-NLS-1$
				for (int i = 0; i < mappedPaths.length; i++) {
					boolean failEarlyOn404 = i + 1 < mappedPaths.length;
					if (serveOrionFile(req, resp, site, new Path(mappedPaths[i].getPath()), failEarlyOn404))
						return;
				}
			} else {
				String message = "Only GET method is supported for workspace paths";
				handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_METHOD_NOT_ALLOWED, NLS.bind(message, mappedPaths), null));
			}
		} else {
			proxyRemotePath(req, new LocationHeaderServletResponseWrapper(req, resp, site), mappedPaths);
		}
	}

	// returns true if the request has been served, false if not (only if failEarlyOn404 is true)
	private boolean serveOrionFile(HttpServletRequest req, HttpServletResponse resp, IHostedSite site, IPath path, boolean failEarlyOn404) throws ServletException, IOException {
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
			if (file == null || !file.fetchInfo().exists()) {
				if (failEarlyOn404) {
					return false;
				}
				handleException(resp, new ServerStatus(IStatus.ERROR, 404, NLS.bind("File not found: {0}", filePath), null));
			}
			if (fileSerializer.handleRequest(req, resp, file)) {
				//return;
			}
			// end copied

			if (file != null) {
				addEditHeaders(resp, site, path);
				addContentTypeHeader(resp, path);
			}
		} else {
			ConfiguratorActivator.getDefault().getAuthService().setUnauthrizedUser(req, resp, null);
		}
		return true;
	}

	private void addEditHeaders(HttpServletResponse resp, IHostedSite site, IPath path) {
		resp.addHeader("X-Edit-Server", site.getEditServerUrl() + "/edit/edit.html#"); //$NON-NLS-1$ //$NON-NLS-2$
		resp.addHeader("X-Edit-Token", FILE_SERVLET_ALIAS + path.toString()); //$NON-NLS-1$
	}

	private void addContentTypeHeader(HttpServletResponse resp, IPath path) {
		String mimeType = getServletContext().getMimeType(path.lastSegment());
		if (mimeType != null)
			resp.addHeader("Content-Type", mimeType);
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

	private void proxyRemotePath(HttpServletRequest req, HttpServletResponse resp, URI[] mappedPaths) throws IOException, ServletException, UnknownHostException {
		try {
			URL[] mappedURLs = new URL[mappedPaths.length];
			for (int i = 0; i < mappedPaths.length; i++) {
				mappedURLs[i] = new URL(mappedPaths[i].toString());
			}
			proxyRemoteUrl(req, resp, mappedURLs);
		} catch (MalformedURLException e) {
			String message = NLS.bind("Malformed remote URL: {0}", mappedPaths.toString());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, message, e));
			return;
		} catch (UnknownHostException e) {
			String message = NLS.bind("Unknown host {0}", e.getMessage());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, message, e));
		} catch (Exception e) {
			String message = NLS.bind("An error occurred while retrieving {0}", mappedPaths.toString());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, e));
		}
	}

	private void proxyRemoteUrl(HttpServletRequest req, HttpServletResponse resp, final URL[] mappedURLs) throws IOException, ServletException, UnknownHostException {
		for (int i = 0; i < mappedURLs.length; i++) {
			boolean failEarlyOn404 = i + 1 < mappedURLs.length;
			ProxyServlet proxy = new RemoteURLProxyServlet(mappedURLs[i], failEarlyOn404);
			proxy.init(getServletConfig());
			try {
				// TODO: May want to avoid console noise from 4xx response codes?
				traceRequest(req);
				try {
					proxy.service(req, resp);
				} catch (NotFoundException ex) {
					// ignore - this exception is only thrown in the "fail early on 404" case.
				}
			} finally {
				proxy.destroy();
			}
		}
	}
}
