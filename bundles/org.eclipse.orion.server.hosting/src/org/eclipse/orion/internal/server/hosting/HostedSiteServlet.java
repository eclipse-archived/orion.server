/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.jetty.servlets.ProxyServlet;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.ServletFileStoreHandler;
import org.eclipse.orion.internal.server.servlets.hosting.IHostedSite;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.internal.server.servlets.workspace.WebWorkspace;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;

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
				String suffix = location.substring(bestMatch.length());
				String separator = suffix.length() > 0 && !prefix.endsWith("/") && !suffix.startsWith("/") ? "/" : "";
				String reverseMappedPath = prefix + separator + suffix;
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

	public HostedSiteServlet() {
		rootStoreURI = Activator.getDefault().getRootLocationURI();
	}

	@Override
	public void init() throws ServletException {
		super.init();
		fileSerializer = new ServletFileStoreHandler(rootStoreURI, getStatusHandler(), getServletContext());
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
		String queryString = getQueryString(req);
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
	 * Returns paths constructed by rewriting pathInfo using rules from the hosted site's mappings.
	 * Paths are ordered from most to least specific match.
	 * @param site The hosted site.
	 * @param pathInfo Path to be rewritten.
	 * @param queryString 
	 * @return The rewritten path. May be either:<ul>
	 * <li>A path to a file in the Orion workspace, eg. <code>/ProjectA/foo/bar.txt</code></li>
	 * <li>An absolute URL pointing to another site, eg. <code>http://foo.com/bar.txt</code></li>
	 * </ul>
	 * @return The rewritten paths. 
	 * @throws URISyntaxException 
	 */
	private URI[] getMapped(IHostedSite site, IPath pathInfo, String queryString) throws URISyntaxException {
		final Map<String, List<String>> map = site.getMappings();
		final IPath originalPath = pathInfo;
		IPath path = originalPath.removeTrailingSeparator();

		List<URI> uris = new ArrayList<URI>();
		String rest = null;
		final int count = path.segmentCount();
		for (int i = 0; i <= count; i++) {
			List<String> base = map.get(path.toString());
			if (base != null) {
				rest = originalPath.removeFirstSegments(count - i).toString();
				for (int j = 0; j < base.size(); j++) {
					URI uri = (rest.equals("") || rest.equals("/")) ? new URI(base.get(j)) : URIUtil.append(new URI(base.get(j)), rest);
					uris.add(new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), queryString, uri.getFragment()));
				}
			}
			path = path.removeLastSegments(1);
		}
		if (uris.size() == 0)
			// No mapping for /
			return null;
		else
			return uris.toArray(new URI[uris.size()]);
	}

	private void serve(HttpServletRequest req, HttpServletResponse resp, IHostedSite site, URI[] mappedURIs) throws ServletException, IOException {
		for (int i = 0; i < mappedURIs.length; i++) {
			URI uri = mappedURIs[i];
			// Bypass a 404 if any workspace or remote paths remain to be checked.
			boolean failEarlyOn404 = i + 1 < mappedURIs.length;
			if (uri.getScheme() == null) {
				if ("GET".equals(req.getMethod())) { //$NON-NLS-1$
					if (serveOrionFile(req, resp, site, new Path(uri.getPath()), failEarlyOn404))
						return;
				} else {
					String message = "Only GET method is supported for workspace paths";
					handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_METHOD_NOT_ALLOWED, NLS.bind(message, mappedURIs), null));
				}
			} else {
				if (proxyRemotePath(req, new LocationHeaderServletResponseWrapper(req, resp, site), uri, failEarlyOn404))
					return;
			}
		}
	}

	// returns true if the request has been served, false if not (only if failEarlyOn404 is true)
	private boolean serveOrionFile(HttpServletRequest req, HttpServletResponse resp, IHostedSite site, IPath path, boolean failEarlyOn404) throws ServletException {
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
			String msg = NLS.bind("No rights to access {0}", workspaceUri);
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, msg, null));
		}
		return true;
	}

	private void addEditHeaders(HttpServletResponse resp, IHostedSite site, IPath path) {
		resp.addHeader("X-Edit-Server", site.getEditServerUrl() + "/edit/edit.html#"); //$NON-NLS-1$ //$NON-NLS-2$
		resp.addHeader("X-Edit-Token", FILE_SERVLET_ALIAS + path.toString()); //$NON-NLS-1$
	}

	private void addContentTypeHeader(HttpServletResponse resp, IPath path) {
		String last = path.lastSegment();
		if (last != null) {
			String mimeType = getServletContext().getMimeType(last);
			if (mimeType != null)
				resp.addHeader("Content-Type", mimeType);
		}
	}

	// temp code for grabbing files from filesystem
	protected IFileStore tempGetFileStore(IPath path) {
		//path format is /workspaceId/projectName/[suffix]
		if (path.segmentCount() <= 1)
			return null;
		WebWorkspace workspace = WebWorkspace.fromId(path.segment(0));
		WebProject project = workspace.getProjectByName(path.segment(1));
		if (project == null)
			return null;
		try {
			return project.getProjectStore().getFileStore(path.removeFirstSegments(2));
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, NLS.bind("An error occurred when getting file store for path {0}", path), e));
			// fallback and return null
		}
		return null;
	}

	/**
	 * @return true if the request was served.
	 */
	private boolean proxyRemotePath(HttpServletRequest req, HttpServletResponse resp, URI remoteURI, boolean failEarlyOn404) throws IOException, ServletException, UnknownHostException {
		try {
			return proxyRemoteUrl(req, resp, new URL(remoteURI.toString()), failEarlyOn404);
		} catch (MalformedURLException e) {
			String message = NLS.bind("Malformed remote URL: {0}", remoteURI.toString());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, message, e));
		} catch (UnknownHostException e) {
			String message = NLS.bind("Unknown host {0}", e.getMessage());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, message, e));
		} catch (Exception e) {
			String message = NLS.bind("An error occurred while retrieving {0}", remoteURI.toString());
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, e));
		}
		return true;
	}

	/**
	 * @return true if the request was served.
	 */
	private boolean proxyRemoteUrl(HttpServletRequest req, HttpServletResponse resp, final URL mappedURL, boolean failEarlyOn404) throws IOException, ServletException, UnknownHostException {
		ProxyServlet proxy = new RemoteURLProxyServlet(mappedURL, failEarlyOn404);
		proxy.init(getServletConfig());
		try {
			// TODO: May want to avoid console noise from 4xx response codes?
			traceRequest(req);
			try {
				proxy.service(req, resp);
			} catch (NotFoundException ex) {
				// This exception is only thrown in the "fail early on 404" case, in which case
				// no output was written and we didn't serve the request.
				return false;
			}
		} finally {
			proxy.destroy();
		}
		// We served this request
		return true;
	}

	/**
	 * @param req The request.
	 * @return The query string of the request in its raw (not URL-encoded) form. This is suitable for passing as the 
	 * 'query' parameter to one of the multi-argument {@link URI} constructors.
	 */
	private static String getQueryString(HttpServletRequest req) {
		String query = req.getQueryString();
		if (query == null || "".equals(query)) //$NON-NLS-1$
			return query;
		String encoding = req.getCharacterEncoding();
		if (encoding == null)
			encoding = "UTF-8"; //$NON-NLS-1$
		try {
			return URLDecoder.decode(query, encoding);
		} catch (UnsupportedEncodingException e) {
			LogHelper.log(new Status(IStatus.WARNING, HostingActivator.PI_SERVER_HOSTING, "Cannot decode query string", e));
			return query;
		}
	}
}
