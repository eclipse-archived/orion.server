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
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.file.ServletFileStoreHandler;
import org.eclipse.orion.internal.server.servlets.hosting.IHostedSite;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;

/**
 * Handles requests for URIs that are part of a running hosted site. Requests must 
 * have the desired hosted site's Host as the first segment in the pathInfo, for example: 
 * <code>/<u>127.0.0.2:8080</u>/foo/bar.html</code> or <code>/<u>mysite.foo.net:8080</u>/bar/baz.html</code>.
 */
public class HostedSiteServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	private static final String WORKSPACE_SERVLET_ALIAS = "/workspace/"; //$NON-NLS-1$

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
			serveOrionFile(req, resp, site, path);
		} else {
			proxyRemoteUrl(resp, path);
		}
	}

	private void serveOrionFile(HttpServletRequest req, HttpServletResponse resp, IHostedSite site, IPath path) throws ServletException {
		String userName = site.getUserName();
		String workspaceId = site.getWorkspaceId();
		String workspaceUri = WORKSPACE_SERVLET_ALIAS + workspaceId;
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
			IFileStore file = tempGetFileStore(filePath, userName);
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
		resp.addHeader("X-Edit-Server", site.getEditServerUrl() + "/coding.html#");
		resp.addHeader("X-Edit-Token", path.toString());
	}

	// temp code for grabbing files from filesystem
	protected IFileStore tempGetFileStore(IPath path, String authority) {
		//first check if we have an alias registered
		if (path.segmentCount() > 0) {
			URI alias = aliasRegistry.lookupAlias(path.segment(0));
			if (alias != null)
				try {
					return EFS.getStore(Util.getURIWithAuthority(alias, authority)).getFileStore(path.removeFirstSegments(1));
				} catch (CoreException e) {
					LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, "An error occured when getting file store for path '" + path + "' and alias '" + alias + "'", e));
					// fallback is to try the same path relatively to the root
				}
		}
		//assume it is relative to the root
		try {
			return EFS.getStore(Util.getURIWithAuthority(rootStoreURI, authority)).getFileStore(path);
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, "An error occured when getting file store for path '" + path + "' and root '" + rootStoreURI + "'", e));
			// fallback and return null
		}

		return null;
	}

	private void proxyRemoteUrl(HttpServletResponse resp, IPath path) throws MalformedURLException, IOException {
		URL url = null;
		try {
			url = new URL(path.toString());
		} catch (MalformedURLException e) {
			// FIXME: nicer error
			throw e;
		}

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		// FIXME: forward headers, send X-Forwarded-For (?), catch remote errors and send 502 (?)
		//				Enumeration<?> headerNames = req.getHeaderNames();
		//				while (headerNames.hasMoreElements()) {
		//					String name = (String) headerNames.nextElement();
		//					String value = req.getHeader(name);
		//					if ("Host".equals(name)) {
		//						continue;
		//					}
		//					connection.addRequestProperty(name, value);
		//				}
		//				for (int i = 0; true; i++) {
		//					String name = connection.getHeaderFieldKey(i);
		//					if (name == null) {
		//						break;
		//					}
		//					resp.setHeader(name, connection.getHeaderField(i));
		//				}
		IOUtilities.pipe(connection.getInputStream(), resp.getOutputStream(), true, true);
	}

}
