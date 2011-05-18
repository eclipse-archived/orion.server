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
package org.eclipse.orion.internal.server.servlets.site;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.workspace.WebElement;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.json.JSONArray;
import org.json.JSONException;
import org.osgi.service.prefs.BackingStoreException;

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

	/**
	 * Removes this site configuration from the backing store. Should only be used when this 
	 * site configuration will never be used (for example, in response to errors during creation).
	 */
	void delete() throws CoreException {
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
}
