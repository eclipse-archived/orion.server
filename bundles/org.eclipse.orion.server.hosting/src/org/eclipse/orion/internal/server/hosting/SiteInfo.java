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
package org.eclipse.orion.internal.server.hosting;

import org.eclipse.orion.server.core.ProtocolConstants;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.json.*;

/**
 * Configuration details for a site that can be hosted on an Orion server.
 */
public class SiteInfo {

	private String hostHint;
	private String id;
	private JSONArray mappings = new JSONArray();
	private String name;
	private String workspaceId;

	/**
	 * Returns the object containing the current site configurations for this user.
	 */
	public static JSONObject getSites(UserInfo user) {
		//return value is a JSONObject where key is site id, value is site object
		String sites = user.getProperty(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS);
		if (sites != null) {
			try {
				return new JSONObject(sites);
			} catch (JSONException e) {
				//let it fail on write
			}
		}
		//assume there are no sites and create a new one
		return new JSONObject();
	}

	public static SiteInfo getSite(UserInfo user, String siteId) {
		JSONObject sites = getSites(user);
		JSONObject siteObject;
		try {
			siteObject = sites.getJSONObject(siteId);
		} catch (JSONException e) {
			//does not exist
			return null;
		}
		return new SiteInfo(siteObject);
	}

	/**
	 * Returns a new site configuration for the given user.
	 */
	public static SiteInfo newSiteConfiguration(UserInfo user, String name, String workspaceId) {
		SiteInfo site = new SiteInfo();
		site.setName(name);
		site.setWorkspace(workspaceId);
		site.setId(nextSiteId(user));
		return site;
	}

	/**
	 * Returns a new site id that is unique for the given user.
	 */
	private static String nextSiteId(UserInfo user) {
		Base64Counter counter = new Base64Counter();
		String userName = user.getUserName();
		JSONObject sitesObject = getSites(user);
		String candidate = userName + '-' + counter.toString();
		while (sitesObject.has(candidate)) {
			counter.increment();
			candidate = userName + '-' + counter.toString();
		}
		return candidate;
	}

	private SiteInfo() {
		super();
	}

	/**
	 * Creates a new SiteInfo instance with information from the given input object.
	 */
	public SiteInfo(JSONObject siteObject) {
		super();
		this.id = siteObject.optString(ProtocolConstants.KEY_ID);
		this.name = siteObject.optString(ProtocolConstants.KEY_NAME);
		this.hostHint = siteObject.optString(SiteConfigurationConstants.KEY_HOST_HINT);
		this.workspaceId = siteObject.optString(SiteConfigurationConstants.KEY_WORKSPACE);
		this.mappings = siteObject.optJSONArray(SiteConfigurationConstants.KEY_MAPPINGS);
	}

	public String getHostHint() {
		return hostHint;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	public JSONArray getMappingsJSON() {
		return mappings;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	public String getWorkspace() {
		return workspaceId;
	}

	/**
	 * Saves this site configuration in the given user info.
	 * @param user
	 * @throws CoreException 
	 */
	public void save(UserInfo user) throws CoreException {
		JSONObject sites = getSites(user);
		try {
			sites.put(getId(), toJSON());
		} catch (JSONException e) {
			//should never happen if metadata is well formed
			throw new RuntimeException(e);
		}
		user.setProperty(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS, sites.toString());
		OrionConfiguration.getMetaStore().updateUser(user);
	}

	/**
	 * Removes this site from the given user.
	 * @throws CoreException 
	 */
	public void delete(UserInfo user) throws CoreException {
		JSONObject sites = getSites(user);
		if (!sites.has(getId())) {
			//nothing to do, site does not exist
			return;
		}
		sites.remove(getId());
		user.setProperty(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS, sites.toString());
		OrionConfiguration.getMetaStore().updateUser(user);
	}

	public void setHostHint(String hint) {
		this.hostHint = hint;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	public void setMappings(JSONArray newMappings) {
		this.mappings = newMappings == null ? new JSONArray() : newMappings;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	public void setWorkspace(String workspace) {
		this.workspaceId = workspace;
	}

	/**
	 * Returns a JSON representation of this site configuration.
	 */
	public JSONObject toJSON() {
		JSONObject result = new JSONObject();
		try {
			result.put(ProtocolConstants.KEY_ID, id);
			result.put(ProtocolConstants.KEY_NAME, name);
			result.put(SiteConfigurationConstants.KEY_HOST_HINT, hostHint);
			result.put(SiteConfigurationConstants.KEY_WORKSPACE, workspaceId);
			result.put(SiteConfigurationConstants.KEY_MAPPINGS, mappings);
		} catch (JSONException e) {
			//cannot happen because keys are well formed
			throw new RuntimeException(e);
		}

		return result;
	}
}
