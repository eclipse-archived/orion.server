/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

	public final static String ORION_VERSION = "OrionVersion";
	public final static String PROJECT = "project";
	public static final String ROOT = "metastore";
	public final static String USER = "user";
	public final static int VERSION = 4;
	public final static String WORKSPACE = "workspace";

	// map of read write locks keyed by userId
	private Map<String, ReadWriteLock> lockMap = new ConcurrentHashMap<String, ReadWriteLock>();

	private File rootLocation = null;

	/**
	 * Create an instance of a SimpleMetaStore under the provided folder.
	 * @param rootLocation The root location for storing content and metadata on this server.
	 */
	public SimpleMetaStore(File rootLocation) {
		super();
		initializeMetaStore(rootLocation);
	}

	public void createProject(ProjectInfo projectInfo) throws CoreException {
		if (projectInfo.getWorkspaceId() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: workspace id is null.", null));
		}
		if (projectInfo.getFullName() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: project name is null.", null));
		}
		if (projectInfo.getFullName().equals(WORKSPACE)) {
			// you cannot name a project "workspace".
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: cannot create a project named \"workspace\".", null));
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
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(projectInfo.getWorkspaceId());
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectInfo.getFullName());
		projectInfo.setUniqueId(projectId);

		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ORION_VERSION, VERSION);
			jsonObject.put("UniqueId", projectInfo.getUniqueId());
			jsonObject.put("WorkspaceId", projectInfo.getWorkspaceId());
			jsonObject.put("FullName", projectInfo.getFullName());
			jsonObject.put("ContentLocation", projectInfo.getContentLocation());
			JSONObject properties = updateProperties(jsonObject, projectInfo);
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + " for user " + userId, e));
		}
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		if (!SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId)) {
			// try to create the project folder since it does not exist
			if (!SimpleMetaStoreUtil.createMetaFolder(workspaceMetaFolder, projectId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + " for user " + userId, null));
			}
		}
		if (!SimpleMetaStoreUtil.createMetaFile(workspaceMetaFolder, projectId, jsonObject)) {
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
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user, did not provide a userName", null));
		}
		String userId = userInfo.getUserName();
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
			userInfo.setUniqueId(userId);
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
			if (SimpleMetaStoreUtil.isMetaFolder(rootLocation, userId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId + ", user already exists", null));
			}
			JSONObject jsonObject = new JSONObject();
			try {
				jsonObject.put(ORION_VERSION, VERSION);
				jsonObject.put("UniqueId", userId);
				jsonObject.put("UserName", userId);
				jsonObject.put("FullName", userInfo.getFullName());
				jsonObject.put("WorkspaceIds", new JSONArray());
				JSONObject properties = updateProperties(jsonObject, userInfo);
				jsonObject.put("Properties", properties);
			} catch (JSONException e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId, e));
			}
			if (!SimpleMetaStoreUtil.createMetaUserFolder(rootLocation, userId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId, null));
			}
			if (SimpleMetaStoreUtil.isMetaFile(userMetaFolder, USER)) {
				// If the file already exists then update with the new contents
				if (!SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, USER, jsonObject)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId, null));
				}
			} else {
				if (!SimpleMetaStoreUtil.createMetaFile(userMetaFolder, USER, jsonObject)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId, null));
				}
			}
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.debug("Created new user " + userId + "."); //$NON-NLS-1$
		} finally {
			lock.writeLock().unlock();
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
		// It is possible to have two workspaces with the same name, so append an integer if this is a duplicate name.
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userInfo.getUniqueId());
		int suffix = 0;
		while (SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName)) {
			suffix++;
			workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(workspaceInfo.getUserId(), workspaceInfo.getFullName() + suffix);
			encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		}
		workspaceInfo.setUniqueId(workspaceId);
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ORION_VERSION, VERSION);
			jsonObject.put("UniqueId", workspaceInfo.getUniqueId());
			jsonObject.put("UserId", workspaceInfo.getUserId());
			jsonObject.put("FullName", workspaceInfo.getFullName());
			jsonObject.put("ProjectNames", new JSONArray());
			JSONObject properties = updateProperties(jsonObject, workspaceInfo);
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: could not create workspace: " + encodedWorkspaceName + " for user " + userInfo.getUserName(), e));
		}
		if (!SimpleMetaStoreUtil.createMetaFolder(userMetaFolder, encodedWorkspaceName)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: could not create workspace: " + encodedWorkspaceName + " for user " + userInfo.getUserName(), null));
		}
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
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
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);

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

		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		if (!SimpleMetaStoreUtil.deleteMetaFile(workspaceMetaFolder, projectId)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteProject: could not delete project: " + projectName + " for user " + userId, null));
		}
		if (SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId)) {
			// delete the project folder since it still exists
			if (!SimpleMetaStoreUtil.deleteMetaFolder(workspaceMetaFolder, projectId, true)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteProject: could not delete project: " + projectName + " for user " + userId, null));
			}
		}
	}

	public void deleteUser(String userId) throws CoreException {
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
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

			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
			if (!SimpleMetaStoreUtil.deleteMetaFile(userMetaFolder, USER)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteUser: could not delete user: " + userId, null));
			}
			if (!SimpleMetaStoreUtil.deleteMetaUserFolder(userMetaFolder, userId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteUser: could not delete user: " + userId, null));
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void deleteWorkspace(String userId, String workspaceId) throws CoreException {
		String workspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, workspaceName);

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
		if (!SimpleMetaStoreUtil.deleteMetaFolder(userMetaFolder, workspaceName, true)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteWorkspace: could not delete workspace: " + workspaceName, null));
		}
	}

	private ReadWriteLock getLockForUser(String userId) {
		if (!lockMap.containsKey(userId)) {
			ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
			lockMap.put(userId, lock);
		}
		return lockMap.get(userId);
	}

	/**
	 * Gets the root location for this meta store. This method should only be used by unit tests.
	 * @return The root location.
	 */
	public File getRootLocation() {
		return rootLocation;
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
		if (!SimpleMetaStoreUtil.isMetaFile(rootLocation, ROOT)) {
			// the root metastore.json file does not exist, see if migration is required
			SimpleMetaStoreMigration migration = new SimpleMetaStoreMigration();
			if (migration.isMigrationRequired(rootLocation)) {
				// Migration is required
				migration.doMigration(rootLocation);
			} else {
				// create a new root metastore.json file
				JSONObject jsonObject = new JSONObject();
				try {
					jsonObject.put(ORION_VERSION, VERSION);
				} catch (JSONException e) {
					throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not create MetaStore");
				}
				if (!SimpleMetaStoreUtil.createMetaFile(rootLocation, ROOT, jsonObject)) {
					throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not create MetaStore");
				}
				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
				logger.debug("Created new simple metadata store."); //$NON-NLS-1$
			}
		}

		// Now verify we have a valid MetaStore
		JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(rootLocation, ROOT);
		try {
			if (jsonObject == null || jsonObject.getInt(ORION_VERSION) != VERSION) {
				throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not read MetaStore");
			}
		} catch (JSONException e) {
			throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not read MetaStore.");
		}
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
		logger.debug("Loaded simple metadata store."); //$NON-NLS-1$

		this.rootLocation = rootLocation;
	}

	public List<String> readAllUsers() throws CoreException {
		return SimpleMetaStoreUtil.listMetaUserFolders(rootLocation);
	}

	public ProjectInfo readProject(String workspaceId, String projectName) throws CoreException {
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		if (userId == null || encodedWorkspaceName == null) {
			return null;
		}
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(workspaceMetaFolder, projectId);
		ProjectInfo projectInfo = new ProjectInfo();
		if (jsonObject == null) {
			if (SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId) && !SimpleMetaStoreUtil.isMetaFile(workspaceMetaFolder, projectId)) {
				// the project folder exists but the project json file does not, so create it
				File projectMetaFolder = SimpleMetaStoreUtil.readMetaFolder(workspaceMetaFolder, projectId);
				URI projectLocation = projectMetaFolder.toURI();
				projectInfo.setFullName(projectName);
				projectInfo.setWorkspaceId(workspaceId);
				projectInfo.setContentLocation(projectLocation);
				createProject(projectInfo);
				jsonObject = SimpleMetaStoreUtil.readMetaFile(workspaceMetaFolder, projectId);
			} else {
				// both the project folder and project json do not exist, no project
				// OR both the project folder and project json exist, but bad project JSON file == no project
				return null;
			}
		}
		try {
			projectInfo.setUniqueId(jsonObject.getString("UniqueId"));
			projectInfo.setWorkspaceId(jsonObject.getString("WorkspaceId"));
			projectInfo.setFullName(jsonObject.getString("FullName"));
			projectInfo.setContentLocation(new URI(jsonObject.getString("ContentLocation")));
			setProperties(projectInfo, jsonObject.getJSONObject("Properties"));
			projectInfo.flush();
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readProject: could not read project " + projectName + " for userId " + userId, e));
		} catch (URISyntaxException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readProject: could not read project " + projectName + " for userId " + userId, e));
		}
		return projectInfo;
	}

	public UserInfo readUser(String userId) throws CoreException {
		ReadWriteLock lock = getLockForUser(userId);
		lock.readLock().lock();
		try {
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
			if (SimpleMetaStoreUtil.isMetaFile(userMetaFolder, USER)) {
				JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, USER);
				if (jsonObject == null) {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					logger.info("SimpleMetaStore.readUser: could not read user " + userId); //$NON-NLS-1$
					return null;
				}
				UserInfo userInfo = new UserInfo();
				try {
					userInfo.setUniqueId(jsonObject.getString("UniqueId"));
					userInfo.setUserName(jsonObject.getString("UserName"));
					if (jsonObject.isNull("FullName")) {
						userInfo.setFullName("Unnamed User");
					} else {
						userInfo.setFullName(jsonObject.getString("FullName"));
					}
					List<String> userWorkspaceIds = new ArrayList<String>();
					JSONArray workspaceIds = jsonObject.getJSONArray("WorkspaceIds");
					if (workspaceIds.length() > 0) {
						for (int i = 0; i < workspaceIds.length(); i++) {
							userWorkspaceIds.add(workspaceIds.getString(i));
						}
					}
					userInfo.setWorkspaceIds(userWorkspaceIds);
					setProperties(userInfo, jsonObject.getJSONObject("Properties"));
					userInfo.flush();
				} catch (JSONException e) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readUser: could not read user " + userId, e));
				}
				return userInfo;
			}
		} finally {
			lock.readLock().unlock();
		}

		// user does not exist for this userId, create it ( see Bug 415505 )
		UserInfo userInfo = new UserInfo();
		userInfo.setUniqueId(userId);
		userInfo.setUserName(userId);
		userInfo.setFullName("Unnamed User");
		createUser(userInfo);
		return userInfo;
	}

	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException {
		if (workspaceId == null) {
			return null;
		}
		String userName = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		if (userName == null || encodedWorkspaceName == null) {
			// could not decode, so cannot find workspace
			return null;
		}
		if (!SimpleMetaStoreUtil.isMetaUserFolder(rootLocation, userName)) {
			return null;
		}
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userName);
		if (!SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName)) {
			return null;
		}
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(workspaceMetaFolder, WORKSPACE);
		if (jsonObject == null) {
			return null;
		}
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		try {
			workspaceInfo.setUniqueId(jsonObject.getString("UniqueId"));
			workspaceInfo.setUserId(jsonObject.getString("UserId"));
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
			workspaceInfo.flush();
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readWorkspace: could not read workspace " + encodedWorkspaceName + " for user id " + userName, e));
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
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(projectInfo.getWorkspaceId());
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		if (!SimpleMetaStoreUtil.isMetaFile(workspaceMetaFolder, projectInfo.getUniqueId())) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not update project: " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
		}
		JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(workspaceMetaFolder, projectInfo.getUniqueId());
		if (!projectInfo.getUniqueId().equals(SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectInfo.getFullName()))) {
			// full name has changed, this is a project move
			String newProjectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectInfo.getFullName());
			if (!SimpleMetaStoreUtil.moveMetaFile(workspaceMetaFolder, projectInfo.getUniqueId(), newProjectId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not move project: " + projectInfo.getUniqueId() + " to " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
			}
			if (!SimpleMetaStoreUtil.moveMetaFolder(workspaceMetaFolder, projectInfo.getUniqueId(), newProjectId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not move project: " + projectInfo.getUniqueId() + " to " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
			}
			// if the content location is local, update the content location with the new name
			if (projectInfo.getContentLocation().getScheme().equals("file")) {
				File oldContentLocation = new File(projectInfo.getContentLocation());
				if (workspaceMetaFolder.toString().equals(oldContentLocation.getParent())) {
					projectInfo.setContentLocation(new File(workspaceMetaFolder, newProjectId).toURI());
				}
			}
			// Update the workspace with the new projectName
			WorkspaceInfo workspaceInfo;
			try {
				workspaceInfo = readWorkspace(projectInfo.getWorkspaceId());
			} catch (CoreException exception) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not find workspace with id:" + projectInfo.getWorkspaceId() + ".", null));
			}
			List<String> newProjectNames = new ArrayList<String>();
			newProjectNames.addAll(workspaceInfo.getProjectNames());
			String oldProjectName = SimpleMetaStoreUtil.decodeProjectNameFromProjectId(projectInfo.getUniqueId());
			newProjectNames.remove(oldProjectName);
			newProjectNames.add(projectInfo.getFullName());
			workspaceInfo.setProjectNames(newProjectNames);
			updateWorkspace(workspaceInfo);
			// update unique id with the new name
			projectInfo.setUniqueId(newProjectId);
		} else if (projectInfo.getProperty("newUserId") != null) {
			// Found the temporary properties to indicate a userId change
			String newUserId = projectInfo.getProperty("newUserId");
			String newWorkspaceId = projectInfo.getProperty("newWorkspaceId");
			projectInfo.setWorkspaceId(newWorkspaceId);

			// if the content location is local, update the content location with the new name
			if (projectInfo.getContentLocation().getScheme().equals("file")) {
				try {
					File oldContentLocation = new File(projectInfo.getContentLocation());
					if (workspaceMetaFolder.getCanonicalPath().equals(oldContentLocation.getParent())) {
						String newEncodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(newWorkspaceId);
						File newUserMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, newUserId);
						File newWorkspaceMetaFolder = new File(newUserMetaFolder, newEncodedWorkspaceName);
						projectInfo.setContentLocation(new File(newWorkspaceMetaFolder, projectInfo.getUniqueId()).toURI());
					}
				} catch (IOException e) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not update project: " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
				}
			}

			// remove the temporary properties
			projectInfo.setProperty("newUserId", null);
			projectInfo.setProperty("newWorkspaceId", null);
		}
		try {
			jsonObject.put(ORION_VERSION, VERSION);
			jsonObject.put("UniqueId", projectInfo.getUniqueId());
			jsonObject.put("WorkspaceId", projectInfo.getWorkspaceId());
			jsonObject.put("FullName", projectInfo.getFullName());
			jsonObject.put("ContentLocation", projectInfo.getContentLocation());
			JSONObject properties = updateProperties(jsonObject, projectInfo);
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not update project: " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
		}
		if (!SimpleMetaStoreUtil.updateMetaFile(workspaceMetaFolder, projectInfo.getUniqueId(), jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not update project: " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
		}
	}

	/**
	 * Update the properties that have been updated in the provided metadataInfo. The updates are determined by 
	 * looking at the the list of operations performed on the properties of this metadataInfo since the last read.
	 * See Bug 426842 for details.
	 * @param jsonObject The jsonObject to be updated with the properties changes.
	 * @param metadataInfo The updated properties JSON metadata.
	 * @return The updated JSONObject.
	 * @throws CoreException
	 */
	private JSONObject updateProperties(JSONObject jsonObject, MetadataInfo metadataInfo) throws CoreException {
		JSONObject properties = new JSONObject();
		try {
			if (jsonObject.has("Properties")) {
				properties = jsonObject.getJSONObject("Properties");
			}
			for (String key : metadataInfo.getOperations().keySet()) {
				MetadataInfo.OperationType operation = metadataInfo.getOperations().get(key);
				if (MetadataInfo.OperationType.DELETE.equals(operation)) {
					// delete the property
					if (properties.has(key)) {
						properties.remove(key);
					}
				} else {
					// create or update the property
					String value = metadataInfo.getProperties().get(key);
					if ("UserRights".equals(key)) {
						// UserRights needs to be handled specifically since it is a JSONArray and not a string.
						JSONArray userRights = new JSONArray(value);
						properties.put("UserRights", userRights);
					} else if ("SiteConfigurations".equals(key)) {
						// UserRights needs to be handled specifically since it is a JSONObject and not a string.
						JSONObject siteConfigurations = new JSONObject(value);
						properties.put("SiteConfigurations", siteConfigurations);
					} else {
						properties.put(key, value);
					}
				}
			}
			metadataInfo.flush();
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProperties: could not update", e));
		}
		return properties;
	}

	public void updateUser(UserInfo userInfo) throws CoreException {
		if (userInfo.getUserName() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: user name is null.", null));
		}
		if (!userInfo.getUniqueId().equals(userInfo.getUserName())) {
			// user id does not equal user name, so we are renaming the user.
			String oldUserId = userInfo.getUniqueId();
			String newUserId = userInfo.getUserName();

			// lock both the old and new userId
			ReadWriteLock oldUserLock = getLockForUser(oldUserId);
			oldUserLock.writeLock().lock();
			ReadWriteLock newUserLock = getLockForUser(newUserId);
			newUserLock.writeLock().lock();
			try {

				// update the workspace JSON with the new userId
				List<String> oldWorkspaceIds = userInfo.getWorkspaceIds();
				List<String> newWorkspaceIds = new ArrayList<String>();
				for (String oldWorkspaceId : oldWorkspaceIds) {
					WorkspaceInfo workspaceInfo = readWorkspace(oldWorkspaceId);
					workspaceInfo.setUserId(newUserId);
					updateWorkspace(workspaceInfo);
					String newWorkspaceId = workspaceInfo.getUniqueId();
					newWorkspaceIds.add(newWorkspaceId);

					// next update each of the project JSON with the new userId and location
					List<String> projectNames = workspaceInfo.getProjectNames();
					for (String projectName : projectNames) {
						ProjectInfo projectInfo = readProject(oldWorkspaceId, projectName);
						// Set temporary properties to indicate a userId change
						projectInfo.setProperty("newUserId", newUserId);
						projectInfo.setProperty("newWorkspaceId", newWorkspaceId);
						updateProject(projectInfo);
					}
				}
				// update the UserRights in the properties
				try {
					JSONObject properties = getUserProperties(userInfo);
					if (properties.has("UserRights")) {
						JSONArray userRights = properties.getJSONArray("UserRights");
						for (int i = 0; i < userRights.length(); i++) {
							JSONObject userRight = userRights.getJSONObject(i);
							String uri = userRight.getString("Uri");
							if (uri.contains(oldUserId)) {
								uri = uri.replace(oldUserId, newUserId);
								userRight.put("Uri", uri);
							}
						}
						properties.put("UserRights", userRights);
					}
					setProperties(userInfo, properties);
				} catch (JSONException e) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: user name " + userInfo.getUserName() + " already exists, cannot rename from " + userInfo.getUniqueId(), e));
				}
				userInfo.setWorkspaceIds(newWorkspaceIds);

				// move the user folder to the new location 
				if (SimpleMetaStoreUtil.isMetaUserFolder(rootLocation, newUserId)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: user name " + userInfo.getUserName() + " already exists, cannot rename from " + userInfo.getUniqueId(), null));
				}
				if (!SimpleMetaStoreUtil.isMetaUserFolder(rootLocation, oldUserId)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: user name " + userInfo.getUniqueId() + " does not exist, cannot rename to " + userInfo.getUserName(), null));
				}
				File oldUserMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, oldUserId);
				File newUserMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, newUserId);
				SimpleMetaStoreUtil.moveUserMetaFolder(oldUserMetaFolder, newUserMetaFolder);

				userInfo.setUniqueId(newUserId);
				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
				logger.debug("Moved MetaStore for user " + oldUserId + " to user " + newUserId + "."); //$NON-NLS-1$
			} finally {
				oldUserLock.writeLock().unlock();
				newUserLock.writeLock().unlock();
			}
		}
		String userId = userInfo.getUserName();
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userId);
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, USER);
			if (jsonObject == null) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not find user " + userId, null));
			}
			try {
				jsonObject.put("UniqueId", userInfo.getUniqueId());
				jsonObject.put("UserName", userInfo.getUserName());
				jsonObject.put("FullName", userInfo.getFullName());
				JSONArray workspaceIds = new JSONArray(userInfo.getWorkspaceIds());
				jsonObject.put("WorkspaceIds", workspaceIds);
				JSONObject properties = updateProperties(jsonObject, userInfo);
				jsonObject.put("Properties", properties);
			} catch (JSONException e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not update user: " + userId, e));
			}
			if (!SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, USER, jsonObject)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not update user: " + userId, null));
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void updateWorkspace(WorkspaceInfo workspaceInfo) throws CoreException {
		String userName = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceInfo.getUniqueId());
		String workspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceInfo.getUniqueId());
		boolean renameUser = false;
		String newWorkspaceId = null;
		if (!workspaceInfo.getUserId().equals(userName)) {
			// user id does not equal user name, so we are renaming the user.
			renameUser = true;
			newWorkspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(workspaceInfo.getUserId(), workspaceName);
		}
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(ORION_VERSION, VERSION);
			if (renameUser) {
				jsonObject.put("UniqueId", newWorkspaceId);
			} else {
				jsonObject.put("UniqueId", workspaceInfo.getUniqueId());
			}
			jsonObject.put("UserId", workspaceInfo.getUserId());
			jsonObject.put("FullName", workspaceInfo.getFullName());
			JSONArray projectNames = new JSONArray(workspaceInfo.getProjectNames());
			jsonObject.put("ProjectNames", projectNames);
			JSONObject properties = updateProperties(jsonObject, workspaceInfo);
			jsonObject.put("Properties", properties);
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateWorkspace: could not update workspace: " + workspaceName + " for user " + userName, e));
		}
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, userName);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, workspaceName);
		if (!SimpleMetaStoreUtil.updateMetaFile(workspaceMetaFolder, WORKSPACE, jsonObject)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateWorkspace: could not update workspace: " + workspaceName + " for user " + userName, null));
		}
		if (renameUser) {
			workspaceInfo.setUniqueId(newWorkspaceId);
		}
	}
}
