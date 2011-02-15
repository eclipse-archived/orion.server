package org.eclipse.orion.internal.server.hosting;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.hosting.SiteHostException;
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
	
	private BitSet ipsAllocated;

	public SiteHostingService() {
		this.table = new HashMap<Key, HostedSite>();
		this.ipsAllocated = new BitSet();
	}
	
	@Override
	public void start(SiteConfiguration siteConfig, WebUser user) {
		synchronized (this) {
			// Acquire the next host
			Host host = acquireHost();
			
			Key key = createKey(siteConfig);
			if (table.containsKey(key)) {
				throw new SiteHostException("Site is already running; can't start");
			}
			
			// FIXME
			String workspaceId = "";
			table.put(key, createValue(siteConfig, user, workspaceId, host));
		}
	}
	
	public void stop(SiteConfiguration siteConfig, WebUser user) {
		synchronized (this) {
			// Release the host
			// Unregister the mappings, etc
			table.remove(createKey(siteConfig));
		}
	}

	@Override
	public boolean isRunning(SiteConfiguration siteConfig) {
		return table.containsKey(createKey(siteConfig));
	}
	
	private Host acquireHost() throws SiteHostException {
		return null;
	}
	
	private void releaseHost(Host host) {
		
	}
	
	private Key createKey(SiteConfiguration siteConfig) {
		return new Key(siteConfig.getId());
	}
	
	private HostedSite createValue(SiteConfiguration siteConfig, WebUser user, String workspaceId, Host host) {
		return new HostedSite(siteConfig, user, workspaceId, host);
	}
}

class Host {
	public Host() {
	}
}

/**
 * Key for an entry in the table. For now this is based on site configuration id,
 * so only 1 instance of a given site configuration may be running at a given time.
 * 
 * We don't store actual SiteConfiguration instances here because their getters aren't
 * aren't idempotent (they rely on the backing store, which may change).
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

	// Workspace id that this launch will use (userName must have appropriate access rights)
	String workspaceId;
	
	// The host where the site is accessible
	Host host;
	
	public HostedSite(SiteConfiguration siteConfig, WebUser user, String workspaceId, Host host) {
		this.mappings = createMap(siteConfig);
		this.userName = user.getName();
		this.workspaceId = workspaceId;
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
