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
 * Requests must have the hosted site's Host name as the first segment in the path, eg:
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
				IPath mappedPath = getMapped(site, path.removeFirstSegments(1).makeAbsolute());
				serve(req, resp, mappedPath);
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
	 * @return The rewritten path. May be an absolute path to a file in the Orion workspace (eg. 
	 * <code>/ProjectA/foo/bar.txt</code>), or point to another site, in which case it will have a 
	 * device part (eg. <code>http://foo.com/bar.txt</code>)
	 */
	private IPath getMapped(HostedSite site, IPath pathInfo) {
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
			return new Path(base).append(rest);
		}
		// No mappings were defined. What to do?
		return null;
	}
	
	private void serve(HttpServletRequest req, HttpServletResponse resp, IPath path) throws ServletException, IOException {
		if (path.getDevice() == null) { //$NON-NLS-1$
			// FIXME: 
			// Check access to workspace for the user who launched this build
			// Pull the file at the given path from the workspace 
			
			// FIXME: This fails if you haven't logged in because the alias will not be present
			String pathInfo = path.toString();
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
			URL url = null;
			try {
				url = new URL(path.toString());
			} catch (MalformedURLException e) {
				// FIXME: nicer http status code
				throw e;
			}
			
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
