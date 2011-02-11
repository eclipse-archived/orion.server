package org.eclipse.orion.internal.server.servlets.site;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.workspace.WebElement;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Configuration for a site that can be hosted on an Orion server.
 */
public class SiteConfiguration extends WebElement {

	/**
	 * Creates a new SiteConfiguration instance with the given backing store.
	 * @param store
	 */
	public SiteConfiguration(IEclipsePreferences store) {
		super(store);
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

	public void setHostHint(String hostDomain) {
		store.put(SiteConfigurationConstants.KEY_HOST_HINT, hostDomain);
	}

	public void setMappings(JSONArray mappings) {
		store.put(SiteConfigurationConstants.KEY_MAPPINGS, mappings.toString());
	}
}
