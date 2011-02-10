package org.eclipse.orion.internal.server.servlets.host;

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
 */
public class HostedSiteServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	private static final String USER = "mark";
	private static final String FILE = "/file";

	// TODO remove these copied variables
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
		String host = req.getHeader("Host");
		if (isForHostedSite(host)) {
			String pathInfo = req.getPathInfo();
			URL url = this.rewrite(pathInfo);

			// FIXME Temporary hack for localhost
			if ("localhost".equals(url.getHost())) {
				// Somehow I need to pull this from my workspace

				// FIXME: This fails if you haven't logged in because the alias will not be present
				pathInfo = url.getPath().substring(url.getPath().indexOf(FILE) + FILE.length());
				IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
				IFileStore file = tempGetFileStore(path, USER/*req.getRemoteUser()*/);
				if (file == null) {
					handleException(resp, new ServerStatus(IStatus.ERROR, 404, NLS.bind("File not found: {0}", path), null));
					return;
				}
				if (fileSerializer.handleRequest(req, resp, file))
					return;
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
		} else {
			super.doGet(req, resp);
		}
	}

	// FIXME: should consult Hosted Sites Table
	private boolean isForHostedSite(String host) {
		return host.startsWith("127.0.0.") && !host.endsWith(".1");
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

	// FIXME use SiteConfig mapping
	private URL rewrite(String pathInfo) throws MalformedURLException {
		Map<String, String> map = getMap();

		IPath originalPath = new Path(pathInfo);
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
			String result = base + (rest.length() == 0 || rest.startsWith("/") ? "" : "/") + rest;
			return new URL(result);
		}
		return null;
	}

	// TODO read from elsewhere
	// Note: we don't tolerate trailing slashes
	private Map<String, String> getMap() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("/", "http://localhost:8080/file/C/static");
		map.put("/editor", "http://localhost:8080/file/D/web");
		map.put("/org.dojotoolkit", "http://localhost:8080/file/G");
		map.put("/openajax", "http://localhost:8080/file/H");
		map.put("/foo", "http://mamacdon.github.com");
		return map;
	}

}
