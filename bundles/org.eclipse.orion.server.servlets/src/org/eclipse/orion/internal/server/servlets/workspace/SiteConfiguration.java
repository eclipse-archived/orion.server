/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import java.net.URI;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.site.SiteConfigurationConstants;
import org.eclipse.orion.internal.server.servlets.site.SiteInfo;
import org.eclipse.orion.server.core.ServerConstants;
import org.json.*;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Configuration details for a site that can be hosted on an Orion server.
 * @deprecated replaced by {@link SiteInfo}. Exists only for compatibility and migration of old workspaces.
 */
class SiteConfiguration extends WebElement {

	public static final String SITE_CONFIGURATIONS_NODE_NAME = "SiteConfigurations"; //$NON-NLS-1$

	/**
	 * @param baseLocation The URI of the SiteConfigurationServlet.
	 * @return Representation of <code>site</code> as a JSONObject.
	 */
	public static JSONObject toJSON(SiteConfiguration site, URI baseLocation) {
		JSONObject result = new JSONObject();
		try {
			result.put(ProtocolConstants.KEY_ID, site.getId());
			result.put(ProtocolConstants.KEY_NAME, site.getName());
		} catch (JSONException e1) {
			//cannot happen, we know keys and values are valid
		}
		try {
			if (baseLocation != null)
				result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(baseLocation, site.getId()));
			result.putOpt(SiteConfigurationConstants.KEY_HOST_HINT, site.getHostHint());
			result.putOpt(SiteConfigurationConstants.KEY_WORKSPACE, site.getWorkspace());
			result.put(SiteConfigurationConstants.KEY_MAPPINGS, site.getMappingsJSON());

			// Note: The SiteConfigurationConstants.KEY_HOSTING_STATUS field will be contributed to the result
			// by the site-hosting bundle (if present) via an IWebResourceDecorator
		} catch (JSONException e) {
			// Can't happen
		}
		return result;
	}

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
	 * @return True if a site configuration exists with the given <code>id</code>.
	 */
	public static boolean siteConfigExists(String id) {
		try {
			return scope.getNode(SITE_CONFIGURATIONS_NODE_NAME).nodeExists(id);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Removes this site configuration from the backing store.
	 */
	public void delete() throws CoreException {
		try {
			IEclipsePreferences parent = (IEclipsePreferences) store.parent();
			store.removeNode();
			parent.flush();
		} catch (BackingStoreException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Error saving state"));
		}
	}

	public String getHostHint() {
		return store.get(SiteConfigurationConstants.KEY_HOST_HINT, null);
	}

	public String getWorkspace() {
		return store.get(SiteConfigurationConstants.KEY_WORKSPACE, null);
	}

	public JSONArray getMappingsJSON() {
		try {
			return new JSONArray(store.get(SiteConfigurationConstants.KEY_MAPPINGS, "[]")); //$NON-NLS-1$
		} catch (JSONException e) {
			return new JSONArray();
		}
	}

	public void setHostHint(String hostHint) {
		store.put(SiteConfigurationConstants.KEY_HOST_HINT, hostHint);
	}

	public void setWorkspace(String workspace) {
		store.put(SiteConfigurationConstants.KEY_WORKSPACE, workspace);
	}

	public void setMappings(JSONArray mappings) {
		store.put(SiteConfigurationConstants.KEY_MAPPINGS, mappings.toString());
	}

	/**
	 * Update this configuration with all the information from the provided site info.
	 * @throws CoreException 
	 */
	public void update(SiteInfo siteInfo) throws CoreException {
		setId(siteInfo.getId());
		setName(siteInfo.getName());
		setHostHint(siteInfo.getHostHint());
		setMappings(siteInfo.getMappingsJSON());
		setWorkspace(siteInfo.getWorkspace());
		save();
	}
}
