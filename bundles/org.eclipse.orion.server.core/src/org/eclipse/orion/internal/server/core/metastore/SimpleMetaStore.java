/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.MetadataInfo;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple implementation of a {@code IMetaStore}.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStore implements IMetaStore {

	/**
	 * The default name of a workspace for a user: Orion Content.
	 */
	public final static String DEFAULT_WORKSPACE_NAME = "Orion Content";

	/**
	 * The name of the Orion Version property in the JSON file.
	 */
	public final static String DESCRIPTION = "This JSON file is at the root of the Orion metadata store responsible for persisting user, workspace and project files and metadata.";

	/**
	 * The name of the Orion Description property in the JSON file.
	 */
	public final static String ORION_DESCRIPTION = "OrionDescription";

	/**
	 * The name of the Orion Version property in the JSON file.
	 */
	public final static String ORION_VERSION = "OrionVersion";

	/**
	 * Each metadata file is in JSON format and should have a version. A missing version is flagged by this value.
	 */
	public final static int ORION_VERSION_MISSING = -1;

	/**
	 * The root of the Simple Meta Store has the root metastore.json metadata file.
	 */
	public static final String ROOT = "metastore";

	/**
	 * The root of the user folder has the user.json metadata file.
	 */
	public final static String USER = "user";

	/**
	 * The current version of the Simple Meta Store.
	 */
	public final static int VERSION = 8;

	/**
	 * The root of the workspace folder has the workspace.json metadata file. (only for OrionVersion 4).
	 */
	public final static String WORKSPACE = "workspace";

	/**
	 * A map of read write locks keyed by userId.
	 */
	private final Map<String, ReadWriteLock> lockMap = Collections.synchronizedMap(new HashMap<String, ReadWriteLock>());

	/**
	 * The root location of this Simple Meta Store.
	 */
	private File rootLocation = null;

	/**
	 * 
	 */
	private SimpleMetaStoreUserPropertyCache userPropertyCache = new SimpleMetaStoreUserPropertyCache();

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config");

	/**
	 * Create an instance of a SimpleMetaStore under the provided folder.
	 * 
	 * @param rootLocation
	 *            The root location for storing content and metadata on this server.
	 * @throws CoreException
	 */
	public SimpleMetaStore(File rootLocation) throws CoreException {
		super();
		this.rootLocation = rootLocation;
		initializeMetaStore(rootLocation);
	}

	@Override
	public void createProject(ProjectInfo projectInfo) throws CoreException {
		if (projectInfo.getWorkspaceId() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: workspace id is null.", null));
		}
		if (projectInfo.getFullName() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createProject: project name is null.", null));
		}
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(projectInfo.getWorkspaceId());
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
			WorkspaceInfo workspaceInfo = readWorkspace(projectInfo.getWorkspaceId());
			if (workspaceInfo == null) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.createProject: could not find workspace with id:" + projectInfo.getWorkspaceId() + ", workspace does not exist.", null));
			}
			String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(projectInfo.getWorkspaceId());
			String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectInfo.getFullName());
			projectInfo.setUniqueId(projectId);

			JSONObject jsonObject = new JSONObject();
			try {
				jsonObject.put(SimpleMetaStore.ORION_VERSION, VERSION);
				jsonObject.put(MetadataInfo.UNIQUE_ID, projectInfo.getUniqueId());
				jsonObject.put("WorkspaceId", projectInfo.getWorkspaceId());
				jsonObject.put(UserConstants.FULL_NAME, projectInfo.getFullName());
				if (projectInfo.getContentLocation() != null) {
					URI contentLocation = projectInfo.getContentLocation();
					String encodedContentLocation = SimpleMetaStoreUtil.encodeProjectContentLocation(contentLocation.toString());
					jsonObject.put("ContentLocation", encodedContentLocation);
				}
				JSONObject properties = updateProperties(jsonObject, projectInfo);
				jsonObject.put("Properties", properties);
			} catch (JSONException e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + " for user " + userId, e));
			}
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
			if (!SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId)) {
				// try to create the project folder if the folder is not linked
				if ((projectInfo.getContentLocation() == null || projectInfo.getProjectStore().equals(getDefaultContentLocation(projectInfo)))
						&& !SimpleMetaStoreUtil.createMetaFolder(workspaceMetaFolder, projectId)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + " for user " + userId, null));
				}
			}
			String metafileId = projectId;
			if (!DEFAULT_WORKSPACE_NAME.equals(workspaceInfo.getFullName())) {
				metafileId = workspaceInfo.getUniqueId() + SimpleMetaStoreUtil.SEPARATOR + projectId;
			}
			if (!SimpleMetaStoreUtil.createMetaFile(userMetaFolder, metafileId, jsonObject)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.createProject: could not create project: " + projectInfo.getFullName() + " for user " + userId, null));
			}

			// Update the workspace with the new projectName
			List<String> newProjectNames = new ArrayList<String>();
			newProjectNames.addAll(workspaceInfo.getProjectNames());
			newProjectNames.add(projectInfo.getFullName());
			workspaceInfo.setProjectNames(newProjectNames);
			updateWorkspace(workspaceInfo);
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
					"SimpleMetaStore.createProject: could not find workspace with id:" + projectInfo.getWorkspaceId() + ", workspace does not exist.", null));
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void createUser(UserInfo userInfo) throws CoreException {
		if (userInfo.getUserName() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
					"SimpleMetaStore.createUser: could not create user, did not provide a userName", null));
		}
		String userId = userInfo.getUserName();
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
			userInfo.setProperty(UserConstants.CREATION_TIMESTAMP, new Long(System.currentTimeMillis()).toString());
			userInfo.setUniqueId(userId);
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			if (SimpleMetaStoreUtil.isMetaFolder(getRootLocation(), userId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.createUser: could not create user: " + userId + ", user already exists", null));
			}
			JSONObject jsonObject = new JSONObject();
			try {
				jsonObject.put(SimpleMetaStore.ORION_VERSION, VERSION);
				jsonObject.put(MetadataInfo.UNIQUE_ID, userId);
				jsonObject.put(UserConstants.USER_NAME, userId);
				jsonObject.put(UserConstants.FULL_NAME, userInfo.getFullName());
				jsonObject.put("WorkspaceIds", new JSONArray());
				JSONObject properties = updateProperties(jsonObject, userInfo);
				jsonObject.put("Properties", properties);
			} catch (JSONException e) {
				throw new CoreException(
						new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId, e));
			}
			if (!SimpleMetaStoreUtil.createMetaUserFolder(getRootLocation(), userId)) {
				throw new CoreException(
						new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId, null));
			}
			if (SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER)) {
				// If the file already exists then update with the new contents
				if (!SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, SimpleMetaStore.USER, jsonObject)) {
					throw new CoreException(
							new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId, null));
				}
			} else {
				if (!SimpleMetaStoreUtil.createMetaFile(userMetaFolder, SimpleMetaStore.USER, jsonObject)) {
					throw new CoreException(
							new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createUser: could not create user: " + userId, null));
				}
			}
			// update the UniqueId cache
			if (userPropertyCache.isRegistered(UserConstants.USER_NAME)) {
				userPropertyCache.add(UserConstants.USER_NAME, userId, userId);
			}
			logger.debug("Created new user " + userId + "."); //$NON-NLS-1$
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void createWorkspace(WorkspaceInfo workspaceInfo) throws CoreException {
		if (workspaceInfo.getUserId() == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: user id is null.", null));
		}
		if (workspaceInfo.getFullName() == null) {
			throw new CoreException(
					new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.createWorkspace: workspace name is null.", null));
		}
		UserInfo userInfo;
		try {
			userInfo = readUser(workspaceInfo.getUserId());
		} catch (CoreException exception) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
					"SimpleMetaStore.createWorkspace: could not find user with id: " + workspaceInfo.getUserId() + ", user does not exist.", null));
		}
		if (userInfo == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
					"SimpleMetaStore.createWorkspace: could not find user with id: " + workspaceInfo.getUserId() + ", user does not exist.", null));
		}
		// lock the user when creating the workspace and updating the user.
		ReadWriteLock lock = getLockForUser(userInfo.getUniqueId());
		lock.writeLock().lock();
		try {
			// We create a meta folder for the workspace using the encoded workspace name
			String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(workspaceInfo.getUserId(), workspaceInfo.getUniqueId() != null ?  workspaceInfo.getUniqueId() : workspaceInfo.getFullName());
			String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
			// It is possible to have two workspaces with the same name, so append an integer if this is a duplicate
			// name.
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userInfo.getUniqueId());
			workspaceInfo.setUniqueId(workspaceId);
			JSONObject jsonObject = new JSONObject();
			try {
				jsonObject.put(SimpleMetaStore.ORION_VERSION, VERSION);
				jsonObject.put(MetadataInfo.UNIQUE_ID, workspaceInfo.getUniqueId());
				jsonObject.put("UserId", workspaceInfo.getUserId());
				jsonObject.put(UserConstants.FULL_NAME, workspaceInfo.getFullName());
				jsonObject.put("ProjectNames", new JSONArray());
				JSONObject properties = updateProperties(jsonObject, workspaceInfo);
				jsonObject.put("Properties", properties);
			} catch (JSONException e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.createWorkspace: could not create workspace: " + encodedWorkspaceName + " for user " + userInfo.getUserName(), e));
			}
			if (!SimpleMetaStoreUtil.createMetaFolder(userMetaFolder, encodedWorkspaceName)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.createWorkspace: could not create workspace: " + encodedWorkspaceName + " for user " + userInfo.getUserName(), null));
			}
			if (SimpleMetaStoreUtil.isMetaFile(userMetaFolder, workspaceId)) {
				File workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, workspaceId);
				logger.info("SimpleMetaStore.createWorkspace: workspace conflict: while creating a workspace for user id: " + userInfo.getUniqueId()
						+ ", found existing workspace json, will not overwrite: " + workspaceMetaFile.toString());
			} else {
				if (!SimpleMetaStoreUtil.createMetaFile(userMetaFolder, workspaceId, jsonObject)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.createWorkspace: could not create workspace: " + encodedWorkspaceName + " for user " + userInfo.getUserName(),
							null));
				}
			}

			// Update the user with the new workspaceId
			List<String> newWorkspaceIds = new ArrayList<String>();
			newWorkspaceIds.addAll(userInfo.getWorkspaceIds());
			newWorkspaceIds.add(workspaceId);
			userInfo.setWorkspaceIds(newWorkspaceIds);
			updateUser(userInfo);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void deleteProject(String workspaceId, String projectName) throws CoreException {
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
			String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);

			// Update the workspace, remove the deleted projectName
			WorkspaceInfo workspaceInfo;
			try {
				workspaceInfo = readWorkspace(workspaceId);
			} catch (CoreException exception) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.deleteProject: could not find project with name:" + projectName + ", workspace does not exist.", null));
			}
			List<String> newProjectIds = new ArrayList<String>();
			newProjectIds.addAll(workspaceInfo.getProjectNames());
			newProjectIds.remove(projectName);
			workspaceInfo.setProjectNames(newProjectIds);
			updateWorkspace(workspaceInfo);

			String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
			String metafileId = projectId;
			if (!SimpleMetaStoreUtil.encodeWorkspaceId(userId, DEFAULT_WORKSPACE_NAME).equals(workspaceId)) {
				metafileId = workspaceId + SimpleMetaStoreUtil.SEPARATOR + metafileId;
			}
			if (!SimpleMetaStoreUtil.deleteMetaFile(userMetaFolder, metafileId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.deleteProject: could not delete project: " + projectName + " for user " + userId, null));
			}
			if (SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId)) {
				// delete the project folder since it still exists
				if (!SimpleMetaStoreUtil.deleteMetaFolder(workspaceMetaFolder, projectId, true)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.deleteProject: could not delete project: " + projectName + " for user " + userId, null));
				}
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void deleteUser(String userId) throws CoreException {
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
			UserInfo userInfo;
			try {
				userInfo = readUser(userId);
			} catch (CoreException exception) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.deleteUser: could not delete user with id: " + userId + ", user does not exist.", null));
			}

			// First delete the workspaces
			for (String workspaceId : userInfo.getWorkspaceIds()) {
				deleteWorkspace(userId, workspaceId);
			}

			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			if (!SimpleMetaStoreUtil.deleteMetaFile(userMetaFolder, SimpleMetaStore.USER)) {
				throw new CoreException(
						new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteUser: could not delete user: " + userId, null));
			}
			if (!SimpleMetaStoreUtil.deleteMetaUserFolder(userMetaFolder, userId)) {
				throw new CoreException(
						new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.deleteUser: could not delete user: " + userId, null));
			}
			// update the userid cache
			userPropertyCache.deleteUser(userId);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void deleteWorkspace(String userId, String workspaceId) throws CoreException {
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
			String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);

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
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.deleteWorkspace: could not find user with id:" + userId + ", user does not exist.", null));
			}
			List<String> newWorkspaceIds = new ArrayList<String>();
			newWorkspaceIds.addAll(userInfo.getWorkspaceIds());
			newWorkspaceIds.remove(workspaceId);
			userInfo.setWorkspaceIds(newWorkspaceIds);
			updateUser(userInfo);

			// delete the meta file and folder
			if (!SimpleMetaStoreUtil.deleteMetaFile(userMetaFolder, workspaceId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.deleteWorkspace: could not delete workspace: " + encodedWorkspaceName, null));
			}
			if (SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName)) {
				if (!SimpleMetaStoreUtil.deleteMetaFolder(userMetaFolder, encodedWorkspaceName, true)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.deleteWorkspace: could not delete workspace: " + encodedWorkspaceName, null));
				}
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public IFileStore getDefaultContentLocation(ProjectInfo projectInfo) throws CoreException {
		if (projectInfo.getWorkspaceId() == null) {
			throw new CoreException(
					new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.getDefaultContentLocation: workspace id is null.", null));
		}
		if (projectInfo.getFullName() == null) {
			throw new CoreException(
					new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.getDefaultContentLocation: project name is null.", null));
		}
		IFileStore workspaceFolder = getWorkspaceContentLocation(projectInfo.getWorkspaceId());
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectInfo.getFullName());
		IFileStore projectFolder = workspaceFolder.getChild(projectId);
		return projectFolder;
	}

	private ReadWriteLock getLockForUser(String userId) {
		synchronized (lockMap) {
			if (!lockMap.containsKey(userId)) {
				ReadWriteLock lock = new ReentrantReadWriteLock();
				lockMap.put(userId, lock);
				return lock;
			}
			return lockMap.get(userId);
		}
	}

	/**
	 * Get the folder at the root of the Orion metadata filesystem.
	 * 
	 * @return the root location.
	 */
	protected File getRootLocation() {
		return rootLocation;
	}

	@Override
	public IFileStore getUserHome(String userId) {
		IFileStore root = OrionConfiguration.getRootLocation();
		if (userId != null) {
			// the format of the user home is /serverworkspace/an/anthony
			String userPrefix = userId.substring(0, Math.min(2, userId.length()));
			return root.getChild(userPrefix).getChild(userId);
		}
		// for backwards compatibility, if userId is null, the old API used to return the root location;
		return root;
	}

	/**
	 * Gets the properties in the provided MetaDataInfo.
	 * 
	 * @param userInfo
	 *            The MetaData info
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
			} else if (UserConstants.PASSWORD.equals(key)) {
				// password needs to be handled specifically since it needs to be decrypted.
				String password = SimpleUserPasswordUtil.encryptPassword(value);
				jsonObject.put(key, password);
			} else {
				jsonObject.put(key, value);
			}
		}
		return jsonObject;
	}

	@Override
	public IFileStore getWorkspaceContentLocation(String workspaceId) throws CoreException {
		if (workspaceId == null) {
			throw new CoreException(
					new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.getWorkspaceContentLocation: workspace id is null.", null));
		}
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		IFileStore userHome = getUserHome(userId);
		IFileStore workspaceFolder = userHome.getChild(workspaceMetaFolder.getName());
		return workspaceFolder;
	}

	/**
	 * Initialize the simple meta store.
	 * 
	 * @param rootLocation
	 *            The root location, a folder on the server.
	 * @throws CoreException
	 */
	protected void initializeMetaStore(File rootLocation) throws CoreException {
		if (!SimpleMetaStoreUtil.isMetaFile(rootLocation, SimpleMetaStore.ROOT)) {
			// the root metastore.json file does not exist, create a new root metastore.json file
			JSONObject jsonObject = new JSONObject();
			try {
				jsonObject.put(SimpleMetaStore.ORION_VERSION, VERSION);
				jsonObject.put(SimpleMetaStore.ORION_DESCRIPTION, DESCRIPTION);
			} catch (JSONException e) {
				logger.error("SimpleMetaStore.initializeMetaStore: JSON error.", e);
				throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not create new metastore.json");
			}
			if (!SimpleMetaStoreUtil.createMetaFile(rootLocation, SimpleMetaStore.ROOT, jsonObject)) {
				throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not create MetaStore");
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Created new simple metadata store (version " + VERSION + ")."); //$NON-NLS-1$
			}
		} else {
			// Verify we have a valid MetaStore with the right version
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(rootLocation, SimpleMetaStore.ROOT);
			try {
				int version = jsonObject.getInt(SimpleMetaStore.ORION_VERSION);
				if (version < VERSION) {
					// the root metastore.json file is an older version, update the root metastore.json file
					jsonObject.put(SimpleMetaStore.ORION_VERSION, VERSION);
					jsonObject.put(SimpleMetaStore.ORION_DESCRIPTION, DESCRIPTION);
					if (!SimpleMetaStoreUtil.updateMetaFile(rootLocation, SimpleMetaStore.ROOT, jsonObject)) {
						throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not update MetaStore");
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Updated simple metadata store to a new version (version " + VERSION + ")."); //$NON-NLS-1$
					}
				} else if (version > VERSION) {
					// we are running an old server on metadata that is at a newer version
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.initializeMetaStore: cannot run an old server (version " + SimpleMetaStore.VERSION
									+ ") on metadata that is at a newer version (version " + version + ")",
							null));
				}
			} catch (JSONException e) {
				logger.error("SimpleMetaStore.initializeMetaStore: JSON error.", e);
				throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not read or update metastore.json");
			}
		}

		logger.info("Loaded simple metadata store (version " + VERSION + ")."); //$NON-NLS-1$
	}

	@Override
	public List<String> readAllUsers() throws CoreException {
		long start = System.currentTimeMillis();
		List<String> userIds = SimpleMetaStoreUtil.listMetaUserFolders(getRootLocation());
		logger.info("Finished reading " + userIds.size() + " users, duration: " + (System.currentTimeMillis() - start) + "ms");
		return userIds;
	}

	@Override
	public ProjectInfo readProject(String workspaceId, String projectName) throws CoreException {
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		if (userId == null) {
			if (logger.isDebugEnabled()) {
				logger.debug("SimpleMetaStore.readProject: requested with a bad userId in the workspaceId " + workspaceId); //$NON-NLS-1$
			}
			return null;
		}
		ReadWriteLock lock = getLockForUser(userId);
		lock.readLock().lock();
		try {
			String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
			if (encodedWorkspaceName == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("SimpleMetaStore.readProject: requested with a bad workspaceId " + workspaceId); //$NON-NLS-1$
				}
				return null;
			}
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
			if (workspaceMetaFolder == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("SimpleMetaStore.readProject: workspaceMetaFolder does not exist for workspace " + workspaceId); //$NON-NLS-1$
				}
				return null;
			}
			String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
			String metafileId = projectId;
			if (!SimpleMetaStoreUtil.encodeWorkspaceId(userId, DEFAULT_WORKSPACE_NAME).equals(workspaceId)) {
				metafileId = workspaceId + SimpleMetaStoreUtil.SEPARATOR + metafileId;
			}
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, metafileId);
			ProjectInfo projectInfo = new ProjectInfo();
			if (jsonObject == null) {
				if (SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId) && !SimpleMetaStoreUtil.isMetaFile(userMetaFolder, metafileId)) {
					// the project folder exists but the project json file does not, so create it
					File projectMetaFolder = SimpleMetaStoreUtil.readMetaFolder(workspaceMetaFolder, projectId);
					if (logger.isDebugEnabled()) {
						logger.info("SimpleMetaStore.readProject: the project folder " + projectMetaFolder.toString() //$NON-NLS-1$
								+ " exists but the project json file does not, so creating it in " + workspaceId);
					}
					URI projectLocation = projectMetaFolder.toURI();
					projectInfo.setFullName(projectName);
					projectInfo.setWorkspaceId(workspaceId);
					projectInfo.setContentLocation(projectLocation);
					lock.readLock().unlock();
					createProject(projectInfo);
					lock.readLock().lock();
					jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, metafileId);
				} else {
					// both the project folder and project json do not exist, no project
					// OR both the project folder and project json exist, but bad project JSON file == no project
					return null;
				}
			}
			try {
				projectInfo.setUniqueId(jsonObject.getString(MetadataInfo.UNIQUE_ID));
				projectInfo.setWorkspaceId(jsonObject.getString("WorkspaceId"));
				projectInfo.setFullName(jsonObject.getString(UserConstants.FULL_NAME));
				if (jsonObject.has("ContentLocation")) {
					String decodedContentLocation = SimpleMetaStoreUtil.decodeProjectContentLocation(jsonObject.getString("ContentLocation"));
					projectInfo.setContentLocation(new URI(decodedContentLocation));
				}
				setProperties(projectInfo, jsonObject.getJSONObject("Properties"));
				projectInfo.flush();
			} catch (JSONException e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.readProject: could not read project " + projectName + " for userId " + userId, e));
			} catch (URISyntaxException e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.readProject: could not read project " + projectName + " for userId " + userId, e));
			}
			return projectInfo;
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public UserInfo readUser(String userId) throws CoreException {
		if (userId == null) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readUser: user is null", null));
		}
		ReadWriteLock lock = getLockForUser(userId);
		lock.readLock().lock();
		try {
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			if (SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER)) {
				JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
				if (jsonObject == null) {
					logger.info("SimpleMetaStore.readUser: could not read user " + userId); //$NON-NLS-1$
					return null;
				}
				UserInfo userInfo = new UserInfo();
				try {
					SimpleMetaStoreMigration migration = new SimpleMetaStoreMigration();
					if (migration.isMigrationRequired(jsonObject)) {
						logger.info("Migration: Migration required for user " + userId + " to the latest (version " + SimpleMetaStore.VERSION + ")");
						// Migration to the latest version is required for this user
						lock.readLock().unlock();
						lock.writeLock().lock();
						try {
							// Bug 451012: since we now have locked for write, check again if we need to migrate
							jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
							if (migration.isMigrationRequired(jsonObject)) {
								migration.doMigration(getRootLocation(), userMetaFolder);
							} else {
								logger.info("Migration: Migration no longer required for user " + userId + ", completed in other thread");
							}
						} finally {
							lock.writeLock().unlock();
						}
						lock.readLock().lock();
						jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
					}
					userInfo.setUniqueId(jsonObject.getString(MetadataInfo.UNIQUE_ID));
					userInfo.setUserName(jsonObject.getString(UserConstants.USER_NAME));
					if (jsonObject.isNull(UserConstants.FULL_NAME)) {
						userInfo.setFullName("Unnamed User");
					} else {
						userInfo.setFullName(jsonObject.getString(UserConstants.FULL_NAME));
					}
					List<String> userWorkspaceIds = new ArrayList<String>();
					JSONArray workspaceIds = jsonObject.getJSONArray("WorkspaceIds");
					if (workspaceIds.length() > 0) {
						for (int i = 0; i < workspaceIds.length(); i++) {
							userWorkspaceIds.add(workspaceIds.getString(i));
						}
					}
					userInfo.setWorkspaceIds(userWorkspaceIds);
					if (userInfo.getWorkspaceIds().size() > 1) {
						// It is currently unexpected that a user has more than one workspace. See Bug 439735
						logger.warn("SimpleMetaStore.readUser: user id " + userInfo.getUniqueId() + " has a multiple workspace conflict: workspace: "
								+ userInfo.getWorkspaceIds().get(0) + " and workspace: " + userInfo.getWorkspaceIds().get(1));
					}
					setProperties(userInfo, jsonObject.getJSONObject("Properties"));
					userInfo.flush();
				} catch (JSONException e) {
					throw new CoreException(
							new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.readUser: could not read user " + userId, e));
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

	@Override
	public UserInfo readUserByProperty(String key, String value, boolean regExp, boolean ignoreCase) throws CoreException {
		String userId = null;
		if (UserConstants.USER_NAME.equals(key)) {
			if (SimpleMetaStoreUtil.isMetaUserFolder(getRootLocation(), value)) {
				userId = value;
			}
		} else {
			userId = userPropertyCache.readUserByProperty(key, value, regExp, ignoreCase);
		}
		if (userId != null) {
			return readUser(userId);
		}
		return null;
	}

	@Override
	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException {
		if (workspaceId == null) {
			return null;
		}
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		if (userId == null || encodedWorkspaceName == null) {
			// could not decode, so cannot find workspace
			return null;
		}
		if (!SimpleMetaStoreUtil.isMetaUserFolder(getRootLocation(), userId)) {
			return null;
		}
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
		JSONObject jsonObject = null;
		// add a read lock when reading the the.
		ReadWriteLock lock = getLockForUser(userId);
		lock.readLock().lock();
		try {
			jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, workspaceId);
			if (jsonObject == null) {
				return null;
			}
			WorkspaceInfo workspaceInfo = new WorkspaceInfo();
			try {
				workspaceInfo.setUniqueId(jsonObject.getString(MetadataInfo.UNIQUE_ID));
				workspaceInfo.setUserId(jsonObject.getString("UserId"));
				workspaceInfo.setFullName(jsonObject.getString(UserConstants.FULL_NAME));
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
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.readWorkspace: could not read workspace " + encodedWorkspaceName + " for user id " + userId, e));
			}
			return workspaceInfo;
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void registerUserProperties(List<String> keys) throws CoreException {
		// register the user properties with the user cache
		userPropertyCache.register(keys);
		if (!(keys.contains(UserConstants.USER_NAME) && keys.size() == 1)) {
			// initialize the user property cache with values from disk
			long start = System.currentTimeMillis();
			int usersCount = initializeAllRegisteredPropertiesFromDisk();
			logger.info("Finished processing " + usersCount + " users for registerUserProperties, duration: " + (System.currentTimeMillis() - start) + "ms");
		}
	}

	/**
	 * Initialize the user properties cache for properties other than user name.
	 */
	private int initializeAllRegisteredPropertiesFromDisk() {
		List<String> allUsers = SimpleMetaStoreUtil.listMetaUserFolders(rootLocation);
		for (String user : allUsers) {
			File userMetaFile = SimpleMetaStoreUtil.readMetaUserFolder(rootLocation, user);
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFile, SimpleMetaStoreUtil.USER);
			JSONObject properties = null;
			try {
				properties = jsonObject.getJSONObject("Properties");
			} catch (JSONException e) {
				logger.error("SimpleMetaStore.initializeAllRegisteredPropertiesFromDisk: failed reading metafile for user " + user, e);
			}

			UserInfo info = new UserInfo();
			info.setUniqueId(user);
			// call setProperties for its side effect of writing registered properties to the cache.
			try {
				setProperties(info, properties);
			} catch (JSONException e) {
				logger.error("SimpleMetaStore.initializeAllRegisteredPropertiesFromDisk: failed reading properties for user " + user, e);
			}
		}
		return allUsers.size();
	}

	/**
	 * Sets all the properties in the provided JSON into the MetaDataInfo.
	 * 
	 * @param metadataInfo
	 *            The MetaData info
	 * @param jsonObject
	 *            the JSON with a list of properties.
	 * @throws JSONException
	 */
	private void setProperties(MetadataInfo metadataInfo, JSONObject jsonObject) throws JSONException {
		String[] properties = JSONObject.getNames(jsonObject);
		Map<String, String> cachedProperties = new HashMap<String, String>();
		if (properties != null) {
			for (String key : properties) {
				String value = jsonObject.get(key).toString();
				if (key.equals(UserConstants.PASSWORD)) {
					// password needs to be handled specifically since it needs to be decrypted.
					String encryptedPassword = value;
					String password = SimpleUserPasswordUtil.decryptPassword(encryptedPassword);
					metadataInfo.setProperty(key, password);
				} else {
					metadataInfo.setProperty(key, value);
				}
				if (metadataInfo instanceof UserInfo && userPropertyCache.isRegistered(key)) {
					cachedProperties.put(key, value);
				}
			}
		}
		if (!cachedProperties.isEmpty()) {
			userPropertyCache.setProperties(metadataInfo.getUniqueId(), cachedProperties);
		}
	}

	@Override
	public void updateProject(ProjectInfo projectInfo) throws CoreException {
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(projectInfo.getWorkspaceId());
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {
			String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(projectInfo.getWorkspaceId());
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
			String metafileId = projectInfo.getUniqueId();
			if (!SimpleMetaStoreUtil.encodeWorkspaceId(userId, DEFAULT_WORKSPACE_NAME).equals(projectInfo.getWorkspaceId())) {
				metafileId = projectInfo.getWorkspaceId() + SimpleMetaStoreUtil.SEPARATOR + metafileId;
			}
			if (!SimpleMetaStoreUtil.isMetaFile(userMetaFolder, metafileId)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.updateProject: could not update project: " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName,
						null));
			}
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, metafileId);
			if (!projectInfo.getUniqueId().equals(SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectInfo.getFullName()))) {
				IFileStore projectStore = projectInfo.getProjectStore();
				IFileStore defaultProjectStore = getDefaultContentLocation(readProject(projectInfo.getWorkspaceId(), projectInfo.getUniqueId()));
				// full name has changed, this is a project move
				String newProjectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectInfo.getFullName());
				String newMetafileId = newProjectId;
				if (!SimpleMetaStoreUtil.encodeWorkspaceId(userId, DEFAULT_WORKSPACE_NAME).equals(projectInfo.getWorkspaceId())) {
					newMetafileId = projectInfo.getWorkspaceId() + SimpleMetaStoreUtil.SEPARATOR + newMetafileId;
				}
				if (!SimpleMetaStoreUtil.moveMetaFile(userMetaFolder, metafileId, newMetafileId)) {
					throw new CoreException(
							new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not move project: "
									+ projectInfo.getUniqueId() + " to " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
				}

				// Move the meta folder if the project is not linked
				if (projectStore.equals(defaultProjectStore)
						&& !SimpleMetaStoreUtil.moveMetaFolder(workspaceMetaFolder, projectInfo.getUniqueId(), newProjectId)) {
					throw new CoreException(
							new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not move project: "
									+ projectInfo.getUniqueId() + " to " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
				}
				// if the content location is local, update the content location with the new name
				if (projectInfo.getContentLocation().getScheme().equals(SimpleMetaStoreUtil.FILE_SCHEMA)) {
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
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.updateProject: could not find workspace with id:" + projectInfo.getWorkspaceId() + ".", null));
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
				if (projectInfo.getContentLocation().getScheme().equals(SimpleMetaStoreUtil.FILE_SCHEMA)) {
					try {
						File oldContentLocation = new File(projectInfo.getContentLocation());
						if (workspaceMetaFolder.getCanonicalPath().equals(oldContentLocation.getParent())) {
							String newEncodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(newWorkspaceId);
							File newUserMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), newUserId);
							File newWorkspaceMetaFolder = new File(newUserMetaFolder, newEncodedWorkspaceName);
							projectInfo.setContentLocation(new File(newWorkspaceMetaFolder, projectInfo.getUniqueId()).toURI());
						}
					} catch (IOException e) {
						throw new CoreException(
								new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProject: could not update project: "
										+ projectInfo.getFullName() + " for workspace " + encodedWorkspaceName, null));
					}
				}

				// remove the temporary properties
				projectInfo.setProperty("newUserId", null);
				projectInfo.setProperty("newWorkspaceId", null);
			}
			try {
				jsonObject.put(SimpleMetaStore.ORION_VERSION, VERSION);
				jsonObject.put(MetadataInfo.UNIQUE_ID, projectInfo.getUniqueId());
				jsonObject.put("WorkspaceId", projectInfo.getWorkspaceId());
				jsonObject.put(UserConstants.FULL_NAME, projectInfo.getFullName());
				if (projectInfo.getContentLocation() != null) {
					URI contentLocation = projectInfo.getContentLocation();
					String encodedContentLocation = SimpleMetaStoreUtil.encodeProjectContentLocation(contentLocation.toString());
					jsonObject.put("ContentLocation", encodedContentLocation);
				}
				JSONObject properties = updateProperties(jsonObject, projectInfo);
				jsonObject.put("Properties", properties);
			} catch (JSONException e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.updateProject: could not update project: " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName,
						null));
			}
			metafileId = projectInfo.getUniqueId();
			if (!SimpleMetaStoreUtil.encodeWorkspaceId(userId, DEFAULT_WORKSPACE_NAME).equals(projectInfo.getWorkspaceId())) {
				metafileId = projectInfo.getWorkspaceId() + SimpleMetaStoreUtil.SEPARATOR + metafileId;
			}
			if (!SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, metafileId, jsonObject)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.updateProject: could not update project: " + projectInfo.getFullName() + " for workspace " + encodedWorkspaceName,
						null));
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Update the properties that have been updated in the provided metadataInfo. The updates are determined by looking
	 * at the the list of operations performed on the properties of this metadataInfo since the last read. See Bug
	 * 426842 for details.
	 * 
	 * @param jsonObject
	 *            The jsonObject to be updated with the properties changes.
	 * @param metadataInfo
	 *            The updated properties JSON metadata.
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
						String value = properties.getString(key);
						properties.remove(key);
						// update the user cache
						if (metadataInfo instanceof UserInfo && userPropertyCache.isRegistered(key)) {
							userPropertyCache.delete(key, value, metadataInfo.getUniqueId());
						}
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
					} else if (UserConstants.PASSWORD.equals(key)) {
						// password needs to be handled specifically since it is encrypted.
						String encryptedPassword = SimpleUserPasswordUtil.encryptPassword(value);
						properties.put(UserConstants.PASSWORD, encryptedPassword);
					} else {
						properties.put(key, value);
						if (metadataInfo instanceof UserInfo && userPropertyCache.isRegistered(key)) {
							userPropertyCache.add(key, value, metadataInfo.getUniqueId());
						}
					}
				}
			}
			metadataInfo.flush();
		} catch (JSONException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateProperties: could not update", e));
		}
		return properties;
	}

	@Override
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
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.updateUser: user name " + userInfo.getUserName() + " already exists, cannot rename from " + userInfo.getUniqueId(),
							e));
				}
				userInfo.setWorkspaceIds(newWorkspaceIds);

				// move the user folder to the new location
				if (SimpleMetaStoreUtil.isMetaUserFolder(getRootLocation(), newUserId)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.updateUser: user name " + userInfo.getUserName() + " already exists, cannot rename from " + userInfo.getUniqueId(),
							null));
				}
				if (!SimpleMetaStoreUtil.isMetaUserFolder(getRootLocation(), oldUserId)) {
					throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
							"SimpleMetaStore.updateUser: user name " + userInfo.getUniqueId() + " does not exist, cannot rename to " + userInfo.getUserName(),
							null));
				}
				File oldUserMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), oldUserId);
				File newUserMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), newUserId);
				SimpleMetaStoreUtil.moveUserMetaFolder(oldUserMetaFolder, newUserMetaFolder);

				userInfo.setUniqueId(newUserId);

				// update the user cache
				if (userPropertyCache.isRegistered(UserConstants.USER_NAME)) {
					userPropertyCache.delete(UserConstants.USER_NAME, oldUserId, oldUserId);
					userPropertyCache.add(UserConstants.USER_NAME, newUserId, newUserId);
				}

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
			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
			if (jsonObject == null) {
				throw new CoreException(
						new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not find user " + userId, null));
			}
			try {
				jsonObject.put(MetadataInfo.UNIQUE_ID, userInfo.getUniqueId());
				jsonObject.put(UserConstants.USER_NAME, userInfo.getUserName());
				jsonObject.put(UserConstants.FULL_NAME, userInfo.getFullName());
				JSONArray workspaceIds = new JSONArray(userInfo.getWorkspaceIds());
				jsonObject.put("WorkspaceIds", workspaceIds);
				JSONObject properties = updateProperties(jsonObject, userInfo);
				jsonObject.put("Properties", properties);
			} catch (JSONException e) {
				throw new CoreException(
						new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not update user: " + userId, e));
			}
			if (!SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, SimpleMetaStore.USER, jsonObject)) {
				throw new CoreException(
						new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.updateUser: could not update user: " + userId, null));
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void updateWorkspace(WorkspaceInfo workspaceInfo) throws CoreException {
		String userId = SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(workspaceInfo.getUniqueId());
		// lock the user when updating the workspace, see Bugzilla 462608.
		ReadWriteLock lock = getLockForUser(userId);
		lock.writeLock().lock();
		try {

			String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceInfo.getUniqueId());
			boolean renameUser = false;
			String newWorkspaceId = null;
			if (!workspaceInfo.getUserId().equals(userId)) {
				// user id does not equal the user Id in the workspace id, so we are renaming the user.
				renameUser = true;
				newWorkspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(workspaceInfo.getUserId(), encodedWorkspaceName);
			}

			File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getRootLocation(), userId);
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, workspaceInfo.getUniqueId());
			if (jsonObject == null) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.updateWorkspace: could not find workspace " + workspaceInfo.getUniqueId(), null));
			}
			try {
				jsonObject.put(SimpleMetaStore.ORION_VERSION, VERSION);
				if (renameUser) {
					jsonObject.put(MetadataInfo.UNIQUE_ID, newWorkspaceId);
				} else {
					jsonObject.put(MetadataInfo.UNIQUE_ID, workspaceInfo.getUniqueId());
				}
				jsonObject.put("UserId", workspaceInfo.getUserId());
				jsonObject.put(UserConstants.FULL_NAME, workspaceInfo.getFullName());
				JSONArray projectNames = new JSONArray(workspaceInfo.getProjectNames());
				jsonObject.put("ProjectNames", projectNames);
				JSONObject properties = updateProperties(jsonObject, workspaceInfo);
				jsonObject.put("Properties", properties);
			} catch (JSONException e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.updateWorkspace: could not update workspace: " + encodedWorkspaceName + " for user " + userId, e));
			}
			if (!SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, workspaceInfo.getUniqueId(), jsonObject)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1,
						"SimpleMetaStore.updateWorkspace: could not update workspace: " + encodedWorkspaceName + " for user " + userId, null));
			}
			if (renameUser) {
				SimpleMetaStoreUtil.moveMetaFile(userMetaFolder, workspaceInfo.getUniqueId(), newWorkspaceId);

				workspaceInfo.setUniqueId(newWorkspaceId);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}
}
