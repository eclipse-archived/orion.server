package org.eclipse.orion.internal.server.hosting;

import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.orion.internal.server.servlets.hosting.*;
import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;

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
	 * Updates to this map occur serially and are done by {@link #start(SiteConfiguration, WebUser, String)}
	 * and {@link #stop(SiteConfiguration, WebUser)}.<p>
	 * 
	 * Reads may occur concurrently with updates and other reads, and are done by {@link #get(String)}
	 * and {@link #get(SiteConfiguration, WebUser)}. This should be OK since map operations on ConcurrentMap
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
	public void start(SiteConfiguration siteConfig, WebUser user, String editServer) throws SiteHostingException {
		synchronized (sites) {
			if (get(siteConfig, user) != null) {
				return; // Already started; nothing to do
			}

			String host = getNextHost(siteConfig.getHostHint());

			try {
				IHostedSite result = sites.putIfAbsent(host, new HostedSite(siteConfig, user, host, editServer));
				if (result != null) {
					// Should never happen, since writes are done serially by start()/stop()
					throw new ConcurrentModificationException("Table was modified concurrently");
				}
			} catch (Exception e) {
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
	public void stop(SiteConfiguration siteConfig, WebUser user) throws SiteHostingException {
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
	public IHostedSite get(SiteConfiguration siteConfig, WebUser user) {
		// Note this may overlap with a concurrent start()/stop() call that modifies the map
		String id = siteConfig.getId();
		String userName = user.getName();
		for (IHostedSite site : sites.values()) {
			if (site.getSiteConfigurationId().equals(id) && site.getUserName().equals(userName)) {
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

	/**
	 * @param host A host in the form <code>hostname:port</code>.
	 * @return The hosted site running at <code>host</code>, or null if <code>host</code>
	 * is not a running hosted site.
	 */
	IHostedSite get(String host) {
		// Note this may overlap with a concurrent start()/stop() call that modifies the map
		return sites.get(host);
	}

	/**
	 * Gets the next available host to use.
	 * 
	 * @param hint A hint to use for determining the hostname when subdomains are available. 
	 * May be <code>null</code>.
	 * @return The host, which will have the form <code>hostname:port</code>.
	 * @throws NoMoreHostsException If no more hosts are available (meaning all IPs and domains 
	 * from the hosting configuration have been allocated).
	 */
	private String getNextHost(String hint) throws NoMoreHostsException {
		hint = hint == null || hint.equals("") ? "site" : hint; //$NON-NLS-1$ //$NON-NLS-2$
		final String portSuffix = ":" + this.config.getHostingPort(); //$NON-NLS-1$

		synchronized (sites) {
			String host = null;

			for (String value : config.getHosts()) {
				int pos = value.lastIndexOf("*"); //$NON-NLS-1$
				if (pos != -1) {
					// It's a domain wildcard
					final String rest = value.substring(pos + 1);

					// Append digits if necessary to get a unique hostname
					String candidate = hint + rest + portSuffix;
					for (int i = 0; isHosted(candidate); i++) {
						candidate = hint + (Integer.toString(i)) + rest + portSuffix;
					}
					host = candidate;
					break;
				} else {
					String candidate = value + portSuffix;
					if (!isHosted(candidate)) {
						host = candidate;
						break;
					}
				}
			}

			if (host == null) {
				throw new NoMoreHostsException("No more hosts available");
			}
			return host;
		}
	}

}