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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.MetadataInfo;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleLinuxMetaStore implements IMetaStore {

	public final static String NAME = "orion.metastore.version";
	public final static int VERSION = 1;
	public final static String USER = "user";
	public final static String PROJECT = "project";
	public final static String WORKSPACE = "workspace";

	private File metaStoreRoot = null;

	public SimpleLinuxMetaStore(File rootLocation) {
		super();
		initializeMetaStore(rootLocation);
	}

	public void createProject(ProjectInfo projectInfo) throws CoreException {
		if (projectInfo.getWorkspaceId() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createProject: workspace id is null.", null));
		}
		WorkspaceInfo workspaceInfo;
		try {
			workspaceInfo = readWorkspace(projectInfo.getWorkspaceId());
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createProject: could not find workspace with id:" + projectInfo.getWorkspaceId() + ", workspace does not exist.", null));
		}
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(projectInfo.getWorkspaceId());
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(projectInfo.getWorkspaceId());
		String projectId = SimpleLinuxMetaStoreUtil.encodeProjectId(projectInfo.getFullName());
		projectInfo.setUniqueId(projectId);

		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(NAME, VERSION);
			jsonObject.put("UniqueId", projectInfo.getUniqueId());
			jsonObject.put("FullName", projectInfo.getFullName());
			jsonObject.put("ContentLocation", projectInfo.getContentLocation());
			JSONObject properties = new JSONObject(projectInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
		}
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		if (!SimpleLinuxMetaStoreUtil.createMetaFolder(workspaceMetaFolder, projectInfo.getFullName())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createProject: could not create workspace: " + workspaceName + " for user " + userName, null));
		}
		File projectMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectInfo.getFullName());
		if (!SimpleLinuxMetaStoreUtil.createMetaFile(projectMetaFolder, PROJECT, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createProject: could not create workspace: " + workspaceName + " for user " + userName, null));
		}

		// Update the workspace with the new projectName
		List<String> newProjectNames = new ArrayList<String>();
		newProjectNames.addAll(workspaceInfo.getProjectNames());
		newProjectNames.add(projectInfo.getFullName());
		workspaceInfo.setProjectNames(newProjectNames);
		updateWorkspace(workspaceInfo);
	}

	public void createUser(UserInfo userInfo) throws CoreException {
		if (!SimpleLinuxMetaStoreUtil.isNameValid(userInfo.getUserName())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createUser: userName is not valid: " + userInfo.getUserName(), null));
		}
		if (userInfo.getUniqueId() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createUser: could not create user: " + userInfo.getUserName() + ", did not provide a userId", null));
		}
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userInfo.getUserName());
		if (SimpleLinuxMetaStoreUtil.isMetaFolder(userMetaFolder)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createUser: could not create user: " + userInfo.getUserName() + ", user already exists", null));
		}
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(NAME, VERSION);
			jsonObject.put("UniqueId", userInfo.getUniqueId());
			jsonObject.put("UserName", userInfo.getUserName());
			jsonObject.put("FullName", userInfo.getFullName());
			jsonObject.put("WorkspaceIds", new JSONArray());
			JSONObject properties = new JSONObject(userInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
		}
		if (!SimpleLinuxMetaStoreUtil.createMetaFolder(metaStoreRoot, userInfo.getUserName())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createUser: could not create user: " + userInfo.getUserName(), null));
		}
		if (!SimpleLinuxMetaStoreUtil.createMetaFile(userMetaFolder, USER, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createUser: could not create user: " + userInfo.getUserName(), null));
		}
	}

	public void createWorkspace(WorkspaceInfo workspaceInfo) throws CoreException {
		if (workspaceInfo.getUserId() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: user id is null.", null));
		}
		UserInfo userInfo;
		try {
			userInfo = readUser(workspaceInfo.getUserId());
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: could not find user with id:" + workspaceInfo.getUserId() + ", user does not exist.", null));
		}
		// We create a meta folder for the workspace using the workspace name
		// It is possible to have two workspaces with the same getFullName, so append an integer to it if this is a duplicate name.
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userInfo.getUserName());
		File workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceInfo.getFullName());
		String newFullName = workspaceInfo.getFullName();
		int suffix = 0;
		while (SimpleLinuxMetaStoreUtil.isMetaFolder(workspaceMetaFolder)) {
			suffix++;
			newFullName = workspaceInfo.getFullName() + suffix;
			workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, newFullName);
		}
		String workspaceId = SimpleLinuxMetaStoreUtil.encodeWorkspaceId(userInfo.getUserName(), newFullName);
		workspaceInfo.setUniqueId(workspaceId);
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(NAME, VERSION);
			jsonObject.put("UniqueId", workspaceInfo.getUniqueId());
			jsonObject.put("FullName", workspaceInfo.getFullName());
			jsonObject.put("ProjectNames", new JSONArray());
			JSONObject properties = new JSONObject(workspaceInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
		}
		if (!SimpleLinuxMetaStoreUtil.createMetaFolder(userMetaFolder, newFullName)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: could not create workspace: " + newFullName + " for user " + userInfo.getUserName(), null));
		}
		if (!SimpleLinuxMetaStoreUtil.createMetaFile(workspaceMetaFolder, WORKSPACE, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.createWorkspace: could not create workspace: " + newFullName + " for user " + userInfo.getUserName(), null));
		}

		// Update the user with the new workspaceId
		List<String> newWorkspaceIds = new ArrayList<String>();
		newWorkspaceIds.addAll(userInfo.getWorkspaceIds());
		newWorkspaceIds.add(workspaceId);
		userInfo.setWorkspaceIds(newWorkspaceIds);
		updateUser(userInfo);
	}

	public void deleteProject(String workspaceId, String projectName) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(workspaceId);
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		File projectMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectName);

		// Update the workspace, remove the deleted projectName
		WorkspaceInfo workspaceInfo;
		try {
			workspaceInfo = readWorkspace(workspaceId);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteProject: could not find project with name:" + projectName + ", workspace does not exist.", null));
		}
		List<String> newProjectIds = new ArrayList<String>();
		newProjectIds.addAll(workspaceInfo.getProjectNames());
		newProjectIds.remove(projectName);
		workspaceInfo.setProjectNames(newProjectIds);
		updateWorkspace(workspaceInfo);

		// delete the meta file and folder
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFile(projectMetaFolder, PROJECT)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteProject: could not delete project: " + projectName, null));
		}
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFolder(projectMetaFolder)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteProject: could not delete project: " + projectName, null));
		}
	}

	public void deleteUser(String userId) throws CoreException {
		UserInfo userInfo;
		try {
			userInfo = readUser(userId);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteUser: could not delete user with id: " + userId + ", user does not exist.", null));
		}

		// First delete the workspaces
		for (String workspaceId : userInfo.getWorkspaceIds()) {
			deleteWorkspace(userId, workspaceId);
		}

		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userInfo.getUserName());
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFile(userMetaFolder, USER)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteUser: could not delete user: " + userInfo.getUserName(), null));
		}
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFolder(userMetaFolder)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteUser: could not delete user: " + userInfo.getUserName(), null));
		}
	}

	public void deleteWorkspace(String userId, String workspaceId) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(workspaceId);
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);

		// Delete the projects in the workspace
		WorkspaceInfo workspaceInfo = readWorkspace(workspaceId);
		for (String projectName : workspaceInfo.getProjectNames()) {
			deleteProject(workspaceId, projectName);
		}

		// Update the user remove the deleted workspaceId
		UserInfo userInfo;
		try {
			userInfo = readUser(userId);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteWorkspace: could not find user with id:" + userId + ", user does not exist.", null));
		}
		List<String> newWorkspaceIds = new ArrayList<String>();
		newWorkspaceIds.addAll(userInfo.getWorkspaceIds());
		newWorkspaceIds.remove(workspaceId);
		userInfo.setWorkspaceIds(newWorkspaceIds);
		updateUser(userInfo);

		// delete the meta file and folder
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFile(workspaceMetaFolder, WORKSPACE)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteWorkspace: could not delete workspace: " + workspaceName, null));
		}
		if (!SimpleLinuxMetaStoreUtil.deleteMetaFolder(workspaceMetaFolder)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.deleteWorkspace: could not delete workspace: " + workspaceName, null));
		}
	}

	private void initializeMetaStore(File rootLocation) {
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
			JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaStoreRootJSON(rootLocation);
			try {
				if (jsonObject == null || jsonObject.getInt(NAME) != VERSION) {
					throw new RuntimeException("SimpleLinuxMetaStore.initializeMetaStore: could not read MetaStore");
				}
			} catch (JSONException e) {
			}
		}
		this.metaStoreRoot = new File(rootLocation, SimpleLinuxMetaStoreUtil.ROOT);
	}

	public List<String> readAllUsers() throws CoreException {
		List<String> userNames = SimpleLinuxMetaStoreUtil.listMetaFiles(metaStoreRoot);
		List<String> userIds = new ArrayList<String>();
		for (String userName : userNames) {
			File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
			JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFileJSON(userMetaFolder, USER);
			try {
				userIds.add(jsonObject.getString("UniqueId"));
			} catch (JSONException e) {
			}
		}
		return userIds;
	}

	public ProjectInfo readProject(String workspaceId, String projectName) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(workspaceId);
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		File projectMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectName);
		JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFileJSON(projectMetaFolder, PROJECT);
		if (jsonObject == null) {
			return null;
		}
		ProjectInfo projectInfo = new ProjectInfo();
		try {
			projectInfo.setUniqueId(jsonObject.getString("UniqueId"));
			projectInfo.setFullName(jsonObject.getString("FullName"));
			projectInfo.setContentLocation(new URI(jsonObject.getString("ContentLocation")));
			setProperties(projectInfo, jsonObject.getJSONObject("Properties"));
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.readProject: could not read project " + projectName, e));
		} catch (URISyntaxException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.readProject: could not read project " + projectName, e));
		}
		return projectInfo;
	}

	public UserInfo readUser(String userId) throws CoreException {
		String userName = findUserNameFromUserId(userId);
		if (userName == null) {
			return null;
		}
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
		JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFileJSON(userMetaFolder, USER);
		if (jsonObject == null) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.info("SimpleLinuxMetaStore.readUser: could not read user " + userName); //$NON-NLS-1$
			return null;
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
			setProperties(userInfo, jsonObject.getJSONObject("Properties"));
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.readUser: could not read user " + userName, e));
		}
		return userInfo;
	}

	private String findUserNameFromUserId(String userId) {
		List<String> userNames = SimpleLinuxMetaStoreUtil.listMetaFiles(metaStoreRoot);
		for (String userName : userNames) {
			File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
			JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFileJSON(userMetaFolder, USER);
			try {
				String uniqueId = jsonObject.getString("UniqueId");
				if (uniqueId != null && uniqueId.equals(userId)) {
					return userName;
				}
			} catch (JSONException e) {
			}
		}
		return null;
	}

	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(workspaceId);
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFileJSON(workspaceMetaFolder, WORKSPACE);
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
			setProperties(workspaceInfo, jsonObject.getJSONObject("Properties"));
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.readWorkspace: could not read workspace " + workspaceName, e));
		}
		return workspaceInfo;
	}

	public void updateProject(ProjectInfo projectInfo) throws CoreException {
		String userName = SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(projectInfo.getWorkspaceId());
		String workspaceName = SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(projectInfo.getWorkspaceId());
		String projectName = SimpleLinuxMetaStoreUtil.decodeProjectNameFromProjectId(projectInfo.getUniqueId());
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(NAME, VERSION);
			jsonObject.put("UniqueId", projectInfo.getUniqueId());
			jsonObject.put("FullName", projectInfo.getFullName());
			jsonObject.put("ContentLocation", projectInfo.getContentLocation());
			JSONObject properties = new JSONObject(projectInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
		}
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		File projectMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectName);
		if (!SimpleLinuxMetaStoreUtil.updateMetaFile(projectMetaFolder, PROJECT, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.updateProject: could not update project: " + projectName + " for workspace " + workspaceName, null));
		}
	}

	public void updateUser(UserInfo userInfo) throws CoreException {
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userInfo.getUserName());
		JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFileJSON(userMetaFolder, USER);
		if (jsonObject == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.updateUser: could not find user " + userInfo.getUserName(), null));
		}
		try {
			jsonObject.put("UniqueId", userInfo.getUniqueId());
			jsonObject.put("UserName", userInfo.getUserName());
			jsonObject.put("FullName", userInfo.getFullName());
			JSONArray workspaceIds = new JSONArray(userInfo.getWorkspaceIds());
			jsonObject.put("WorkspaceIds", workspaceIds);
			JSONObject properties = new JSONObject(userInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
		}
		if (!SimpleLinuxMetaStoreUtil.updateMetaFile(userMetaFolder, USER, jsonObject)) {
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
			JSONObject properties = new JSONObject(workspaceInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
		}
		File userMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		if (!SimpleLinuxMetaStoreUtil.updateMetaFile(workspaceMetaFolder, WORKSPACE, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleLinuxMetaStore.updateWorkspace: could not update workspace: " + workspaceName + " for user " + userName, null));
		}
	}

	/**
	 * Sets all the properties from JSON in the MetaDataInfo.
	 * @throws JSONException 
	 */
	private void setProperties(MetadataInfo metadataInfo, JSONObject jsonObject) throws JSONException {
		String[] properties = JSONObject.getNames(jsonObject);
		if (properties != null) {
			for (String key : properties) {
				String value = jsonObject.getString(key);
				metadataInfo.setProperty(key, value);
			}
		}
	}

}
