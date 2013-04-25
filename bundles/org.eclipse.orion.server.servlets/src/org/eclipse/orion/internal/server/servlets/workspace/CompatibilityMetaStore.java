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
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.site.*;
import org.eclipse.orion.server.core.metastore.*;
import org.eclipse.orion.server.core.users.OrionScope;
import org.json.*;
import org.osgi.service.prefs.BackingStoreException;

/**
 * A meta store implementation that is backed by the legacy Orion 1.0 back end
 * storage (Equinox preferences)
 * @deprecated avoid warnings about interacting with deprecated code
 */
public class CompatibilityMetaStore implements IMetaStore {

	public void createProject(String workspaceId, ProjectInfo info) throws CoreException {
		WebWorkspace workspace = WebWorkspace.fromId(workspaceId);
		WebProject project = WebProject.fromId(WebProject.nextProjectId());
		info.setUniqueId(project.getId());
		updateProject(info);
		workspace.addProject(project);
		workspace.save();
	}

	public void createUser(UserInfo info) throws CoreException {
		//assign a unique user id if caller hasn't done so already
		if (info.getUniqueId() == null)
			info.setUniqueId(WebUser.nextUserId());
		//cause user to be created first
		WebUser.fromUserId(info.getUniqueId());
		//now update other user info
		updateUser(info);
	}

	public void createWorkspace(String userId, WorkspaceInfo info) throws CoreException {
		WebWorkspace workspace = WebUser.fromUserId(userId).createWorkspace(info.getFullName());
		info.setUniqueId(workspace.getId());
	}

	public void deleteProject(String workspaceId, String projectName) throws CoreException {
		WebWorkspace workspace = WebWorkspace.fromId(workspaceId);
		WebProject project = workspace.getProjectByName(projectName);
		//if no such project exists, we have nothing to do here
		if (project == null)
			return;
		//If all went well, remove project from workspace
		workspace.removeProject(project);

		//remove project metadata
		project.remove();

		//save the workspace and project metadata
		project.save();
		workspace.save();
	}

	public void deleteUser(String userId) throws CoreException {
		WebUser user = WebUser.fromUserId(userId);
		//first delete workspaces
		JSONArray workspaces = user.getWorkspacesJSON();
		for (int i = 0; i < workspaces.length(); i++) {
			try {
				String workspaceId = workspaces.getJSONObject(i).getString(ProtocolConstants.KEY_ID);
				deleteWorkspace(userId, workspaceId);
			} catch (JSONException e) {
				//ignore malformed metadata
			}
		}
		//only delete user if we succeeded  to delete everything else
		user.delete();
	}

	public void deleteWorkspace(String userId, String workspaceId) throws CoreException {
		if (!WebWorkspace.exists(workspaceId))
			return;
		WebWorkspace workspace = WebWorkspace.fromId(workspaceId);
		//first delete projects
		for (WebProject project : workspace.getProjects()) {
			deleteProject(workspaceId, project.getName());
		}
		//finally delete the workspace metadata
		workspace.removeNode();
	}

	public List<String> readAllUsers() throws CoreException {
		try {
			String[] children = new OrionScope().getNode(WebUser.USERS_NODE).childrenNames();
			return Arrays.asList(children);
		} catch (BackingStoreException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Unable to retrieve metadata", e));
		}
	}

	public ProjectInfo readProject(String projectId) throws CoreException {
		if (!WebProject.exists(projectId))
			return null;
		return toProjectInfo(WebProject.fromId(projectId));
	}

	public ProjectInfo readProject(String workspaceId, String projectName) throws CoreException {
		WebWorkspace workspace = WebWorkspace.fromId(workspaceId);
		return toProjectInfo(workspace.getProjectByName(projectName));
	}

	public UserInfo readUser(String uid) throws CoreException {
		//convert a legacy WebUser object into a UserInfo
		WebUser webUser = WebUser.fromUserId(uid);
		UserInfo info = new UserInfo();
		info.setUniqueId(webUser.getId());
		info.setFullName(webUser.getName());
		info.setUserName(webUser.getUserName());
		info.setGuest(webUser.isGuest());
		//authorization info
		IEclipsePreferences store = webUser.getStore();
		info.setProperty(ProtocolConstants.KEY_USER_RIGHTS_VERSION, store.get(ProtocolConstants.KEY_USER_RIGHTS_VERSION, null));
		info.setProperty(ProtocolConstants.KEY_USER_RIGHTS, store.get(ProtocolConstants.KEY_USER_RIGHTS, null));
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

	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException {
		if (!WebWorkspace.exists(workspaceId))
			return null;
		WebWorkspace workspace = WebWorkspace.fromId(workspaceId);
		WorkspaceInfo info = new WorkspaceInfo();
		info.setUniqueId(workspaceId);
		info.setFullName(workspace.getName());
		List<String> projectIds = new ArrayList<String>();
		for (WebProject project : workspace.getProjects())
			projectIds.add(project.getId());
		info.setProjectIds(projectIds);
		return info;
	}

	private ProjectInfo toProjectInfo(WebProject project) {
		if (project == null)
			return null;
		ProjectInfo info = new ProjectInfo();
		info.setUniqueId(project.getId());
		info.setFullName(project.getName());
		info.setContentLocation(project.getContentLocation());
		return info;
	}

	public void updateProject(ProjectInfo projectInfo) throws CoreException {
		WebProject project = WebProject.fromId(projectInfo.getUniqueId());
		project.setContentLocation(projectInfo.getContentLocation());
		project.setName(projectInfo.getFullName());
		project.save();
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

	public void updateUser(UserInfo info) throws CoreException {
		String userId = info.getUniqueId();
		if (userId == null)
			throw new IllegalArgumentException("User id not provided"); //$NON-NLS-1$
		if (!WebUser.exists(userId)) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Cannot update non-existent user: " + userId));
		}
		WebUser webUser = WebUser.fromUserId(userId);
		if (info.getUserName() != null)
			webUser.setUserName(info.getUserName());
		if (info.getFullName() != null)
			webUser.setName(info.getFullName());
		webUser.setGuest(info.isGuest());

		//authorization info
		IEclipsePreferences store = webUser.getStore();
		String prop = info.getProperty(ProtocolConstants.KEY_USER_RIGHTS_VERSION);
		if (prop != null)
			store.put(ProtocolConstants.KEY_USER_RIGHTS_VERSION, prop);
		prop = info.getProperty(ProtocolConstants.KEY_USER_RIGHTS);
		if (prop != null)
			store.put(ProtocolConstants.KEY_USER_RIGHTS, prop);

		updateSites(info, webUser);

		webUser.save();
	}
}
