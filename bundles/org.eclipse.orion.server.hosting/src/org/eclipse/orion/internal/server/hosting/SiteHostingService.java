package org.eclipse.orion.internal.server.hosting;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.hosting.SiteHostingException;
import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;

/**
 * Maintains a table of hosted sites. This table is kept in memory only and not persisted.
 */
public class SiteHostingService implements ISiteHostingService {
	
	private Map<Key, HostedSite> table;
	
	private Set<String> hosts;	// All hosts we've used
	private BitSet allocated;	// Bit #i is set if the ip address 192.168.0.i has been allocated
	
	private Object hostLock = new Object();
	
	public SiteHostingService() {
		this.table = new HashMap<Key, HostedSite>();
		this.hosts = new HashSet<String>();
		this.allocated = new BitSet(256);
		allocated.set(0);
		allocated.set(1);
		allocated.set(255);
	}
	
	@Override
	public void start(SiteConfiguration siteConfig, WebUser user) {
		Key key = createKey(siteConfig);
		synchronized (table) {
			if (table.containsKey(key)) {
				throw new SiteHostingException("Site is already started; can't start");
			}
			
			String host = acquireHost();
			table.put(key, createValue(siteConfig, user, host));
		}
	}
	
	public void stop(SiteConfiguration siteConfig, WebUser user) {
		Key key = createKey(siteConfig);
		synchronized (table) {
			HostedSite site = table.get(key);
			if (site == null) {
				throw new SiteHostingException("Site is not started; can't stop");
			}
			
			releaseHost(site.getHost());
			table.remove(key);
		}
	}
	
	@Override
	public boolean isRunning(SiteConfiguration siteConfig) {
		return get(siteConfig) != null;
	}

	public HostedSite get(SiteConfiguration siteConfig) {
		synchronized (table) {
			return table.get(createKey(siteConfig));
		}
	}
	
	@Override
	public boolean isHosted(String host) {
		// FIXME: this gets called a lot, should really avoid blocking here :(
		// perhaps use ConcurrentHashMap and key == host
		synchronized (hostLock) {
			return hosts.contains(host);
		}
	}
	
	/**
	 * @param host
	 * @return
	 */
	public HostedSite get(String host) {
		synchronized (table) {
			for (HostedSite site : table.values()) {
				if (site.getHost().equals(host))
					return site;
			}
		}
		return null;
	}
	
	private String acquireHost() throws SiteHostingException {
		synchronized (hostLock) {
			// FIXME: allow configurable IPs
			// FIXME: if domain wildcards available, try those firstx
			int bit = allocated.nextClearBit(0);
			if (bit == -1) {
				throw new SiteHostingException("No more hosts available");
			}
			allocated.set(bit);
			String host = "127.0.0." + bit;
			hosts.add(host);
			return host;
		}
	}
	
	private void releaseHost(String host) {
		String lastByteStr = host.substring(host.lastIndexOf(".") + 1); //$NON-NLS-1$
		int lastByte = Integer.parseInt(lastByteStr);
		synchronized (hostLock) {
			allocated.clear(lastByte);
			hosts.remove(host);
		}
	}
	
	private Key createKey(SiteConfiguration siteConfig) {
		return new Key(siteConfig.getId());
	}
	
	private HostedSite createValue(SiteConfiguration siteConfig, WebUser user, String host) {
		return new HostedSite(siteConfig, user, host);
	}
}

/**
 * Key for an entry in the table. For now this is based on site configuration id,
 * so for any site configuration, only 1 instance of it may be hosted at a time.
 * 
 * Don't store actual SiteConfiguration instances here because their getters rely 
 * on the backing store, which may change.
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
