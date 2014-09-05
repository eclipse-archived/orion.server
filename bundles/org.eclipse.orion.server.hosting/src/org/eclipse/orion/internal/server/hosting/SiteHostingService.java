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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.metastore.UserInfo;

/**
 * Provides a same-server implementation of ISiteHostingService. Maintains a table of 
 * hosted sites for this purpose. This table is not persisted, so site launches are only
 * active until the server stops.
 */
public class SiteHostingService implements ISiteHostingService {

	private final SiteHostingConfig config;

	/**
	 * Key: Host, in the form <code>hostname:port</code>.<br>
	 * Value: The hosted site associated with the host.<p>
	 * 
	 * Updates to this map occur serially and are done by {@link #start(SiteInfo, UserInfo, String)}
	 * and {@link #stop(SiteInfo, UserInfo)}.<p>
	 * 
	 * Reads may occur concurrently with updates and other reads, and are done by {@link #get(String)}
	 * and {@link #get(SiteInfo, UserInfo)}. This should be OK since map operations on ConcurrentMap
	 * are thread-safe.
	 */
	private ConcurrentMap<String, IHostedSite> sites;

	/**
	 * Creates the site hosting service.
	 * @param config The site hosting configuration
	 */
	public SiteHostingService(SiteHostingConfig config) {
		this.config = config;
		this.sites = new ConcurrentHashMap<String, IHostedSite>();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService#start(org.eclipse.orion.internal.server.servlets.site.SiteConfiguration, org.eclipse.orion.internal.server.servlets.workspace.WebUser, java.lang.String)
	 */
	@Override
	public void start(SiteInfo siteConfig, UserInfo user, String editServer, URI requestURI) throws SiteHostingException {
		synchronized (sites) {
			if (get(siteConfig, user) != null) {
				return; // Already started; nothing to do
			}
			String host = null;
			try {
				URI url = acquireURL(siteConfig.getHostHint(), requestURI);
				host = url.getHost();
				IHostedSite result = sites.putIfAbsent(host, new HostedSite(siteConfig, user, host, editServer, url.toString()));
				if (result != null) {
					// Should never happen, since writes are done serially by start()/stop()
					throw new ConcurrentModificationException("Table was modified concurrently");
				}
			} catch (Exception e) {
				if (host != null)
					sites.remove(host);
				throw new SiteHostingException(e.getMessage(), e);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService#stop(org.eclipse.orion.internal.server.servlets.site.SiteConfiguration, org.eclipse.orion.internal.server.servlets.workspace.WebUser)
	 */
	@Override
	public void stop(SiteInfo siteConfig, UserInfo user) throws SiteHostingException {
		synchronized (sites) {
			IHostedSite site = get(siteConfig, user);
			if (site == null) {
				return; // Already stopped; nothing to do
			}

			if (!sites.remove(site.getHost(), site)) {
				throw new ConcurrentModificationException("Table was modified concurrently");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService#get(org.eclipse.orion.internal.server.servlets.site.SiteConfiguration, org.eclipse.orion.internal.server.servlets.workspace.WebUser)
	 */
	@Override
	public IHostedSite get(SiteInfo siteConfig, UserInfo user) {
		// Note this may overlap with a concurrent start()/stop() call that modifies the map
		String id = siteConfig.getId();
		String userId = user.getUniqueId();
		for (IHostedSite site : sites.values()) {
			if (site.getSiteConfigurationId().equals(id) && site.getUserId().equals(userId)) {
				return site;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService#isHosted(java.lang.String)
	 */
	@Override
	public boolean isHosted(String host) {
		// Note this may overlap with a concurrent start()/stop() call that modifies the map
		return get(host) != null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService#matchesVirtualHost(java.lang.String)
	 */
	@Override
	public boolean matchesVirtualHost(String host) {
		List<String> hosts = config.getHosts();
		for (String h : hosts) {
			if (h.equals(host)) {
				return true;
			} else {
				// Request URI does not matter here since we only care about the HostPattern's host, not scheme/port.
				HostPattern pattern;
				try {
					pattern = getHostPattern(h, new URI("http", null, host, -1, null, null, null));
					String configHost = pattern.getHost();
					if (configHost != null && host.endsWith(configHost.replace("*", ""))) { //$NON-NLS-1$ //$NON-NLS-2$
						return true;
					}
				} catch (URISyntaxException e) {
					// Should not happen
				}
			}
		}
		return false;
	}

	/**
	 * @param host A host in the form <code>hostname:port</code>.
	 * @return The hosted site running at <code>host</code>, or null if <code>host</code>
	 * is not a running hosted site.
	 */
	public IHostedSite get(String host) {
		// Note this may overlap with a concurrent start()/stop() call that modifies the map
		return sites.get(host);
	}

	/**
	 * Gets the next available URL where a site may be hosted.
	 * 
	 * @param hint A hint to use for determining the hostname when subdomains are available. May be <code>null</code>.
	 * @param requestURI The incoming request URI
	 * @return The host, which will have the form <code>hostname:port</code>.
	 * @throws NoMoreHostsException If no more hosts are available (meaning all IPs and domains 
	 * from the hosting configuration have been allocated).
	 * @throws BadHostnameException If a host pattern or hint led to an invalid URL being generated.
	 */
	private URI acquireURL(String hint, URI requestURI) throws SiteHostingException {
		hint = hint == null || hint.equals("") ? "site" : hint; //$NON-NLS-1$ //$NON-NLS-2$
		synchronized (sites) {
			URI result = null;
			for (String value : config.getHosts()) {
				try {
					HostPattern pattern = getHostPattern(value, requestURI);
					String host = pattern.getHost();
					if (pattern.isWildcard()) {
						// It's a domain wildcard
						final String rest = "." + pattern.getWildcardDomain(); //$NON-NLS-1$

						// Append digits if necessary to get a unique hostname
						String candidate = hint + rest;
						for (int i = 0; isHosted(candidate); i++) {
							candidate = hint + (Integer.toString(i)) + rest;
						}
						result = new URI(pattern.getScheme(), null, candidate, pattern.getPort(), null, null, null);
						break;
					} else {
						if (!isHosted(host)) {
							result = new URI(pattern.getScheme(), null, host, pattern.getPort(), null, null, null);
							break;
						}
					}
				} catch (URISyntaxException e) {
					// URI wasn't valid, either because a bad hint was provided or the server was configured with a bad HostPattern.
					LogHelper.log(e);
					if (isHintValid(hint))
						throw new BadHostnameException("Invalid virtual host suffix was provided. Contact your administrator.", e);
					throw new BadHostnameException("Invalid host hint. Only URI hostname characters are permitted.", e);
				}
			}

			if (result == null) {
				throw new NoMoreHostsException("No more hosts available");
			}
			return result;
		}
	}

	private boolean isHintValid(String hint) {
		try {
			new URI("http", null, hint, -1, null, null, null);
			return true;
		} catch (URISyntaxException e) {
			return false;
		}
	}

	/**
	 * @param pattern
	 * @param requestURI Used as a fallback to assign scheme and port when the pattern does not specify them.
	 * @return
	 */
	private HostPattern getHostPattern(String pattern, URI requestURI) {
		String scheme = null;
		int port = -1;
		// Parse scheme
		if (pattern.startsWith("http://") || pattern.startsWith("https://")) {
			int schemeEnd = pattern.indexOf("://");
			scheme = pattern.substring(0, schemeEnd);
			pattern = pattern.substring(schemeEnd + "://".length(), pattern.length());
			if ("https".equals(scheme))
				port = 443;
			else if ("http".equals(scheme))
				port = 80;
		}
		// Parse host and port (if present)
		String hostPart = pattern;
		int pos;
		if ((pos = pattern.lastIndexOf(":")) != -1 && pos < pattern.length() - 1) { //$NON-NLS-1$
			hostPart = pattern.substring(0, pos);
			try {
				port = Integer.parseInt(pattern.substring(pos + 1, pattern.length()));
			} catch (NumberFormatException e) {
			}
		}

		if (scheme == null) {
			scheme = requestURI.getScheme();
		}
		if (port == -1) {
			port = requestURI.getPort();
		}
		return new HostPattern(scheme, hostPart, defaultPort(scheme, port));
	}

	private int defaultPort(String scheme, int port) {
		if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80))
			return -1;
		return port;
	}

	/**
	 * Represents a parsed entry from the orion.core.virtualHosts setting.
	 */
	private class HostPattern {
		private String scheme;
		private String host;
		private int port;
		private boolean isWildcard = false;
		private String wildcardDomain = null;

		public HostPattern(String scheme, String host, int port) {
			this.scheme = scheme;
			this.host = host;
			this.port = port;
			if (host != null) {
				int pos = host.lastIndexOf("*");
				isWildcard = (pos >= 0 && pos < host.length() - 2 && host.charAt(pos + 1) == '.');
				if (isWildcard) {
					wildcardDomain = host.substring(pos + 2, host.length());
				}
			}
		}

		String getScheme() {
			return scheme;
		}

		String getHost() {
			return host;
		}

		String getWildcardDomain() {
			return wildcardDomain;
		}

		int getPort() {
			return port;
		}

		boolean isWildcard() {
			return isWildcard;
		}
	}
}