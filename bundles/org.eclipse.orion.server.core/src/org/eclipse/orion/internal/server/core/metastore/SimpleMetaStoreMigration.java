/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
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
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrate the MetaStore from the version one of the simple metadata store (SimpleMetaStoreV1) to 
 * version two of the simple metadata store (SimpleMetaStoreV2).
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStoreMigration {

	/**
	 * The first version of the Simple Meta Store was version 4 introduced for Orion 4.0.
	 */
	public final static int VERSION4 = 4;

	/**
	 * The second version of the Simple Meta Store was version 6 introduced for Orion 4.0.
	 */
	public final static int VERSION6 = 6;

	//	private static final String LEGACY_METADATA_DIR = "/.metadata/.plugins/org.eclipse.orion.server.core/.settings/";
	//	private static final String SECURESTORAGE_DIR = "/.metadata/.plugins/org.eclipse.orion.server.user.securestorage";
	private Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$

	private void deleteFile(File parentFile) {
		if (parentFile.isDirectory()) {
			File[] allFiles = parentFile.listFiles();
			if (allFiles.length == 0) {
				parentFile.delete();
			} else {
				for (File file : allFiles) {
					deleteFile(file);
				}
				parentFile.delete();
			}
		} else {
			parentFile.delete();
		}
	}

	//	public void doMigration(File rootLocation) {
	//		logger.info("Starting simple storage migration."); //$NON-NLS-1$

	//		updateOrionVersion(rootLocation, SimpleMetaStore.ROOT);

	//		File rootFile = SimpleMetaStoreUtil.retrieveMetaFile(rootLocation, SimpleMetaStore.ROOT);
	//		File[] files = rootLocation.listFiles();
	//		for (int i = 0; i < files.length; i++) {
	//			File next = files[i];
	//			if (next.getName().equals(rootFile.getName())) {
	//				continue;
	//			} else if (next.getName().equals(".metadata")) {
	//				// skip the eclipse workspace metadata folder
	//				continue;
	//			} else if (next.isDirectory() && next.getName().length() == 2) {
	//				// process organizational folder "an" in /serverworkspace/an/anthony
	//				updateOrganizationalFolder(next);
	//			} else {
	//				logger.info("Workspace root contains invalid metadata: deleted orphan folder " + next.toString()); //$NON-NLS-1$
	//				deleteFile(next);
	//			}
	//		}

	//		File secureStorage = new File(rootLocation, SECURESTORAGE_DIR);
	//		if (secureStorage.exists()) {
	//			logger.info("Deleted legacy secure storage folder: " + secureStorage.toString()); //$NON-NLS-1$
	//			deleteFile(secureStorage);
	//		}
	//		File orionLegacyPrefs = new File(rootLocation, LEGACY_METADATA_DIR);
	//		if (orionLegacyPrefs.exists()) {
	//			logger.info("Deleted legacy metadata storage folder: " + orionLegacyPrefs.toString()); //$NON-NLS-1$
	//			deleteFile(orionLegacyPrefs);
	//		}

	//		logger.info("Completed simple storage migration."); //$NON-NLS-1$
	//	}

	public void doMigration(File userMetaFolder) throws JSONException {
		logger.info("Migration: Migrating user " + userMetaFolder.getName() + " to the latest (version " + SimpleMetaStore.VERSION + ")");
		int oldVersion = updateOrionVersion(userMetaFolder, SimpleMetaStore.USER);
		File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, SimpleMetaStore.USER);
		File[] files = userMetaFolder.listFiles();
		int directoryCount = 0;
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.isFile() && next.getName().equals(userMetaFile.getName())) {
				// skip the user.json
				continue;
			} else if (oldVersion == VERSION4 && next.isDirectory()) {
				// process a workspace folder in /serverworkspace/an/anthony
				updateWorkspaceFolder(next);
				directoryCount++;
			} else if (oldVersion == VERSION6 && next.isDirectory()) {
				// skip the workspace folder in /serverworkspace/an/anthony/OrionContent
				directoryCount++;
				continue;
			} else if (oldVersion == VERSION4 && next.isFile()) {
				logger.info("User folder contains invalid metadata: orphan orphan file " + next.toString()); //$NON-NLS-1$
				deleteFile(next);
			} else if (oldVersion == VERSION6 && next.isFile() && next.getName().endsWith(SimpleMetaStoreUtil.METAFILE_EXTENSION)) {
				// process a {workspaceId}.json or {projectName}.json
				updateOrionVersion(next.getParentFile(), next.getName().substring(0, next.getName().length() - SimpleMetaStoreUtil.METAFILE_EXTENSION.length()));
			} else {
				logger.info("User folder contains invalid metadata: orphan file " + next.toString()); //$NON-NLS-1$
			}
		}
		if ((oldVersion == VERSION4 || oldVersion == VERSION6) && directoryCount >= 1) {
			mergeMultipleWorkspaces(directoryCount, userMetaFolder);
		}
	}

	public boolean isMigrationRequired(JSONObject jsonObject) throws JSONException {
		if (!jsonObject.has(SimpleMetaStore.ORION_VERSION)) {
			return true;
		}
		int version = jsonObject.getInt(SimpleMetaStore.ORION_VERSION);
		if (version != SimpleMetaStore.VERSION) {
			return true;
		}
		return false;
	}

	/**
	 * In version 4 and version 6 it as possible to have multiple workspaces, merge them in the default workspace
	 * and delete the extra workspaces.
	 * @param count The number of folders under the user, should be one for the workspace folder.
	 * @param userMetaFolder The user metadata folder.
	 * @throws JSONException
	 */
	private void mergeMultipleWorkspaces(int count, File userMetaFolder) throws JSONException {
		JSONObject userJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
		JSONArray workspaceIds = userJSON.getJSONArray("WorkspaceIds");
		boolean changedUserJSON = false;
		boolean changedWorkspaceJSON = false;
		if (workspaceIds.length() == count) {
			// the extra folder(s) in the user folder are valid workspaces, merge them into one
			String firstWorkspaceId = null;
			JSONObject firstWorkspaceJSON = null;
			File firstWorkspaceFolder = null;
			for (int i = 0; i < workspaceIds.length(); i++) {
				String workspaceId = workspaceIds.getString(i);
				String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
				if (SimpleMetaStoreUtil.isMetaFile(userMetaFolder, workspaceId) && SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName)) {
					JSONObject workspaceJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, workspaceId);
					File workspaceFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
					if (firstWorkspaceId == null) {
						// the first workspace is the master
						firstWorkspaceId = workspaceId;
						firstWorkspaceJSON = workspaceJSON;
						firstWorkspaceFolder = workspaceFolder;
						continue;
					} else {
						JSONArray projectNames = workspaceJSON.getJSONArray("ProjectNames");
						for (int p = 0; p < projectNames.length(); p++) {
							String projectName = projectNames.getString(p);
							if (SimpleMetaStoreUtil.isMetaFolder(workspaceFolder, projectName) && SimpleMetaStoreUtil.isMetaFile(userMetaFolder, projectName)) {
								// project is in the default location, move project folder and then update project metadata
								File originalProjectFolder = SimpleMetaStoreUtil.retrieveMetaFolder(workspaceFolder, projectName);
								SimpleMetaStoreUtil.moveMetaFolder(workspaceFolder, projectName, firstWorkspaceFolder, projectName);
								File newProjectFolder = SimpleMetaStoreUtil.retrieveMetaFolder(firstWorkspaceFolder, projectName);
								logger.debug("Migration: Moved project folder: " + originalProjectFolder.getAbsolutePath() + " to " + newProjectFolder.getAbsolutePath());
								JSONObject projectJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, projectName);
								String contentLocation = newProjectFolder.toURI().toString();
								// remove trailing slash from the contentLocation 
								contentLocation = contentLocation.substring(0, contentLocation.length() - 1);
								projectJSON.put("ContentLocation", contentLocation);
								projectJSON.put("WorkspaceId", firstWorkspaceId);
								SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, projectName, projectJSON);
								File updatedMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, projectName);
								logger.debug("Migration: Updated project metadata: " + updatedMetaFile.getAbsolutePath() + " with new workspaceId " + firstWorkspaceId);
							}
							JSONArray firstWorkspaceProjectNames = firstWorkspaceJSON.getJSONArray("ProjectNames");
							firstWorkspaceProjectNames.put(projectName);
							logger.debug("Migration: Updated workspace metadata: updated workspace " + firstWorkspaceId + " with new project " + projectName);
							changedWorkspaceJSON = true;
						}
					}
					SimpleMetaStoreUtil.deleteMetaFolder(userMetaFolder, encodedWorkspaceName, true);
					logger.debug("Migration: Updated workspace metadata: deleted multiple workspace folder of " + workspaceId + " at " + workspaceFolder);
					File workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, workspaceId);
					SimpleMetaStoreUtil.deleteMetaFile(userMetaFolder, workspaceId);
					logger.debug("Migration: Updated workspace metadata: deleted multiple workspace file at " + workspaceMetaFile);
					changedUserJSON = true;
				} else {
					// an invalid workspace folder
					logger.info("Workspace folder contains invalid metadata: orphan folder " + workspaceId); //$NON-NLS-1$
				}
			}
			if (firstWorkspaceId != null && changedUserJSON) {
				updateUserJson(userMetaFolder, userJSON, firstWorkspaceId);
				logger.debug("Migration: Updated user metadata: user has one workspace " + firstWorkspaceId);
			}
			if (firstWorkspaceId != null && changedWorkspaceJSON) {
				File updatedMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, firstWorkspaceId);
				SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, firstWorkspaceId, firstWorkspaceJSON);
				logger.debug("Migration: Updated workspace metadata: updated workspace metadata at " + updatedMetaFile.getAbsolutePath());
			}
		}
	}

	//	private void updateOrganizationalFolder(File folder) {
	//		File[] files = folder.listFiles();
	//		for (int i = 0; i < files.length; i++) {
	//			File next = files[i];
	//			if (next.isDirectory()) {
	//				// process user folder "anthony" in /serverworkspace/an/anthony
	//				updateUserFolder(next);
	//			} else {
	//				logger.info("Organizational folder contains invalid metadata: deleted orphan file " + next.toString()); //$NON-NLS-1$
	//				deleteFile(next);
	//			}
	//		}
	//	}

	private void moveProjectJsonFile(File folder, String projectName) {
		File userMetaFolder = folder.getParentFile();
		JSONObject projectMetaFile = SimpleMetaStoreUtil.readMetaFile(folder, projectName);
		File newProjectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, projectName);
		if (newProjectMetaFile.exists()) {
			logger.error("Duplicate project metadata file at " + newProjectMetaFile.toString()); //$NON-NLS-1$
			return;
		}
		SimpleMetaStoreUtil.createMetaFile(userMetaFolder, projectName, projectMetaFile);
		logger.debug("Migration: Created project MetaData file: " + newProjectMetaFile.getAbsolutePath());

		File oldProjectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, projectName);
		SimpleMetaStoreUtil.deleteMetaFile(folder, projectName);
		logger.debug("Migration: Deleted old project MetaData file: " + oldProjectMetaFile.getAbsolutePath());
	}

	private String moveWorkspaceJsonFile(File folder) throws JSONException {
		File parent = folder.getParentFile();
		File workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, SimpleMetaStore.WORKSPACE);
		JSONObject workspaceMetaData = SimpleMetaStoreUtil.readMetaFile(folder, SimpleMetaStore.WORKSPACE);
		if (!workspaceMetaData.has("UniqueId")) {
			logger.error("Workspace metadata is missing UniqueId " + workspaceMetaFile.toString()); //$NON-NLS-1$
			return null;
		}
		String workspaceId = workspaceMetaData.getString("UniqueId");
		File newWorkspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(parent, workspaceId);
		if (newWorkspaceMetaFile.exists()) {
			logger.error("Duplicate workspace metadata file at " + newWorkspaceMetaFile.toString()); //$NON-NLS-1$
			return null;
		}
		SimpleMetaStoreUtil.createMetaFile(parent, workspaceId, workspaceMetaData);

		logger.debug("Migration: Created workspace MetaData file: " + newWorkspaceMetaFile.getAbsolutePath());

		File oldWorkspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, SimpleMetaStore.WORKSPACE);
		SimpleMetaStoreUtil.deleteMetaFile(folder, SimpleMetaStore.WORKSPACE);
		logger.debug("Migration: Deleted old workspace MetaData file: " + oldWorkspaceMetaFile.getAbsolutePath());
		return workspaceId;
	}

	/**
	 * Update the Orion version in the provided file and folder.
	 * @param parent The parent folder containing the metadata (JSON) file.
	 * @param name The name of the file without the ".json" extension.
	 * @return The previous version that was in the metadata file.
	 * @throws JSONException 
	 */
	private int updateOrionVersion(File parent, String name) throws JSONException {
		JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(parent, name);
		int oldVersion = -1;
		if (jsonObject.has(SimpleMetaStore.ORION_VERSION)) {
			oldVersion = jsonObject.getInt(SimpleMetaStore.ORION_VERSION);
		}
		jsonObject.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
		SimpleMetaStoreUtil.updateMetaFile(parent, name, jsonObject);
		File metaFile = SimpleMetaStoreUtil.retrieveMetaFile(parent, name);
		String oldVersionStr = (oldVersion == -1) ? "UNKNOWN" : Integer.toString(oldVersion);
		logger.debug("Migration: Updated Orion version from version " + oldVersionStr + " to version " + SimpleMetaStore.VERSION + " in MetaData file: " + metaFile.getAbsolutePath());
		return oldVersion;
	}

	/**
	 * Update the user metadata file with the new single workspace and user rights.
	 * @param userMetaFolder The user metadata folder.
	 * @param userJSON The current user metadata.
	 * @param workspaceId the single workspace. 
	 */
	private void updateUserJson(File userMetaFolder, JSONObject userJSON, String workspaceId) throws JSONException {
		JSONArray workspaceIds = new JSONArray();
		workspaceIds.put(workspaceId);
		userJSON.put("WorkspaceIds", workspaceIds);

		JSONObject properties = userJSON.getJSONObject("Properties");
		JSONArray userRights = new JSONArray();
		JSONObject userRight = new JSONObject();
		userRight.put("Method", 15);
		String usersRight = "/users/";
		userRight.put("Uri", usersRight.concat(userMetaFolder.getName()));
		userRights.put(userRight);

		userRight = new JSONObject();
		userRight.put("Method", 15);
		String workspaceRight = "/workspace/";
		userRight.put("Uri", workspaceRight.concat(workspaceId));
		userRights.put(userRight);

		userRight = new JSONObject();
		userRight.put("Method", 15);
		userRight.put("Uri", workspaceRight.concat(workspaceId).concat("/*"));
		userRights.put(userRight);

		userRight = new JSONObject();
		userRight.put("Method", 15);
		String fileRight = "/file/";
		userRight.put("Uri", fileRight.concat(workspaceId));
		userRights.put(userRight);

		userRight = new JSONObject();
		userRight.put("Method", 15);
		userRight.put("Uri", fileRight.concat(workspaceId).concat("/*"));
		userRights.put(userRight);

		properties.put("UserRights", userRights);
		userJSON.put("Properties", properties);

		SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, SimpleMetaStore.USER, userJSON);
	}

	private void updateWorkspaceFolder(File folder) throws JSONException {
		if (!SimpleMetaStoreUtil.isMetaFile(folder, SimpleMetaStore.WORKSPACE)) {
			logger.info("Workspace folder contains invalid metadata: deleted orphan folder " + folder.toString()); //$NON-NLS-1$
			deleteFile(folder);
			return;
		}
		updateOrionVersion(folder, SimpleMetaStore.WORKSPACE);
		String workspaceId = moveWorkspaceJsonFile(folder);
		if (workspaceId == null) {
			return;
		}
		File workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder.getParentFile(), workspaceId);
		JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(folder.getParentFile(), workspaceId);
		if (!jsonObject.has("ProjectNames")) {
			logger.error("Workspace metadata is missing ProjectNames " + workspaceMetaFile.toString()); //$NON-NLS-1$
			return;
		}
		JSONArray projectNames = jsonObject.getJSONArray("ProjectNames");
		List<String> projectNameList = new ArrayList<String>();
		for (int i = 0; i < projectNames.length(); i++) {
			projectNameList.add(projectNames.getString(i));
		}
		File[] files = folder.listFiles();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.getName().equals(workspaceMetaFile.getName())) {
				continue;
			} else if (next.isDirectory()) {
				// process project folder in /serverworkspace/an/anthony/workspace
				if (!projectNameList.contains(next.getName())) {
					logger.info("Workspace folder contains invalid metadata: deleted orphan project folder " + next.toString()); //$NON-NLS-1$
					deleteFile(next);
				}
			} else if (next.isFile()) {
				// process project folder in /serverworkspace/an/anthony/workspace
				if (next.getName().endsWith(SimpleMetaStoreUtil.METAFILE_EXTENSION)) {
					String name = next.getName().substring(0, next.getName().length() - SimpleMetaStoreUtil.METAFILE_EXTENSION.length());
					if (!projectNameList.contains(name)) {
						logger.info("Workspace folder contains invalid metadata: deleted orphan project file " + next.toString()); //$NON-NLS-1$
						deleteFile(next);
					} else {
						updateOrionVersion(folder, name);
						moveProjectJsonFile(folder, name);
					}
				} else {
					logger.info("Workspace folder contains invalid metadata: deleted orphan file " + next.toString()); //$NON-NLS-1$
					deleteFile(next);
				}
			} else {
				logger.info("Workspace folder contains invalid metadata: deleted orphan file " + next.toString()); //$NON-NLS-1$
				deleteFile(next);
			}
		}
	}
}
