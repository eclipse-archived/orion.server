/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
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
import org.eclipse.orion.internal.server.servlets.site.*;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.core.users.OrionScope;
import org.json.*;
import org.osgi.service.prefs.BackingStoreException;

/**
 * An Eclipse web user.
 */
public class WebUser extends WebElement {

	public WebUser(IEclipsePreferences store) {
		super(store);
	}

	/**
	 * Creates a web user instance for the given name.
	 */
	public static WebUser fromUserName(String userName) {
		IEclipsePreferences users = new OrionScope().getNode("Users"); //$NON-NLS-1$
		IEclipsePreferences result = (IEclipsePreferences) users.node(userName);
		if (result.get(ProtocolConstants.KEY_NAME, null) == null)
			result.put(ProtocolConstants.KEY_NAME, userName);
		if (result.get(ProtocolConstants.KEY_ID, null) == null)
			result.put(ProtocolConstants.KEY_ID, new UniversalUniqueIdentifier().toBase64String());
		try {
			result.flush();
		} catch (BackingStoreException e) {
			LogHelper.log(e);
		}
		return new WebUser(result);
	}

	public WebWorkspace createWorkspace(String name) throws CoreException {
		String id = WebWorkspace.nextWorkspaceId();
		WebWorkspace workspace = WebWorkspace.fromId(id);
		workspace.setName(name);
		workspace.save();
		//create an object to represent this new workspace associated with this user
		JSONObject newWorkspace = new JSONObject();
		try {
			newWorkspace.put(ProtocolConstants.KEY_ID, id);
			newWorkspace.put(ProtocolConstants.KEY_LAST_MODIFIED, System.currentTimeMillis());
		} catch (JSONException e) {
			//cannot happen as the keys and values are well-formed
		}

		//add the new workspace to the list of workspaces known to this user
		String workspaces = store.get(ProtocolConstants.KEY_WORKSPACES, null);
		JSONArray workspaceArray = null;
		if (workspaces != null) {
			try {
				workspaceArray = new JSONArray(workspaces);
			} catch (JSONException e) {
				//ignore and create a new one
			}
		}
		if (workspaceArray == null)
			workspaceArray = new JSONArray();
		workspaceArray.put(newWorkspace);
		store.put(ProtocolConstants.KEY_WORKSPACES, workspaceArray.toString());
		save();
		return workspace;
	}

	/**
	 * Returns the workspaces used by this user as a JSON array. 
	 */
	public JSONArray getWorkspacesJSON() {
		try {
			String workspaces = store.get(ProtocolConstants.KEY_WORKSPACES, null);
			//just return empty array if there are no workspaces
			if (workspaces != null)
				return new JSONArray(workspaces);
		} catch (JSONException e) {
			//someone has bashed the underlying storage - just fall through below
		}
		return new JSONArray();
	}

	/**
	 * Creates a SiteConfiguration for this user.
	 * @param name
	 * @param workspace
	 * @return The created SiteConfiguration.
	 */
	public SiteConfiguration createSiteConfiguration(String name, String workspace) throws CoreException {
		String id = SiteConfiguration.nextSiteConfigurationId();
		SiteConfiguration siteConfig = SiteConfiguration.fromId(id);
		siteConfig.setName(name);
		siteConfig.setWorkspace(workspace);
		siteConfig.save();

		// Create a JSON object to represent the user-to-siteconfig association
		JSONObject newSiteConfiguration = new JSONObject();
		try {
			newSiteConfiguration.put(ProtocolConstants.KEY_ID, id);
		} catch (JSONException e) {
			// Can't happen
		}

		// Add the new site configuration to this user's list of known site configurations
		IEclipsePreferences siteConfigNode = (IEclipsePreferences) getSiteConfigurationsNode().node(id);
		siteConfigNode.put(ProtocolConstants.KEY_ID, id);
		save();

		return siteConfig;
	}

	public void removeSiteConfiguration(SiteConfiguration siteConfig) throws CoreException {
		try {
			// Remove this user's record of the site configuration.
			getSiteConfigurationsNode().node(siteConfig.getId()).removeNode();
		} catch (BackingStoreException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Error removing site configuration", e));
		}
		save();
	}

	/**
	 * @return JSONArray of all site configurations known to this user.
	 */
	public JSONArray getSiteConfigurationsJSON(URI baseLocation) {
		try {
			IEclipsePreferences siteConfigsNode = (IEclipsePreferences) store.node(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS);
			String[] siteConfigIds = siteConfigsNode.childrenNames();
			JSONArray jsonArray = new JSONArray();
			for (String id : siteConfigIds) {
				// Get the actual site configuration this points to
				SiteConfiguration siteConfig = getExistingSiteConfiguration(id);
				JSONObject siteConfigJson = SiteConfigurationResourceHandler.toJSON(siteConfig, baseLocation);
				jsonArray.put(siteConfigJson);
			}
			return jsonArray;
		} catch (BackingStoreException e) {
			LogHelper.log(e);
		}
		return new JSONArray();
	}

	/**
	 * @return The site configuration with the given <code>id</code>, or null if this user has
	 * no record of such a site configuration.
	 */
	public SiteConfiguration getSiteConfiguration(String id) {
		try {
			IEclipsePreferences siteConfigsNode = this.getSiteConfigurationsNode();
			if (siteConfigsNode.nodeExists(id)) {
				// Get the actual site configuration
				if (SiteConfiguration.siteConfigExists(id)) {
					SiteConfiguration siteConfig = SiteConfiguration.fromId(id);
					return siteConfig;
				} else {
					// Shouldn't happen
					LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Site configuration does not exist in backing store"));
				}
			}
		} catch (BackingStoreException e) {
			LogHelper.log(e);
		}
		return null;
	}

	private static SiteConfiguration getExistingSiteConfiguration(String id) {
		if (SiteConfiguration.siteConfigExists(id)) {
			SiteConfiguration siteConfig = SiteConfiguration.fromId(id);
			return siteConfig;
		} else {
			// Shouldn't happen. Maybe someone deleted the site configuration underlying storage
			return null;
		}
	}

	/**
	 * @return This user's site configurations preference node
	 */
	private IEclipsePreferences getSiteConfigurationsNode() {
		return (IEclipsePreferences) this.store.node(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS);
	}

	/**
	 * Removes the user from the backing store.
	 */
	public void delete() throws CoreException {
		try {
			IEclipsePreferences parent = (IEclipsePreferences) store.parent();
			store.clear();
			store.removeNode();
			// TODO: consider removing user's Workspaces, Projects, Clones, SiteConfigs if no one else is using them
			parent.flush();
		} catch (BackingStoreException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Error removing user", e));
		}
	}
}
