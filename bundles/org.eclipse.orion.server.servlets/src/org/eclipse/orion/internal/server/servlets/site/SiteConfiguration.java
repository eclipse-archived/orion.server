package org.eclipse.orion.internal.server.servlets.site;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.workspace.WebElement;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Configuration details for a site that can be hosted on an Orion server.
 */
public class SiteConfiguration extends WebElement {

	public static final String SITE_CONFIGURATIONS_NODE_NAME = "SiteConfigurations"; //$NON-NLS-1$

	private static final Base64Counter siteConfigCounter = new Base64Counter();

	/**
	 * Creates a new SiteConfiguration instance with the given backing store.
	 * @param store
	 */
	public SiteConfiguration(IEclipsePreferences store) {
		super(store);
	}

	/**
	 * @param id The globally unique site configuration id.
	 * @return A site configuration with the given id. The site configuration may or may not yet exist in the backing store.
	 */
	public static SiteConfiguration fromId(String id) {
		SiteConfiguration siteConfiguration = new SiteConfiguration((IEclipsePreferences) scope.getNode(SITE_CONFIGURATIONS_NODE_NAME).node(id));
		siteConfiguration.setId(id);
		return siteConfiguration;
	}

	/**
	 * @return The next available site configuration id. The id is guaranteed to be globally unique on this server.
	 */
	public static String nextSiteConfigurationId() {
		synchronized (siteConfigCounter) {
			String candidate;
			do {
				candidate = siteConfigCounter.toString();
				siteConfigCounter.increment();
			} while (siteConfigExists(candidate));
			return candidate;
		}
	}

	/**
	 * @return True if a site configuration exists with the given <code>id</code>.
	 */
	public static boolean siteConfigExists(String id) {
		try {
			return scope.getNode(SITE_CONFIGURATIONS_NODE_NAME).nodeExists(id);
		} catch (Exception e) {
			return false;
		}
	}

	public String getAuthPassword() {
		return store.get(SiteConfigurationConstants.KEY_AUTH_PASSWORD, null);
	}

	public String getHostHint() {
		return store.get(SiteConfigurationConstants.KEY_HOST_HINT, null);
	}

	public JSONArray getMappingsJSON() {
		try {
			return new JSONArray(store.get(SiteConfigurationConstants.KEY_MAPPINGS, "[]")); //$NON-NLS-1$
		} catch (JSONException e) {
			return new JSONArray();
		}
	}

	public void setAuthPassword(String authPassword) {
		store.put(SiteConfigurationConstants.KEY_AUTH_PASSWORD, authPassword);
	}

	public void setHostHint(String hostHint) {
		store.put(SiteConfigurationConstants.KEY_HOST_HINT, hostHint);
	}

	public void setMappings(JSONArray mappings) {
		store.put(SiteConfigurationConstants.KEY_MAPPINGS, mappings.toString());
	}
}
