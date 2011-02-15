package org.eclipse.orion.internal.server.hosting;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
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
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;

/**
 * Handles requests for URIs that are part of a running hosted site.
 * Requests should have the Host as the first segement in the request uri
 * <code>/192.168.0.2/foo/bar.html</code>
 */
public class HostedSiteServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	// FIXME
	private static final String FILE_SERVLET_ALIAS = "/file";
	private static final String USER = "mark";

	// FIXME remove these copied variables
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
		IPath path = new Path(pathInfo == null ? "" : pathInfo); //$NON-NLS-1$
		if (path.segmentCount() > 0) {
			String hostedHost = path.segment(0);
			HostedSite site = HostingActivator.getDefault().getHostingService().get(hostedHost);
			if (site != null) {
				URL url = getMappedURL(site, path.removeFirstSegments(1).makeAbsolute());
				serve(req, resp, url);
			} else {
				
			}
		} else {
			super.doGet(req, resp);
		}
	}
	
	/**
	 * Returns a URL 
	 * rewriting pathInfo using the most-specific
	 * @param site
	 * @param pathInfo
	 * @return
	 * @throws MalformedURLException If the target mapping is not valid URL
	 */
	private URL getMappedURL(HostedSite site, IPath pathInfo) throws MalformedURLException {
		Map<String, String> map = site.getMappings();

		IPath originalPath = pathInfo;
		IPath path = originalPath;
		String base = null;
		String rest = null;
		int count = path.segmentCount();
		for (int i = 0; i <= count; i++) {
			base = map.get(path.toString());
			if (base != null) {
				rest = originalPath.removeFirstSegments(count - i).toString();
				break;
			}
			path = path.removeLastSegments(1);
		}

		if (base != null) {
			String result = base + (rest.length() == 0 || rest.startsWith("/") ? "" : "/") + rest; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			
			// FIXME this doesn't handle absolute URIs with no protocl eg (/foo/bar.txt)
			// maybe another data structure would be better
			return new URL(result);
		}
		return null;
	}
	
	private void serve(HttpServletRequest req, HttpServletResponse resp, URL url) throws ServletException, IOException {
		String pathInfo;
		// FIXME Need a better way of getting files from this server
		if ("localhost".equals(url.getHost())) { //$NON-NLS-1$
			// Somehow I need to pull this from my workspace

			// FIXME: This fails if you haven't logged in because the alias will not be present
			pathInfo = url.getPath().substring(url.getPath().indexOf(FILE_SERVLET_ALIAS) + FILE_SERVLET_ALIAS.length());
			IPath filePath = pathInfo == null ? Path.ROOT : new Path(pathInfo);
			IFileStore file = tempGetFileStore(filePath, USER/*req.getRemoteUser()*/);
			if (file == null) {
				handleException(resp, new ServerStatus(IStatus.ERROR, 404, NLS.bind("File not found: {0}", filePath), null));
				//return;
			}
			if (fileSerializer.handleRequest(req, resp, file)) {
				//return;
			}
		} else {
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			// TODO: forward headers to proxy request here
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

}
