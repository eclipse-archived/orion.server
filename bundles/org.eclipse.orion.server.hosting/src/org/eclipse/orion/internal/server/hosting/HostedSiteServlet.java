package org.eclipse.orion.internal.server.hosting;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.core.IAliasRegistry;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.Util;
import org.eclipse.orion.internal.server.servlets.file.ServletFileStoreHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WebWorkspace;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;

/**
 * Handles requests for URIs that are part of a running hosted site. Requests must 
 * have the desired hosted site's Host as the first segment in the pathInfo, for example: 
 * <code>/<u>127.0.0.2:8080</u>/foo/bar.html</code>
 */
public class HostedSiteServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	// FIXME mamacdon remove these if possible
	private static final String FILE_SERVLET_ALIAS = "/file";
	private static final String USER = "mark";

	// FIXME mamacdon remove these copied variables
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
		String pathInfo = req.getPathInfo();
		IPath path = new Path(null /*don't parse host:port as device*/, pathInfo == null ? "" : pathInfo); //$NON-NLS-1$
		if (path.segmentCount() > 0) {
			String hostedHost = path.segment(0);
			HostedSite site = HostingActivator.getDefault().getHostingService().get(hostedHost);
			if (site != null) {
				IPath mappedPath = getMapped(site, path.removeFirstSegments(1).makeAbsolute());
				serve(req, resp, site, mappedPath);
			} else {
				String msg = NLS.bind("Hosted site {0} not found", hostedHost);
				handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
			}
		} else {
			super.doGet(req, resp);
		}
	}
	
	/**
	 * Returns a path constructed by rewriting pathInfo using the most specific rule from a hosted
	 * site's mappings.
	 * @param site The hosted site.
	 * @param pathInfo Path to be rewritten.
	 * @return The rewritten path. May be either:<ul>
	 * <li>A path to a file in the Orion workspace, eg. <code>/ProjectA/foo/bar.txt</code></li>
	 * <li>An absolute URL pointing to another site, eg. <code>http://foo.com/bar.txt</code></li>
	 * </ul>
	 */
	private IPath getMapped(HostedSite site, IPath pathInfo) {
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
		// No mapping were defined. What to do?
		return null;
	}
	
	private void serve(HttpServletRequest req, HttpServletResponse resp, HostedSite site, IPath path) throws ServletException, IOException {
		if (path.getDevice() == null) {
			serveOrionFile(req, resp, site, path);
		} else {
			proxyRemoteUrl(resp, path);
		}
	}

	private void serveOrionFile(HttpServletRequest req, HttpServletResponse resp, HostedSite site, IPath path) throws ServletException {
		String userName = site.getUserName();
		String workspaceUri = "/workspace/" + site.getWorkspaceId(); //$NON-NLS-1$
		boolean allow = false;
		// Check that user who launched the hosted site has access to the workspace
		try {
			if (AuthorizationService.checkRights(userName, workspaceUri, "GET")) { //$NON-NLS-1$
				allow = true;
			}
		} catch (JSONException e) {
			throw new ServletException(e);
		}
		
		// FIXME mamacdon: refactor elsewhere, remember the dev-server-url in the HostedSite 
		resp.addHeader("X-Edit-Server", "http://localhost:8080/");
		resp.addHeader("X-Edit-Token", "coding.html#/file" + path.toString());
		
		// FIXME mamacdon: this code is copied from NewFileServlet, fix it
		if (allow) {
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
		}
	}
	
	// FIXME temp junk for grabbing files from filesystem
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
			// FIXME mamacdon: nicer error
			throw e;
		}
		
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		// FIXME mamacdon: forward headers, catch remote errors and send 502 (?)
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
