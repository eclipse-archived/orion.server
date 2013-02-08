/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.*;

/**
 * Controls which URLs found in JSON API response bodies will be "unqualified" (rewritten to remove the hostname 
 * and port of this server).
 */
public abstract class JsonURIUnqualificationStrategy {
	/**
	 * A strategy that unqualifies all URLs in the JSON body.
	 */
	public static final JsonURIUnqualificationStrategy ALL = new JsonURIUnqualificationStrategy() {
		@Override
		protected URI unqualifyObjectProperty(String key, URI uri, String scheme, String hostname, int port) {
			return unqualifyURI(uri, scheme, hostname, port);
		}

		@Override
		protected URI unqualifyArrayValue(int index, URI uri, String scheme, String hostname, int port) {
			return unqualifyURI(uri, scheme, hostname, port);
		}
	};
	/**
	 * A strategy that unqualifies only the URLs that are values of a "Location" key in JSON objects.
	 */
	public static final JsonURIUnqualificationStrategy LOCATION_ONLY = new JsonURIUnqualificationStrategy() {
		@Override
		protected URI unqualifyObjectProperty(String key, URI uri, String scheme, String hostname, int port) {
			if (!ProtocolConstants.KEY_LOCATION.equals(key))
				return uri;
			else
				return unqualifyURI(uri, scheme, hostname, port);
		}

		@Override
		protected URI unqualifyArrayValue(int index, URI uri, String scheme, String hostname, int port) {
			return uri;
		}
	};

	public void run(HttpServletRequest req, Object result) {
		rewrite(result, req.getScheme(), req.getServerName(), req.getServerPort(), req.getContextPath());
	}

	private void rewrite(JSONObject json, String scheme, String hostname, int port, String contextPath) {
		String[] names = JSONObject.getNames(json);
		if (names == null)
			return;
		for (String name : names) {
			Object o = json.opt(name);
			if (o instanceof URI) {
				try {
					URI uri = (URI) o;
					if ("orion".equals(uri.getScheme())) {
						uri = new URI(null, null, contextPath + uri.getPath(), uri.getQuery(), uri.getFragment());
					}
					json.put(name, unqualifyObjectProperty(name, uri, scheme, hostname, port));
				} catch (JSONException e) {
				} catch (URISyntaxException e) {
				}
			} else if (o instanceof String) {
				String string = (String) o;
				if (string.startsWith(scheme) || string.startsWith("orion:/")) {
					try {
						URI uri = new URI(string);
						if ("orion".equals(uri.getScheme())) {
							uri = new URI(null, null, contextPath + uri.getPath(), uri.getQuery(), uri.getFragment());
						}
						json.put(name, unqualifyObjectProperty(name, uri, scheme, hostname, port));
					} catch (JSONException e) {
					} catch (URISyntaxException e) {
					}
				}
			} else {
				rewrite(o, scheme, hostname, port, contextPath);
			}
		}
	}

	private void rewrite(Object o, String scheme, String hostname, int port, String contextPath) {
		if (o instanceof JSONObject) {
			rewrite((JSONObject) o, scheme, hostname, port, contextPath);
		} else if (o instanceof JSONArray) {
			JSONArray a = (JSONArray) o;
			for (int i = 0; i < a.length(); i++) {
				Object v = a.opt(i);
				if (v instanceof URI) {
					try {
						a.put(i, unqualifyArrayValue(i, (URI) v, scheme, hostname, port));
					} catch (JSONException e) {
					}
				} else if (v instanceof String) {
					String string = (String) v;
					if (string.startsWith(scheme)) {
						try {
							a.put(i, unqualifyArrayValue(i, new URI(string), scheme, hostname, port));
						} catch (JSONException e) {
						} catch (URISyntaxException e) {
						}
					}
				} else {
					rewrite(v, scheme, hostname, port, contextPath);
				}
			}
		}
	}

	protected abstract URI unqualifyObjectProperty(String key, URI uri, String scheme, String hostname, int port);

	protected abstract URI unqualifyArrayValue(int index, URI uri, String scheme, String hostname, int port);

	protected static URI unqualifyURI(URI uri, String scheme, String hostname, int port) {
		URI simpleURI = uri;
		int uriPort = uri.getPort();
		if (uriPort == -1) {
			uriPort = getDefaultPort(uri.getScheme());
		}
		if (scheme.equals(uri.getScheme()) && hostname.equals(uri.getHost()) && port == uriPort) {
			try {
				simpleURI = new URI(null, null, null, -1, uri.getPath(), uri.getQuery(), uri.getFragment());
			} catch (URISyntaxException e) {
				simpleURI = uri;
			}
		}
		return simpleURI;
	}

	private static int getDefaultPort(String scheme) {
		if ("http".equalsIgnoreCase(scheme)) {
			return 80;
		}
		if ("https".equalsIgnoreCase(scheme)) {
			return 443;
		}
		return -1;
	}
}
