package org.eclipse.orion.internal.server.hosting;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.hosting.SiteHostingException;
import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.internal.server.servlets.site.SiteConfigurationConstants;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Maintains a table of hosted sites. This table is kept in memory only and not persisted.
 */
public class SiteHostingService implements ISiteHostingService {
	
	private Map<Key, HostedSite> table;
	
	private Set<String> hosts;	// All hosts we've used
	private BitSet allocated;	// Bit #i is set if the ip address 192.168.0.i has been allocated
	
	// Exclusive access to hosts, allocated
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
			
			// Register mappings
			
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
			
			table.remove(key);
			
			releaseHost(site.host);
			
			// Unregister the mappings
		}
	}

	@Override
	public boolean isRunning(SiteConfiguration siteConfig) {
		synchronized (table) {
			return table.containsKey(createKey(siteConfig));
		}
	}
	
	@Override
	public boolean isHosted(String host) {
		// TODO: this is the most common case, we should really avoid blocking
		synchronized (hostLock) {
			return hosts.contains(host);
		}
	}
	
	private String acquireHost() throws SiteHostingException {
		synchronized (hostLock) {
			// FIXME: allow configurable IPs
			// FIXME: if domain wildcards available, try those first
			int bit = allocated.nextClearBit(0);
			if (bit == -1) {
				throw new SiteHostingException("No more hosts available");
			}
			allocated.set(bit);
			String host = "192.168.0." + bit;
			hosts.add(host);
			return host;
		}
	}
	
	private void releaseHost(String host) {
		String lastByteStr = host.substring(host.lastIndexOf(".")); //$NON-NLS-1$
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
 * so only 1 instance of a given site configuration may be running at a given time.
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

class HostedSite {
	// Mappings defined by the site configuration
	Map<String, String> mappings;
	
	// The user who launched this site
	String userName;

	// Workspace id that this launch will use (userName must have appropriate access rights to it)
	String workspaceId;
	
	// The host where the site is accessible
	String host;
	
	public HostedSite(SiteConfiguration siteConfig, WebUser user, String host) {
		this.mappings = createMap(siteConfig);
		this.userName = user.getName();
		this.workspaceId = siteConfig.getWorkspace();
		this.host = host;
	}

	private static Map<String,String> createMap(SiteConfiguration siteConfig) {
		Map<String,String> map = new HashMap<String,String>();
		JSONArray mappingsJson = siteConfig.getMappingsJSON();
		for (int i=0; i < mappingsJson.length(); i++) {
			try {
				JSONObject mapping = (JSONObject) mappingsJson.get(i);
				String source = mapping.optString(SiteConfigurationConstants.KEY_SOURCE, null);
				String target = mapping.optString(SiteConfigurationConstants.KEY_TARGET, null);
				if (source != null && target != null) {
					map.put(source, target);
				}
			} catch (JSONException e) {
				// Shouldn't happen
			}
		}
		return map; 
	}
}
