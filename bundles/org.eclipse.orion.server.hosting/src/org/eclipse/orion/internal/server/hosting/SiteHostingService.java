package org.eclipse.orion.internal.server.hosting;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.orion.internal.server.servlets.hosting.IHostedSite;
import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.hosting.SiteHostingException;
import org.eclipse.orion.internal.server.servlets.hosting.WrongHostingStatusException;
import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;

/**
 * Provides a same-server implementation of ISiteHostingService. Maintains a table of 
 * hosted sites for this purpose. This table is not persisted, so site launches are only
 * active until the server stops.
 */
public class SiteHostingService implements ISiteHostingService {
	
	private final int hostingPort;
	private Map<Key, HostedSite> sites;
	private String editServer;
	
	private Set<String> hosts;   // All hosts we've used, each has the form "hostname:port"
	private BitSet allocated;    // Bit i is set if the IP address 127.0.0.i has been allocated
	
	private Object hostLock = new Object();

	/**
	 * @param hostingPort The port that hosted sites are accessed on
	 */
	public SiteHostingService(int hostingPort /*, SiteHostingConfig config*/) {
		this.hostingPort = hostingPort;
		this.sites = new HashMap<Key, HostedSite>();
		this.editServer = "http://localhost:" +  System.getProperty("org.eclipse.equinox.http.jetty.http.port"); //$NON-NLS-1$ //$NON-NLS-2$
		
		this.hosts = new HashSet<String>();
		this.allocated = new BitSet(256);
		allocated.set(0);
		allocated.set(1);
		allocated.set(255);
	}
	
	@Override
	public void start(SiteConfiguration siteConfig, WebUser user) throws SiteHostingException {
		Key key = createKey(siteConfig);
		synchronized (sites) {
			if (sites.containsKey(key)) {
				throw new WrongHostingStatusException("Site is already started");
			}
			
			String host = acquireHost();
			sites.put(key, createValue(siteConfig, user, host, editServer));
		}
	}
	
	@Override
	public void stop(SiteConfiguration siteConfig, WebUser user) throws SiteHostingException {
		Key key = createKey(siteConfig);
		synchronized (sites) {
			HostedSite site = sites.get(key);
			if (site == null) {
				throw new WrongHostingStatusException("Site is already stopped");
			}
			
			releaseHost(site.getHost());
			sites.remove(key);
		}
	}
	
	@Override
	public IHostedSite get(SiteConfiguration siteConfig) {
		synchronized (sites) {
			return sites.get(createKey(siteConfig));
		}
	}
	
	/**
	 * @param host A host in the form <code>hostname:port</code>
	 * @return
	 */
	@Override
	public boolean isHosted(String host) {
		// FIXME: This gets called a lot, can we avoid the locking?
		synchronized (hostLock) {
			return hosts.contains(host);
		}
	}
	
	/**
	 * @param host A host in the form <code>hostname:port</code>
	 * @return
	 */
	HostedSite get(String host) {
		synchronized (sites) {
			for (HostedSite site : sites.values()) {
				if (site.getHost().equals(host))
					return site;
			}
		}
		return null;
	}
	
	private String acquireHost() throws SiteHostingException {
		synchronized (hostLock) {
			// FIXME: 127.0.0.x only works by default on Win/Linux 
			// - Allow configurable IPs
			// - If domain wildcards available, try those first
			int bit = allocated.nextClearBit(0);
			if (bit == -1) {
				throw new SiteHostingException("No more hosts available");
			}
			allocated.set(bit);
			String host = "127.0.0." + bit + ":" + this.hostingPort; //$NON-NLS-2$
			hosts.add(host);
			return host;
		}
	}
	
	private void releaseHost(String host) {
		String lastByteStr = host.substring(host.lastIndexOf(".") + 1, host.lastIndexOf(":")); //$NON-NLS-1$ //$NON-NLS-2$
		int lastByte = Integer.parseInt(lastByteStr);
		synchronized (hostLock) {
			allocated.clear(lastByte);
			hosts.remove(host);
		}
	}
	
	private Key createKey(SiteConfiguration siteConfig) {
		return new Key(siteConfig.getId());
	}
	
	private HostedSite createValue(SiteConfiguration siteConfig, WebUser user, String host, String devServer) {
		return new HostedSite(siteConfig, user, host, devServer);
	}
}

/**
 * Key for an entry in the table. For now this is based on site configuration id,
 * so for any site configuration, only 1 instance of it may be hosted at a time.
 * 
 * We don't store actual SiteConfiguration instances here because their getters rely 
 * on the backing store, which can change.
 */
class Key {
	// Globally unique id of the site configuration
	private String siteConfigurationId;
	
	public Key(String siteConfigurationId) {
		this.siteConfigurationId = siteConfigurationId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+ ((siteConfigurationId == null) ? 0 : siteConfigurationId
						.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Key other = (Key) obj;
		if (siteConfigurationId == null) {
			if (other.siteConfigurationId != null)
				return false;
		} else if (!siteConfigurationId.equals(other.siteConfigurationId))
			return false;
		return true;
	}
}
