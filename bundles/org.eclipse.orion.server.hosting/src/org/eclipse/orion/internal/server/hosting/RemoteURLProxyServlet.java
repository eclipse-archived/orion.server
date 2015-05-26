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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.util.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Override the Jetty ProxyServlet implementation. Do this to tolerate some unusual 
 * HTTP practices that Ajax libraries are using (see NOTE: below.) 
 */
public class RemoteURLProxyServlet extends ProxyServlet {

	private final Logger logger = LoggerFactory.getLogger(HostingActivator.PI_SERVER_HOSTING);

	{
		// Bug 346139
		////_DontProxyHeaders.add("host");
	}

	private HttpURI url;
	private final boolean failEarlyOn404;

	public RemoteURLProxyServlet(URL url, boolean failEarlyOn404) {
		try {
			this.url = new HttpURI(url.toURI());
			this.failEarlyOn404 = failEarlyOn404;
		} catch (URISyntaxException e) {
			//should be well formed
			throw new RuntimeException(e);
		}
	}

/*	@Override
	protected HttpURI proxyHttpURI(final String scheme, final String serverName, int serverPort, final String uri) throws MalformedURLException {
		return url;
	}

*/	/* (non-Javadoc)
	 * @see javax.servlet.Servlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
	 */
	public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		if ("CONNECT".equalsIgnoreCase(request.getMethod())) {
			////handleConnect(request, response);
		} else {
			String uri = request.getRequestURI();
			if (request.getQueryString() != null)
				uri += "?" + request.getQueryString();

			////HttpURI url = proxyHttpURI(request.getScheme(), request.getServerName(), request.getServerPort(), uri);

			URL rawURL = new URL(url.toString());
			URLConnection connection = rawURL.openConnection();
			connection.setAllowUserInteraction(false);

			// Set method
			HttpURLConnection http = null;
			if (connection instanceof HttpURLConnection) {
				http = (HttpURLConnection) connection;
				http.setRequestMethod(request.getMethod());
				http.setInstanceFollowRedirects(false); // NOTE
			}

			// check connection header
			String connectionHdr = request.getHeader("Connection");
			if (connectionHdr != null) {
				connectionHdr = connectionHdr.toLowerCase();
				if (connectionHdr.equals("keep-alive") || connectionHdr.equals("close"))
					connectionHdr = null;
			}

			// copy headers
			boolean xForwardedFor = false;
			Enumeration<String> enm = request.getHeaderNames();
			while (enm.hasMoreElements()) {
				// TODO could be better than this!
				String hdr = (String) enm.nextElement();
				String lhdr = hdr.toLowerCase();

				if ("host".equals(lhdr)) {
					// Bug 346139: set Host based on the destination URL being proxied
					int port = url.getPort();
					String realHost;
					if (port == -1 || port == rawURL.getDefaultPort())
						realHost = url.getHost();
					else
						realHost = url.getHost() + ":" + port;
					connection.addRequestProperty("Host", realHost);
				}

				////if (_DontProxyHeaders.contains(lhdr))
				////	continue;
				if (connectionHdr != null && connectionHdr.indexOf(lhdr) >= 0)
					continue;

				Enumeration<String> vals = request.getHeaders(hdr);
				while (vals.hasMoreElements()) {
					String val = (String) vals.nextElement();
					if (val != null) {
						connection.addRequestProperty(hdr, val);
						xForwardedFor |= "X-Forwarded-For".equalsIgnoreCase(hdr);
					}
				}
			}

			// Proxy headers
			connection.setRequestProperty("Via", "1.1 (jetty)");
			if (!xForwardedFor)
				connection.addRequestProperty("X-Forwarded-For", request.getRemoteAddr());

			// Bug 346139: prevent an infinite proxy loop by decrementing the Max-Forwards header
			Enumeration<String> maxForwardsHeaders = request.getHeaders("Max-Forwards");
			String maxForwardsHeader = null;
			while (maxForwardsHeaders.hasMoreElements()) {
				maxForwardsHeader = (String) maxForwardsHeaders.nextElement();
			}
			int maxForwards = 5;
			try {
				maxForwards = Math.max(0, Integer.parseInt(maxForwardsHeader));
			} catch (NumberFormatException e) {
				// Use default
			}
			if (maxForwards-- < 1) {
				response.sendError(HttpURLConnection.HTTP_BAD_GATEWAY, "Max-Forwards exceeded");
				return;
			}
			connection.addRequestProperty("Max-Forwards", "" + maxForwards);

			// a little bit of cache control
			String cache_control = request.getHeader("Cache-Control");
			if (cache_control != null && (cache_control.indexOf("no-cache") >= 0 || cache_control.indexOf("no-store") >= 0))
				connection.setUseCaches(false);

			// customize Connection

			try {
				connection.setDoInput(true);

				// do input thang!
				InputStream in = request.getInputStream();
				if (isOutputSupported(request)) {
					connection.setDoOutput(true);
					IO.copy(in, connection.getOutputStream());
				}

				// Connect                
				connection.connect();
			} catch (Exception e) {
				if (!(e instanceof UnknownHostException))
					logger.error("Error connecting to " + url, e);
			}

			InputStream proxy_in = null;

			// handler status codes etc.
			int code = 500;
			if (http != null) {
				proxy_in = http.getErrorStream();

				code = http.getResponseCode();
				if (failEarlyOn404 && code == 404) {
					// make sure this is thrown only in the "fail early on 404" case
					throw new NotFoundException();
				}
				response.setStatus(code);
			}

			if (proxy_in == null) {
				try {
					proxy_in = connection.getInputStream();
				} catch (Exception e) {
					if (!(e instanceof IOException))
						logger.error("Error reading input from " + url, e);
					if (http != null)
						proxy_in = http.getErrorStream();
				}
			}

			// clear response defaults.
			response.setHeader("Date", null);
			response.setHeader("Server", null);

			// set response headers
			int h = 0;
			String hdr = connection.getHeaderFieldKey(h);
			String val = connection.getHeaderField(h);
			while (hdr != null || val != null) {
				String lhdr = hdr != null ? hdr.toLowerCase() : null;
				////if (hdr != null && val != null && !_DontProxyHeaders.contains(lhdr))
				////	response.addHeader(hdr, val);

				h++;
				hdr = connection.getHeaderFieldKey(h);
				val = connection.getHeaderField(h);
			}
			response.addHeader("Via", "1.1 (jetty)");

			// Handle
			if (proxy_in != null)
				IO.copy(proxy_in, response.getOutputStream());
		}
	}

	private static boolean isOutputSupported(HttpServletRequest req) {
		String method = req.getMethod();
		return "POST".equals(method) || "PUT".equals(method);
	}
}
