/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.internal.webide.server.servlets.project;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.e4.internal.webide.server.servlets.ProtocolConstants;
import org.eclipse.e4.webide.server.resources.UniversalUniqueIdentifier;
import org.eclipse.e4.webide.server.users.EclipseWebScope;
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
		IEclipsePreferences users = new EclipseWebScope().getNode("Users"); //$NON-NLS-1$
		IEclipsePreferences result = (IEclipsePreferences) users.node(userName);
		if (result.get(ProtocolConstants.KEY_NAME, null) == null)
			result.put(ProtocolConstants.KEY_NAME, userName);
		if (result.get(ProtocolConstants.KEY_ID, null) == null)
			result.put(ProtocolConstants.KEY_ID, new UniversalUniqueIdentifier().toBase64String());
		try {
			result.flush();
		} catch (BackingStoreException e) {
			//TODO just log it
		}
		return new WebUser(result);
	}

	/**
	 * Adds a project to the list of projects that belong to this user.
	 */
	public void addProject(WebProject project) {
		String existingProjects = store.get(ProtocolConstants.KEY_PROJECTS, "[]");
		JSONArray allProjects;
		try {
			allProjects = new JSONArray(existingProjects);
		} catch (JSONException e) {
			//someone messed with the backing store and inserted something invalid- just wipe it out
			allProjects = new JSONArray();
		}
		//make sure we don't already have it
		String newProjectId = project.getId();
		for (int i = 0; i < allProjects.length(); i++) {
			try {
				if (newProjectId.equals(allProjects.get(i)))
					return;
			} catch (JSONException e) {
				//ignore empty slots
			}
		}
		//finally add the project to the user
		JSONObject storedProject = new JSONObject();
		try {
			storedProject.put(ProtocolConstants.KEY_ID, newProjectId);
		} catch (JSONException e) {
			//cannot happen because the key and value are well formed
			throw new RuntimeException(e);
		}
		allProjects.put(storedProject);
		store.put(ProtocolConstants.KEY_PROJECTS, allProjects.toString());
	}

	public WebProject createProject(String id) throws CoreException {
		WebProject project = WebProject.fromId(id);
		project.save();
		//create an object to represent this new project associated with this user
		JSONObject newProject = new JSONObject();
		try {
			newProject.put(ProtocolConstants.KEY_ID, id);
			newProject.put(ProtocolConstants.KEY_LAST_MODIFIED, System.currentTimeMillis());
		} catch (JSONException e) {
			//cannot happen as the keys and values are well-formed
		}

		//add the new project to the list of projects known to this user
		String projects = store.get(ProtocolConstants.KEY_PROJECTS, null);
		JSONArray projectArray = null;
		if (projects != null) {
			try {
				projectArray = new JSONArray(projects);
			} catch (JSONException e) {
				//ignore and create a new one
			}
		}
		if (projectArray == null)
			projectArray = new JSONArray();
		projectArray.put(newProject);
		store.put(ProtocolConstants.KEY_PROJECTS, projectArray.toString());
		save();
		return project;
	}

	/**
	 * Returns the projects used by this user as a JSON array. 
	 */
	public JSONArray getProjectsJSON() {
		try {
			String projects = store.get(ProtocolConstants.KEY_PROJECTS, null);
			//just return empty array if there are no workspaces
			if (projects != null)
				return new JSONArray(projects);
		} catch (JSONException e) {
			//someone has bashed the underlying storage - just fall through below
		}
		return new JSONArray();
	}
}
