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
package org.eclipse.orion.internal.server.core.metastore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.resources.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SimpleLinuxMetaStore implements IMetaStore {

	public final static String NAME = "orion.metastore.version";
	public final static int VERSION = 1;
	public final static String USER = "user";
	public final static String PROJECT = "project";
	public final static String WORKSPACE = "workspace";

	private URI metaStoreRoot = null;

	public SimpleLinuxMetaStore(URI rootLocation) {
		super();
		initializeMetaStore(rootLocation);
	}

	public void createProject(String workspaceId, ProjectInfo info) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void createUser(UserInfo userInfo) throws CoreException {
		if (!SimpleLinuxMetaStoreUtil.isNameValid(userInfo.getUserName())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createUser: userName is not valid: " + userInfo.getUserName(), null));
		}
		userInfo.setUniqueId(SimpleLinuxMetaStoreUtil.encodeUserId(userInfo.getUserName()));
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(NAME, VERSION);
			jsonObject.put("UniqueId", userInfo.getUniqueId());
			jsonObject.put("UserName", userInfo.getUserName());
			jsonObject.put("FullName", userInfo.getFullName());
			jsonObject.put("WorkspaceIds", new JSONArray());
		} catch (JSONException e) {
		}
		if (!SimpleLinuxMetaStoreUtil.createMetaFolder(metaStoreRoot, userInfo.getUserName())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createUser: could not create user: " + userInfo.getUserName(), null));
		}
		URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userInfo.getUserName());
		if (!SimpleLinuxMetaStoreUtil.createMetaFile(userMetaFolderURI, USER, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createUser: could not create user: " + userInfo.getUserName(), null));
		}
	}

	public void createWorkspace(String userId, WorkspaceInfo workspaceInfo) throws CoreException {
		// we know the default workspace name is "Orion Content", so fix according to the naming rules
		String workspaceName = workspaceInfo.getFullName().toLowerCase().replace(' ', '-');
		if (!SimpleLinuxMetaStoreUtil.isNameValid(workspaceName)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: workspace name is not valid: " + workspaceInfo.getFullName(), null));
		}
		UserInfo userInfo;
		try {
			userInfo = readUser(userId);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: could not find user with id:" + userId + ", user does not exist.", null));
		}
		String workspaceId = SimpleLinuxMetaStoreUtil.encodeWorkspaceId(userInfo.getUserName(), workspaceName);
		workspaceInfo.setUniqueId(workspaceId);
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(NAME, VERSION);
			jsonObject.put("UniqueId", workspaceInfo.getUniqueId());
			jsonObject.put("FullName", workspaceInfo.getFullName());
			jsonObject.put("ProjectNames", new JSONArray());
		} catch (JSONException e) {
		}
		URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userInfo.getUserName());
		if (!SimpleLinuxMetaStoreUtil.createMetaFolder(userMetaFolderURI, workspaceName)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: could not create workspace: " + workspaceId + " for user " + userInfo.getUserName(), null));
		}
		URI workspaceMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(userMetaFolderURI, workspaceName);
		if (!SimpleLinuxMetaStoreUtil.createMetaFile(workspaceMetaFolderURI, WORKSPACE, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: could not create workspace: " + workspaceId + " for user " + userInfo.getUserName(), null));
		}

		// Update the user with the new workspaceId
		List<String> newWorkspaceIds = new ArrayList<String>();
		newWorkspaceIds.addAll(userInfo.getWorkspaceIds());
		newWorkspaceIds.add(workspaceId);
		userInfo.setWorkspaceIds(newWorkspaceIds);
		updateUser(userInfo);
	}

	public void deleteProject(String workspaceId, String projectName) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void deleteUser(String userId) throws CoreException {
		UserInfo userInfo;
		try {
			userInfo = readUser(userId);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteUser: could not delete user with id: " + userId + ", user does not exist.", null));
		}
		URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userInfo.getUserName());
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFile(userMetaFolderURI, USER)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteUser: could not delete user: " + userInfo.getUserName(), null));
		}
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFolder(userMetaFolderURI)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteUser: could not delete user: " + userInfo.getUserName(), null));
		}
	}

	public void deleteWorkspace(String userId, String workspaceId) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(workspaceId);
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userName);
		URI workspaceMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(userMetaFolderURI, workspaceName);

		// Update the user remove the deleted workspaceId
		UserInfo userInfo;
		try {
			userInfo = readUser(userId);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: could not find user with id:" + userId + ", user does not exist.", null));
		}
		List<String> newWorkspaceIds = new ArrayList<String>();
		newWorkspaceIds.addAll(userInfo.getWorkspaceIds());
		newWorkspaceIds.remove(workspaceId);
		userInfo.setWorkspaceIds(newWorkspaceIds);
		updateUser(userInfo);

		// delete the meta file and folder
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFile(workspaceMetaFolderURI, WORKSPACE)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteWorkspace: could not delete workspace: " + workspaceName, null));
		}
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFolder(workspaceMetaFolderURI)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteWorkspace: could not delete workspace: " + workspaceName, null));
		}
	}

	private void initializeMetaStore(URI rootLocation) {
		if (!SimpleLinuxMetaStoreUtil.isMetaStoreRoot(rootLocation)) {
			// Create a new MetaStore
			JSONObject jsonObject = new JSONObject();
			try {
				jsonObject.put(NAME, VERSION);
			} catch (JSONException e) {
			}
			if (!SimpleLinuxMetaStoreUtil.createMetaStoreRoot(rootLocation, jsonObject)) {
				throw new RuntimeException("SimpleLinuxMetaStore.initializeMetaStore: could not create MetaStore");
			}
		} else {
			// Verify we have a MetaStore
			JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaStoreRoot(rootLocation);
			try {
				if (jsonObject == null || jsonObject.getInt(NAME) != VERSION) {
					throw new RuntimeException("SimpleLinuxMetaStore.initializeMetaStore: could not read MetaStore");
				}
			} catch (JSONException e) {
			}
		}
		try {
			this.metaStoreRoot = new URI(rootLocation.toString());
		} catch (URISyntaxException e) {
		}
	}

	public List<String> readAllUsers() throws CoreException {
		List<String> userNames = SimpleLinuxMetaStoreUtil.listMetaFiles(metaStoreRoot);
		List<String> userIds = new ArrayList<String>();
		for (String userName : userNames) {
			URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userName);
			JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFile(userMetaFolderURI, USER);
			try {
				userIds.add(jsonObject.getString("UniqueId"));
			} catch (JSONException e) {
			}
		}
		return userIds;
	}

	public ProjectInfo readProject(String workspaceId, String projectName) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public UserInfo readUser(String userId) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromUserId(userId);
		URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userName);
		JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFile(userMetaFolderURI, USER);
		if (jsonObject == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.readUser: could not read user " + userName, null));
		}
		UserInfo userInfo = new UserInfo();
		try {
			userInfo.setUniqueId(jsonObject.getString("UniqueId"));
			userInfo.setUserName(jsonObject.getString("UserName"));
			userInfo.setFullName(jsonObject.getString("FullName"));
			List<String> userWorkspaceIds = new ArrayList<String>();
			JSONArray workspaceIds = jsonObject.getJSONArray("WorkspaceIds");
			if (workspaceIds.length() > 0) {
				for (int i = 0; i < workspaceIds.length(); i++) {
					userWorkspaceIds.add(workspaceIds.getString(i));
				}
			}
			userInfo.setWorkspaceIds(userWorkspaceIds);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.readUser: could not read user " + userName, e));
		}
		return userInfo;
	}

	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(workspaceId);
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userName);
		URI workspaceMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(userMetaFolderURI, workspaceName);
		JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFile(workspaceMetaFolderURI, WORKSPACE);
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		try {
			workspaceInfo.setUniqueId(jsonObject.getString("UniqueId"));
			workspaceInfo.setFullName(jsonObject.getString("FullName"));
			List<String> workspaceProjectNames = new ArrayList<String>();
			JSONArray projectNames = jsonObject.getJSONArray("ProjectNames");
			if (projectNames.length() > 0) {
				for (int i = 0; i < projectNames.length(); i++) {
					workspaceProjectNames.add(projectNames.getString(i));
				}
			}
			workspaceInfo.setProjectNames(workspaceProjectNames);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.readUser: could not read user " + userName, e));
		}
		return workspaceInfo;
	}

	public void updateProject(ProjectInfo project) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void updateUser(UserInfo userInfo) throws CoreException {
		URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userInfo.getUserName());
		JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFile(userMetaFolderURI, USER);
		if (jsonObject == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.updateUser: could not find user " + userInfo.getUserName(), null));
		}
		try {
			jsonObject.put("UniqueId", userInfo.getUniqueId());
			jsonObject.put("UserName", userInfo.getUserName());
			jsonObject.put("FullName", userInfo.getFullName());
			JSONArray workspaceIds = new JSONArray(userInfo.getWorkspaceIds());
			jsonObject.put("WorkspaceIds", workspaceIds);
		} catch (JSONException e) {
		}
		if (!SimpleLinuxMetaStoreUtil.updateMetaFile(userMetaFolderURI, USER, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.updateUser: could not update user: " + userInfo.getUserName(), null));
		}
	}

	public void updateWorkspace(WorkspaceInfo workspaceInfo) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(workspaceInfo.getUniqueId());
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceInfo.getUniqueId());
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(NAME, VERSION);
			jsonObject.put("UniqueId", workspaceInfo.getUniqueId());
			jsonObject.put("FullName", workspaceInfo.getFullName());
			JSONArray projectNames = new JSONArray(workspaceInfo.getProjectNames());
			jsonObject.put("ProjectNames", projectNames);

		} catch (JSONException e) {
		}
		URI userMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(metaStoreRoot, userName);
		URI workspaceMetaFolderURI = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(userMetaFolderURI, workspaceName);
		if (!SimpleLinuxMetaStoreUtil.updateMetaFile(workspaceMetaFolderURI, WORKSPACE, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.updateWorkspace: could not update workspace: " + workspaceName + " for user " + userName, null));
		}
	}

}
