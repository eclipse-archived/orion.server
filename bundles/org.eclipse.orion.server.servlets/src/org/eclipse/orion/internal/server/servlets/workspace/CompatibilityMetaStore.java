/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.site.*;
import org.eclipse.orion.server.core.metastore.*;
import org.json.*;

/**
 * A meta store implementation that is backed by the legacy Orion 1.0 back end
 * storage (Equinox preferences)
 * @deprecated avoid warnings about interacting with deprecated code
 */
public class CompatibilityMetaStore implements IMetaStore {

	public void createUser(UserInfo info) throws CoreException {
		updateUser(info);
	}

	public void createWorkspace(WorkspaceInfo info) throws CoreException {
	}

	public void createProject(ProjectInfo info) throws CoreException {
	}

	public UserInfo readUser(String uid) throws CoreException {
		//convert a legacy WebUser object into a UserInfo
		WebUser webUser = WebUser.fromUserId(uid);
		UserInfo info = new UserInfo();
		info.setUID(webUser.getId());
		info.setFullName(webUser.getName());
		info.setUserName(webUser.getUserName());
		info.setGuest(webUser.isGuest());
		//workspaces
		JSONArray workspaces = webUser.getWorkspacesJSON();
		List<String> workspaceIds = new ArrayList<String>(workspaces.length());
		for (int i = 0; i < workspaces.length(); i++) {
			try {
				workspaceIds.add(workspaces.getJSONObject(i).getString(ProtocolConstants.KEY_ID));
			} catch (JSONException e) {
				//ignore malformed metadata
			}
		}
		info.setWorkspaceIds(workspaceIds);
		//site configurations
		JSONArray sites = webUser.getSiteConfigurationsJSON(null);
		JSONObject sitesObject = new JSONObject();
		for (int i = 0; i < sites.length(); i++) {
			try {
				JSONObject site = sites.getJSONObject(i);
				sitesObject.put(site.getString(ProtocolConstants.KEY_ID), site);
			} catch (JSONException e) {
				//skip malformed site
			}
		}
		info.setProperty(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS, sitesObject.toString());

		return info;
	}

	public void updateUser(UserInfo info) throws CoreException {
		String userId = info.getUID();
		if (userId == null)
			throw new IllegalArgumentException("User id not provided"); //$NON-NLS-1$
		WebUser webUser = WebUser.fromUserId(userId);
		webUser.setUserName(info.getUserName());
		webUser.setName(info.getFullName());
		webUser.setGuest(info.isGuest());

		updateSites(info, webUser);

		webUser.save();
	}

	/**
	 * Update persisted site configurations based on the current information in the provided
	 * {@link UserInfo}.
	 */
	private void updateSites(UserInfo info, WebUser webUser) throws CoreException {
		//site configurations
		String sitesString = info.getProperty(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS);
		if (sitesString != null) {
			JSONArray currentSites = webUser.getSiteConfigurationsJSON(null);
			try {
				JSONObject newSites = new JSONObject(sitesString);
				List<String> sitesToAdd = new ArrayList<String>();
				for (@SuppressWarnings("unchecked")
				Iterator<String> it = newSites.keys(); it.hasNext();) {
					sitesToAdd.add(it.next());
				}
				//iterate over existing site and check if it is changed or removed
				for (int i = 0; i < currentSites.length(); i++) {
					JSONObject currentSite = currentSites.getJSONObject(i);
					final String currentSiteId = currentSite.getString(ProtocolConstants.KEY_ID);
					JSONObject newSite = newSites.optJSONObject(currentSiteId);
					if (newSite == null) {
						//remove existing site because it is not found in new user info
						webUser.removeSiteConfiguration(webUser.getSiteConfiguration(currentSiteId));
					} else {
						//existing site that may need to be updated
						SiteConfiguration currentSiteConfig = webUser.getSiteConfiguration(currentSiteId);
						SiteInfo newSiteInfo = SiteInfo.getSite(info, currentSiteId);
						currentSiteConfig.update(newSiteInfo);
					}
					//if site already exists then it's not an addition
					sitesToAdd.remove(currentSiteId);
				}
				//add any new sites
				for (String newSiteId : sitesToAdd) {
					SiteInfo newSiteInfo = SiteInfo.getSite(info, newSiteId);
					SiteConfiguration newSiteConfig = webUser.createSiteConfiguration(newSiteId, newSiteInfo.getName(), newSiteInfo.getWorkspace());
					newSiteConfig.update(newSiteInfo);
				}

			} catch (JSONException e) {
				//malformed input
				throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Malformed site configuration data", e));
			}
		}
	}

	public void deleteUser(String uid) throws CoreException {
		WebUser.fromUserId(uid).delete();
	}

}
