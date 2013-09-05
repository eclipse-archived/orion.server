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
import java.util.Map;

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

/**
 * A simple implementation of a {@code IMetaStore}.
 * <p>
 * The design of the meta store is simply a hierarchy of files and folders:
 * <ul>
 * <li>Each user has their own folder with a user.json and a list of folders, each of which is a workspace.</li>
 * <li>Workspaces and projects are organized in folders in a hierarchy under the user they belong to.</li>
 * <li>A workspace is a folder containing workspace.json.</li>
 * <li>A project is a folder containing project.json.</li></ul> 
 * </p>
 * @author Anthony Hunter
 *
 */
public class SimpleMetaStore implements IMetaStore {

	public final static String ORION_METASTORE_VERSION = "OrionMetastoreVersion";
	public final static int VERSION = 1;
	public final static String USER = "user";
	public final static String PROJECT = "project";
	public final static String WORKSPACE = "workspace";

	private File metaStoreRoot = null;

	/**
	 * Create an instance of a SimpleMetaStore under the provided folder.
	 * @param rootLocation The root location, a folder on the server.
	 */
	public SimpleMetaStore(File rootLocation) {
		super();
		initializeMetaStore(rootLocation);
	}

	public void createProject(ProjectInfo projectInfo) throws CoreException {
		if (projectInfo.getWorkspaceId() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: workspace id is null.", null));
		}
		WorkspaceInfo workspaceInfo;
		try {
			workspaceInfo = readWorkspace(projectInfo.getWorkspaceId());
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: could not find workspace with id:" + projectInfo.getWorkspaceId() + ", workspace does not exist.", null));
		}
		if (workspaceInfo == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: could not find workspace with id:" + projectInfo.getWorkspaceId() + ", workspace does not exist.", null));
		}
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(projectInfo.getWorkspaceId());
		String workspaceId = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(projectInfo.getWorkspaceId());
		String projectId = projectInfo.getFullName();
		projectInfo.setUniqueId(projectId);

		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ORION_METASTORE_VERSION, VERSION);
			jsonObject.put("UniqueId", projectInfo.getUniqueId());
			jsonObject.put("FullName", projectInfo.getFullName());
			jsonObject.put("ContentLocation", projectInfo.getContentLocation());
			JSONObject properties = new JSONObject(projectInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + " for user " + userId, e));
		}
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceId);
		if (!SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + ", cannot find workspace " + projectInfo.getWorkspaceId(), null));
		}
		if (!SimpleMetaStoreUtil.createMetaFolder(workspaceMetaFolder, projectInfo.getFullName())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + " for user " + userId, null));
		}
		File projectMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectInfo.getFullName());
		if (!SimpleMetaStoreUtil.createMetaFile(projectMetaFolder, PROJECT, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + " for user " + userId, null));
		}

		// Update the workspace with the new projectName
		List<String> newProjectNames = new ArrayList<String>();
		newProjectNames.addAll(workspaceInfo.getProjectNames());
		newProjectNames.add(projectInfo.getFullName());
		workspaceInfo.setProjectNames(newProjectNames);
		updateWorkspace(workspaceInfo);
	}

	public void createUser(UserInfo userInfo) throws CoreException {
		if (userInfo.getUserName() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userInfo.getUniqueId() + ", did not provide a userName", null));
		}
		userInfo.setUniqueId(userInfo.getUserName());
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userInfo.getUserName());
		if (SimpleMetaStoreUtil.isMetaFolder(userMetaFolder)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userInfo.getUserName() + ", user already exists", null));
		}
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ORION_METASTORE_VERSION, VERSION);
			jsonObject.put("UniqueId", userInfo.getUniqueId());
			jsonObject.put("UserName", userInfo.getUserName());
			jsonObject.put("FullName", userInfo.getFullName());
			jsonObject.put("WorkspaceIds", new JSONArray());
			JSONObject properties = getUserProperties(userInfo);
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userInfo.getUserName(), e));
		}
		if (!SimpleMetaStoreUtil.createMetaUserFolder(metaStoreRoot, userInfo.getUserName())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userInfo.getUserName(), null));
		}
		if (!SimpleMetaStoreUtil.createMetaFile(userMetaFolder, USER, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userInfo.getUserName(), null));
		}
	}

	public void createWorkspace(WorkspaceInfo workspaceInfo) throws CoreException {
		if (workspaceInfo.getUserId() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: user id is null.", null));
		}
		if (workspaceInfo.getFullName() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: workspace name is null.", null));
		}
		UserInfo userInfo;
		try {
			userInfo = readUser(workspaceInfo.getUserId());
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: could not find user with id: " + workspaceInfo.getUserId() + ", user does not exist.", null));
		}
		if (userInfo == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: could not find user with id: " + workspaceInfo.getUserId() + ", user does not exist.", null));
		}
		// We create a meta folder for the workspace using the encoded workspace name
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(workspaceInfo.getUserId(), workspaceInfo.getFullName());
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		// It is possible to have two workspaces with the same name, so append an integer to it if this is a duplicate name.
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userInfo.getUniqueId());
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, encodedWorkspaceName);
		int suffix = 0;
		while (SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder)) {
			suffix++;
			workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(workspaceInfo.getUserId(), workspaceInfo.getFullName() + suffix);
			encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
			workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, encodedWorkspaceName);
		}
		workspaceInfo.setUniqueId(workspaceId);
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ORION_METASTORE_VERSION, VERSION);
			jsonObject.put("UniqueId", workspaceInfo.getUniqueId());
			jsonObject.put("FullName", workspaceInfo.getFullName());
			jsonObject.put("ProjectNames", new JSONArray());
			JSONObject properties = new JSONObject(workspaceInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: could not create workspace: " + encodedWorkspaceName + " for user " + userInfo.getUserName(), e));
		}
		if (!SimpleMetaStoreUtil.createMetaFolder(userMetaFolder, encodedWorkspaceName)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: could not create workspace: " + encodedWorkspaceName + " for user " + userInfo.getUserName(), null));
		}
		if (!SimpleMetaStoreUtil.createMetaFile(workspaceMetaFolder, WORKSPACE, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: could not create workspace: " + encodedWorkspaceName + " for user " + userInfo.getUserName(), null));
		}

		// Update the user with the new workspaceId
		List<String> newWorkspaceIds = new ArrayList<String>();
		newWorkspaceIds.addAll(userInfo.getWorkspaceIds());
		newWorkspaceIds.add(workspaceId);
		userInfo.setWorkspaceIds(newWorkspaceIds);
		updateUser(userInfo);
	}

	public void deleteProject(String workspaceId, String projectName) throws CoreException {
		String userName = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		String workspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		File projectMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectName);

		// Update the workspace, remove the deleted projectName
		WorkspaceInfo workspaceInfo;
		try {
			workspaceInfo = readWorkspace(workspaceId);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteProject: could not find project with name:" + projectName + ", workspace does not exist.", null));
		}
		List<String> newProjectIds = new ArrayList<String>();
		newProjectIds.addAll(workspaceInfo.getProjectNames());
		newProjectIds.remove(projectName);
		workspaceInfo.setProjectNames(newProjectIds);
		updateWorkspace(workspaceInfo);

		// delete the meta file and folder
		if (!SimpleMetaStoreUtil.deleteMetaFile(projectMetaFolder, PROJECT)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteProject: could not delete project: " + projectName, null));
		}
		if (!SimpleMetaStoreUtil.deleteMetaFolder(projectMetaFolder)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteProject: could not delete project: " + projectName, null));
		}
	}

	public void deleteUser(String userId) throws CoreException {
		UserInfo userInfo;
		try {
			userInfo = readUser(userId);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteUser: could not delete user with id: " + userId + ", user does not exist.", null));
		}

		// First delete the workspaces
		for (String workspaceId : userInfo.getWorkspaceIds()) {
			deleteWorkspace(userId, workspaceId);
		}

		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userInfo.getUserName());
		if (!SimpleMetaStoreUtil.deleteMetaFile(userMetaFolder, USER)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteUser: could not delete user: " + userInfo.getUserName(), null));
		}
		if (!SimpleMetaStoreUtil.deleteMetaUserFolder(userMetaFolder, userInfo.getUserName())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteUser: could not delete user: " + userInfo.getUserName(), null));
		}
	}

	public void deleteWorkspace(String userId, String workspaceId) throws CoreException {
		String workspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);

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
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteWorkspace: could not find user with id:" + userId + ", user does not exist.", null));
		}
		List<String> newWorkspaceIds = new ArrayList<String>();
		newWorkspaceIds.addAll(userInfo.getWorkspaceIds());
		newWorkspaceIds.remove(workspaceId);
		userInfo.setWorkspaceIds(newWorkspaceIds);
		updateUser(userInfo);

		// delete the meta file and folder
		if (!SimpleMetaStoreUtil.deleteMetaFile(workspaceMetaFolder, WORKSPACE)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteWorkspace: could not delete workspace: " + workspaceName, null));
		}
		if (!SimpleMetaStoreUtil.deleteMetaFolder(workspaceMetaFolder)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteWorkspace: could not delete workspace: " + workspaceName, null));
		}
	}

	/**
	 * Gets the properties in the provided MetaDataInfo.
	 * @param metadataInfo The MetaData info
	 * @return the JSON with a list of properties.
	 * @throws JSONException
	 */
	private JSONObject getUserProperties(UserInfo userInfo) throws JSONException {
		JSONObject jsonObject = new JSONObject();
		Map<String, String> properties = userInfo.getProperties();
		for (String key : properties.keySet()) {
			String value = properties.get(key);
			if ("UserRights".equals(key)) {
				// UserRights needs to be handled specifically since it is a JSONArray and not a string.
				JSONArray userRights = new JSONArray(value);
				jsonObject.put("UserRights", userRights);
			} else if ("SiteConfigurations".equals(key)) {
					// UserRights needs to be handled specifically since it is a JSONObject and not a string.
					JSONObject siteConfigurations = new JSONObject(value);
					jsonObject.put("SiteConfigurations", siteConfigurations);
			} else {
				jsonObject.put(key, value);
			}
		}
		return jsonObject;
	}

	/**
	 * Initialize the simple meta store.
	 * @param rootLocation The root location, a folder on the server.
	 */
	private void initializeMetaStore(File rootLocation) {
		if (!SimpleMetaStoreUtil.isMetaStoreRoot(rootLocation)) {
			// Create a new MetaStore
			JSONObject jsonObject = new JSONObject();
			try {
				jsonObject.put(ORION_METASTORE_VERSION, VERSION);
			} catch (JSONException e) {
				throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not create MetaStore");
			}
			if (!SimpleMetaStoreUtil.createMetaStoreRoot(rootLocation, jsonObject)) {
				throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not create MetaStore");
			}
		} else {
			// Verify we have a MetaStore
			JSONObject jsonObject = SimpleMetaStoreUtil.retrieveMetaStoreRootJSON(rootLocation);
			try {
				if (jsonObject == null || jsonObject.getInt(ORION_METASTORE_VERSION) != VERSION) {
					throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not read MetaStore");
				}
			} catch (JSONException e) {
				throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not read MetaStore");
			}
		}
		this.metaStoreRoot = new File(rootLocation, SimpleMetaStoreUtil.ROOT);
	}

	public List<String> readAllUsers() throws CoreException {
		return SimpleMetaStoreUtil.listMetaUserFolders(metaStoreRoot);
	}

	public ProjectInfo readProject(String workspaceId, String projectName) throws CoreException {
		String userName = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		String workspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		File projectMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectName);
		JSONObject jsonObject = SimpleMetaStoreUtil.retrieveMetaFileJSON(projectMetaFolder, PROJECT);
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
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readProject: could not read project " + projectName, e));
		} catch (URISyntaxException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readProject: could not read project " + projectName, e));
		}
		return projectInfo;
	}

	public UserInfo readUser(String userId) throws CoreException {
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userId);
		if (!SimpleMetaStoreUtil.isMetaFolder(userMetaFolder)) {
			// user does not exist for this userId, create it ( see Bug 415505 )
			UserInfo userInfo = new UserInfo();
			userInfo.setUniqueId(userId);
			userInfo.setUserName(userId);
			userInfo.setFullName("Unnamed User");
			createUser(userInfo);
			return userInfo;
		}
		JSONObject jsonObject = SimpleMetaStoreUtil.retrieveMetaFileJSON(userMetaFolder, USER);
		if (jsonObject == null) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.info("SimpleMetaStore.readUser: could not read user " + userId); //$NON-NLS-1$
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
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readUser: could not read user " + userId, e));
		}
		return userInfo;
	}

	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException {
		if (workspaceId == null) {
			return null;
		}
		String userName = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		String workspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		if (userName == null || workspaceName == null) {
			// could not decode, so cannot find workspace
			return null;
		}
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		JSONObject jsonObject = SimpleMetaStoreUtil.retrieveMetaFileJSON(workspaceMetaFolder, WORKSPACE);
		if (jsonObject == null) {
			return null;
		}
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
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readWorkspace: could not read workspace " + workspaceName, e));
		}
		return workspaceInfo;
	}

	/**
	 * Sets all the properties in the provided JSON into the MetaDataInfo.
	 * @param metadataInfo The MetaData info
	 * @param jsonObject the JSON with a list of properties.
	 * @throws JSONException
	 */
	private void setProperties(MetadataInfo metadataInfo, JSONObject jsonObject) throws JSONException {
		String[] properties = JSONObject.getNames(jsonObject);
		if (properties != null) {
			for (String key : properties) {
				Object value = jsonObject.get(key);
				metadataInfo.setProperty(key, value.toString());
			}
		}
	}

	public void updateProject(ProjectInfo projectInfo) throws CoreException {
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(projectInfo.getWorkspaceId());
		String workspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(projectInfo.getWorkspaceId());
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ORION_METASTORE_VERSION, VERSION);
			jsonObject.put("UniqueId", projectInfo.getUniqueId());
			jsonObject.put("FullName", projectInfo.getFullName());
			jsonObject.put("ContentLocation", projectInfo.getContentLocation());
			JSONObject properties = new JSONObject(projectInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
		}
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		File projectMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectInfo.getUniqueId());
		if (!SimpleMetaStoreUtil.updateMetaFile(projectMetaFolder, PROJECT, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not update project: " + projectInfo.getUniqueId() + " for workspace " + workspaceName, null));
		}
	}

	public void updateUser(UserInfo userInfo) throws CoreException {
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userInfo.getUserName());
		JSONObject jsonObject = SimpleMetaStoreUtil.retrieveMetaFileJSON(userMetaFolder, USER);
		if (jsonObject == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not find user " + userInfo.getUserName(), null));
		}
		try {
			jsonObject.put("UniqueId", userInfo.getUniqueId());
			jsonObject.put("UserName", userInfo.getUserName());
			jsonObject.put("FullName", userInfo.getFullName());
			JSONArray workspaceIds = new JSONArray(userInfo.getWorkspaceIds());
			jsonObject.put("WorkspaceIds", workspaceIds);
			JSONObject properties = getUserProperties(userInfo);
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not update user: " + userInfo.getUserName(), e));
		}
		if (!SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, USER, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not update user: " + userInfo.getUserName(), null));
		}
	}

	public void updateWorkspace(WorkspaceInfo workspaceInfo) throws CoreException {
		String userName = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceInfo.getUniqueId());
		String workspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceInfo.getUniqueId());
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ORION_METASTORE_VERSION, VERSION);
			jsonObject.put("UniqueId", workspaceInfo.getUniqueId());
			jsonObject.put("FullName", workspaceInfo.getFullName());
			JSONArray projectNames = new JSONArray(workspaceInfo.getProjectNames());
			jsonObject.put("ProjectNames", projectNames);
			JSONObject properties = new JSONObject(workspaceInfo.getProperties());
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateWorkspace: could not update workspace: " + workspaceName + " for user " + userName, e));
		}
		File userMetaFolder = SimpleMetaStoreUtil.retrieveMetaUserFolder(metaStoreRoot, userName);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, workspaceName);
		if (!SimpleMetaStoreUtil.updateMetaFile(workspaceMetaFolder, WORKSPACE, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateWorkspace: could not update workspace: " + workspaceName + " for user " + userName, null));
		}
	}
}
