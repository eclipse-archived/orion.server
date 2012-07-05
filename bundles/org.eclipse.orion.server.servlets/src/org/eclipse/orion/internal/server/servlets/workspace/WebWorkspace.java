/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.json.*;

/**
 * An Eclipse web workspace.
 */
public class WebWorkspace extends WebElement {
	private static final String WORKSPACE_NODE_NAME = "Workspaces";//$NON-NLS-1$
	private static final Base64Counter workspaceCounter = new Base64Counter();

	/**
	 * Creates a workspace instance with the given globally unique id. The workspace
	 * may or may not actually exist yet in the backing storage.
	 * @param id the globally unique workspace id
	 * @return A workspace instance with the given id
	 */
	public static WebWorkspace fromId(String id) {
		WebWorkspace result = new WebWorkspace((IEclipsePreferences) scope.getNode(WORKSPACE_NODE_NAME).node(id));
		result.setId(id);
		return result;
	}

	/**
	 * Creates a new web workspace instance with the given backing storage.
	 * @param store The storage where the workspace is persisted.
	 */
	public WebWorkspace(IEclipsePreferences store) {
		super(store);
	}

	/**
	 * Adds a project to the list of projects that belong to this workspace.
	 */
	public void addProject(WebProject project) {
		JSONArray allProjects = getProjectsJSON();
		//make sure we don't already have it
		String newProjectId = project.getId();
		for (int i = 0; i < allProjects.length(); i++) {
			try {
				JSONObject existing = (JSONObject) allProjects.get(i);
				if (newProjectId.equals(existing.get(ProtocolConstants.KEY_ID)))
					return;
			} catch (JSONException e) {
				//ignore empty slots
			}
		}
		//finally add the project to the workspace
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

	/**
	 * Returns the project in this workspace with the given name, or <code>null</code>
	 * if there is no such project.
	 */
	public WebProject getProjectByName(String name) {
		JSONArray projects = getProjectsJSON();
		for (int i = 0; i < projects.length(); i++) {
			try {
				JSONObject projectJSON = (JSONObject) projects.get(i);
				WebProject project = WebProject.fromId(projectJSON.optString(ProtocolConstants.KEY_ID, ""));
				if (name.equals(project.getName()))
					return project;
			} catch (JSONException e) {
				//ignore and keep looking
			}
		}
		return null;

	}

	/**
	 * Returns the next available project id. The id is guaranteed to be globally unique within
	 * this server.
	 * @return the next available project id, or <code>null</code> if an id could not be allocated
	 */
	public static String nextWorkspaceId() {
		synchronized (workspaceCounter) {
			String candidate;
			do {
				candidate = workspaceCounter.toString();
				workspaceCounter.increment();
			} while (exists(candidate));
			return candidate;
		}
	}

	/**
	 * Returns whether a workspace with the given id already exists.
	 * @param id The id of the workspace 
	 * @return <code>true</code> if the workspace already exists, and <code>false</code> otherwise.
	 */
	public static boolean exists(String id) {
		try {
			return scope.getNode(WORKSPACE_NODE_NAME).nodeExists(id);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Remove a project from the list of projects that belong to this workspace.
	 */
	public void removeProject(WebProject project) {
		String existingProjects = store.get(ProtocolConstants.KEY_PROJECTS, "[]");
		JSONArray allProjects;
		try {
			allProjects = new JSONArray(existingProjects);
		} catch (JSONException e) {
			//someone messed with the backing store and inserted something invalid- just wipe it out
			allProjects = new JSONArray();
		}
		//find the project to remove from the workspace
		String newProjectId = project.getId();
		int index = -1;
		for (int i = 0; i < allProjects.length(); i++) {
			try {
				if (newProjectId.equals(((JSONObject) (allProjects.get(i))).get("Id"))) {
					index = i;
					break;
				}
			} catch (JSONException e) {
				//ignore empty slots
			}
		}

		if (index == -1)
			return;
		allProjects.remove(index);
		store.put(ProtocolConstants.KEY_PROJECTS, allProjects.toString());
	}

	/**
	 * Returns a JSON array of the projects in this workspace. Each entry in
	 * the result array is a JSON object, including the unique project Id,
	 * and any workspace-specific properties associated with that project.
	 */
	public JSONArray getProjectsJSON() {
		try {
			String projects = store.get(ProtocolConstants.KEY_PROJECTS, null);
			if (projects != null)
				return new JSONArray(projects);
		} catch (JSONException e) {
			//someone has bashed the underlying storage - just fall through below
		}
		//just return empty array
		JSONArray result = new JSONArray();
		return result;
	}

}
