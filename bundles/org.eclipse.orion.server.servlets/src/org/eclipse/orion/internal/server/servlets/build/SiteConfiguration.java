package org.eclipse.orion.internal.server.servlets.build;

import org.eclipse.orion.internal.server.servlets.workspace.WebElement;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.json.JSONArray;
import org.json.JSONException;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Configuration for a site that can be hosted on an Orion server.
 */
public class SiteConfiguration extends WebElement {

	private static final Base64Counter siteConfigCounter = new Base64Counter();

	/**
	 * Creates a new SiteConfiguration instance with the given backing store.
	 * @param store
	 */
	public SiteConfiguration(IEclipsePreferences store) {
		super(store);
	}

	/**
	 * @return The <code>user</code>'s existing SiteConfiguration with the given <code>id</code>, or  
	 * null if no SiteConfiguration with that id exists for <code>user</code>.
	 */
	public static SiteConfiguration fromId(WebUser user, String id) {
		try {
			IEclipsePreferences siteConfigsNode = getSiteConfigurationsNode(user);
			if (siteConfigsNode.nodeExists(id)) {
				return new SiteConfiguration((IEclipsePreferences) siteConfigsNode.node(id));
			}
		} catch (BackingStoreException e) {
			LogHelper.log(e);
		}
		return null;
	}

	/**
	 * Creates a SiteConfiguration instance with the given name for the given user.
	 * @param user
	 * @param name
	 * @return The created SiteConfiguration.
	 */
	public static SiteConfiguration createSiteConfiguration(WebUser user, String name) throws CoreException {
		String id = nextSiteConfigurationId(user);
		IEclipsePreferences result = (IEclipsePreferences) getSiteConfigurationsNode(user).node(id);
		SiteConfiguration siteConfig = new SiteConfiguration(result);
		siteConfig.setId(id);
		siteConfig.setName(name);
		siteConfig.save();
		return siteConfig;
	}

	/**
	 * @return The next available site configuration id. The id is only unique within the user.
	 */
	public static String nextSiteConfigurationId(WebUser user) {
		synchronized (siteConfigCounter) {
			String candidate;
			do {
				candidate = siteConfigCounter.toString();
				siteConfigCounter.increment();
			} while (exists(user, candidate));
			return candidate;
		}
	}

	/**
	 * @param user
	 * @return The SiteConfigurations preference node from <code>user</code>'s preferences
	 */
	private static IEclipsePreferences getSiteConfigurationsNode(WebUser user) {
		return (IEclipsePreferences) scope.getNode("Users").node(user.getName()).node(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS); //$NON-NLS-1$
	}

	public static boolean exists(WebUser user, String id) {
		try {
			return getSiteConfigurationsNode(user).nodeExists(id);
		} catch (Exception e) {
			return false;
		}
	}

	public String getAuthName() {
		return store.get(SiteConfigurationConstants.KEY_AUTH_NAME, null);
	}

	public String getAuthPassword() {
		return store.get(SiteConfigurationConstants.KEY_AUTH_PASSWORD, null);
	}

	public String getHostDomain() {
		return store.get(SiteConfigurationConstants.KEY_HOST_DOMAIN, null);
	}

	public JSONArray getMappingsJSON() {
		try {
			return new JSONArray(store.get(SiteConfigurationConstants.KEY_MAPPINGS, "[]")); //$NON-NLS-1$
		} catch (JSONException e) {
			return new JSONArray();
		}
	}

	public void setAuthName(String authName) {
		store.put(SiteConfigurationConstants.KEY_AUTH_NAME, authName);
	}

	public void setAuthPassword(String authPassword) {
		store.put(SiteConfigurationConstants.KEY_AUTH_PASSWORD, authPassword);
	}

	public void setHostDomain(String hostDomain) {
		store.put(SiteConfigurationConstants.KEY_HOST_DOMAIN, hostDomain);
	}

	public void setMappings(JSONArray mappings) {
		store.put(SiteConfigurationConstants.KEY_MAPPINGS, mappings.toString());
	}
}
