/*******************************************************************************
 * Copyright (c) 2010, 2013 IBM Corporation and others.
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
import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.internal.server.servlets.site.SiteConfigurationConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.eclipse.orion.server.core.users.OrionScope;
import org.json.*;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Represents a single Orion user.
 * @deprecated replaced by {@link IMetaStore} and {@link UserInfo}.
 */
public class WebUser extends WebElement {
	private static final Base64Counter userCounter = new Base64Counter();

	static final String USERS_NODE = "Users"; //$NON-NLS-1$

	public WebUser(IEclipsePreferences store) {
		super(store);
	}

	//	public static Collection<String> getGuestAccountsToDelete(int limit) {
	//		List<String> uids = new ArrayList<String>();
	//		try {
	//			int excess = guestUserCount() - limit;
	//			Base64Counter count = new Base64Counter();
	//			while (excess-- > 0) {
	//				// Deletes oldest ones first
	//				String uid;
	//				do {
	//					uid = GUEST_UID_PREFIX + count.toString();
	//					count.increment();
	//				} while (!exists(uid));
	//				uids.add(uid);
	//			}
	//		} catch (BackingStoreException e) {
	//			LogHelper.log(e);
	//		}
	//		return uids;
	//	}
	//
	//	private static int guestUserCount() throws BackingStoreException {
	//		// FIXME probably slow
	//		int count = 0;
	//		IEclipsePreferences usersNode = new OrionScope().getNode(USERS_NODE);
	//		for (String uid : usersNode.childrenNames()) {
	//			if (uid.startsWith(GUEST_UID_PREFIX)) {
	//				count++;
	//			}
	//		}
	//		return count;
	//	}

	private static IEclipsePreferences getUserNode(String uid) {
		try {
			return (IEclipsePreferences) new OrionScope().getNode(USERS_NODE).node(uid);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Returns whether a user with the given id already exists.
	 */
	static boolean exists(String userId) {
		if (userId == null)
			return false;
		try {
			return scope.getNode(USERS_NODE).nodeExists(userId);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Returns the next available user id.
	 */
	static synchronized String nextUserId() {
		while (exists(userCounter.toString())) {
			userCounter.increment();
		}
		return userCounter.toString();
	}

	/**
	 * Creates a web user instance for the given name.
	 */
	public static WebUser fromUserId(String userId) {
		IEclipsePreferences result = getUserNode(userId);
		if (result.get(ProtocolConstants.KEY_NAME, null) == null)
			result.put(ProtocolConstants.KEY_NAME, "Unnamed User");
		//ignore any existing value for userId because it used to be a randomly generated UUID
		result.put(ProtocolConstants.KEY_ID, userId);
		if (result.get(ProtocolConstants.KEY_USER_NAME, null) == null)
			result.put(ProtocolConstants.KEY_USER_NAME, userId);
		WebUser user = new WebUser(result);
		try {
			user.save();
		} catch (CoreException e) {
			LogHelper.log(e);
		}
		return user;
	}

	public WebWorkspace createWorkspace(String name) throws CoreException {
		//default to first workspace having username
		String id = WebWorkspace.nextWorkspaceId(getUserName());
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
	 * Return the human readable username that appears in file URLs.
	 * @return the user name
	 */
	public String getUserName() {
		return store.get(ProtocolConstants.KEY_USER_NAME, ""); //$NON-NLS-1$
	}

	/**
	 * Sets the human readable username that appears in file URLs.
	 * @param name The new user name
	 */
	public void setUserName(String name) {
		store.put(ProtocolConstants.KEY_USER_NAME, name);
	}

	public boolean isGuest() {
		return this.store.getBoolean(ProtocolConstants.KEY_GUEST, false);
	}

	public void setGuest(boolean isGuest) {
		//default is false so we don't need to store that
		if (isGuest)
			this.store.putBoolean(ProtocolConstants.KEY_GUEST, isGuest);
		else
			this.store.remove(ProtocolConstants.KEY_GUEST);
	}

	/**
	 * Creates a SiteConfiguration for this user.
	 * @param name
	 * @param workspace
	 * @return The created SiteConfiguration.
	 */
	public SiteConfiguration createSiteConfiguration(String id, String name, String workspace) throws CoreException {
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
			// Remove this user's pointer to the site configuration.
			getSiteConfigurationsNode().node(siteConfig.getId()).removeNode();
			// Delete the site configuration from the backing store
			siteConfig.delete();
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
				if (siteConfig != null) {
					JSONObject siteConfigJson = SiteConfiguration.toJSON(siteConfig, baseLocation);
					jsonArray.put(siteConfigJson);
				}
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
				}
				// Shouldn't happen
				LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Site configuration does not exist in backing store"));
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
		}
		// Shouldn't happen. Maybe someone deleted the site configuration underlying storage
		return null;
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

	public void deleteWorkspace(String workspaceId) throws CoreException {
		String workspaces = store.get(ProtocolConstants.KEY_WORKSPACES, null);
		JSONArray workspaceArray = null;
		if (workspaces != null) {
			try {
				workspaceArray = new JSONArray(workspaces);
			} catch (JSONException e) {
				//ignore and create a new one
			}
		}
		if (workspaceArray == null) {
			workspaceArray = new JSONArray();
		}
		for (int i = 0; i < workspaceArray.length(); i++) {
			String workspace = null;
			try {
				String jsonString = workspaceArray.getString(i);
				JSONObject jsonObject = new JSONObject(jsonString);
				workspace = jsonObject.getString("Id");
			} catch (JSONException e) {
				// should not occur, we are reading in valid JSON
			}
			if (workspaceId.equals(workspace)) {
				workspaceArray.remove(i);
			}
		}
		store.put(ProtocolConstants.KEY_WORKSPACES, workspaceArray.toString());
		save();
	}
}
