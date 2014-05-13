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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
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
public class SimpleMetaStoreMigrationV2 {

	private static final String LEGACY_METADATA_DIR = "/.metadata/.plugins/org.eclipse.orion.server.core/.settings/";
	private static final String SECURESTORAGE_DIR = "/.metadata/.plugins/org.eclipse.orion.server.user.securestorage";
	private PrintStream migrationLog;

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

	public void doMigration(File rootLocation) {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
		logger.info("Starting simple storage migration."); //$NON-NLS-1$
		migrationLogOpen(rootLocation);
		migrationLogPrint("Migrating MetaStore from simple to simple2.");

		updateOrionVersion(rootLocation, SimpleMetaStore.ROOT);

		File rootFile = SimpleMetaStoreUtil.retrieveMetaFile(rootLocation, SimpleMetaStore.ROOT);
		File[] files = rootLocation.listFiles();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.getName().equals(rootFile.getName())) {
				continue;
			} else if (next.getName().equals(".metadata")) {
				// skip the eclipse workspace metadata folder
				continue;
			} else if (next.isDirectory() && next.getName().length() == 2) {
				// process organizational folder "an" in /serverworkspace/an/anthony
				updateOrganizationalFolder(next);
			} else {
				migrationLogPrint("INFO: workspace root contains invalid metadata: deleted orphan folder " + next.toString()); //$NON-NLS-1$
				deleteFile(next);
			}
		}
		
		File secureStorage = new File(rootLocation, SECURESTORAGE_DIR);
		if (secureStorage.exists()) {
			migrationLogPrint("INFO: deleted legacy secure storage folder: " + secureStorage.toString()); //$NON-NLS-1$
			deleteFile(secureStorage);
		}
		File orionLegacyPrefs = new File(rootLocation, LEGACY_METADATA_DIR);
		if (orionLegacyPrefs.exists()) {
			migrationLogPrint("INFO: deleted legacy metadata storage folder: " + orionLegacyPrefs.toString()); //$NON-NLS-1$
			deleteFile(orionLegacyPrefs);
		}

		migrationLogClose();
		logger.info("Completed simple storage migration."); //$NON-NLS-1$
	}
	
	public boolean isMigrationRequired(File rootLocation) {
		if (SimpleMetaStore.getOrionVersion(rootLocation) == SimpleMetaStoreV1.VERSION) {
			// version one exists, migration is required
			return true;
		}
		return false;
	}

	/**
	 * Close the migration log.
	 */
	private void migrationLogClose() {
		migrationLog.close();
	}

	/** 
	 * Open the migration log, the log is stored at the user provided workspace root.
	 * @param workspaceRoot the workspace root.
	 * @throws FileNotFoundException
	 */
	private void migrationLogOpen(File workspaceRoot) {
		try {
			File metadataFolder = new File(workspaceRoot, ".metadata");
			File logFile = new File(metadataFolder, "migration2.log");
			FileOutputStream stream = new FileOutputStream(logFile);
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.info("Simple storage version two migration log is at " + logFile.getAbsolutePath());
			migrationLog = new PrintStream(stream);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("SimpleMetaStoreMigration.migrationLogOpen: could not create migration log.");
		}
	}

	/**
	 * Print a message to the migration log.
	 * @param message the message.
	 */
	private void migrationLogPrint(String message) {
		migrationLog.println(message);
		// If debugging is turned on print the message to the logger as well
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
		logger.debug(message); //$NON-NLS-1$
	}

	private void moveProjectJsonFile(File folder, String projectName) {
		File userMetaFolder = folder.getParentFile();
		JSONObject projectMetaFile = SimpleMetaStoreUtil.readMetaFile(folder, projectName);
		File newProjectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, projectName);
		if (newProjectMetaFile.exists()) {
			migrationLogPrint("ERROR: duplicate project metadata file at " + newProjectMetaFile.toString()); //$NON-NLS-1$
			return;
		}
		SimpleMetaStoreUtil.createMetaFile(userMetaFolder, projectName, projectMetaFile);
		migrationLogPrint("Created project MetaData file: " + newProjectMetaFile.getAbsolutePath());

		File oldProjectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, projectName);
		SimpleMetaStoreUtil.deleteMetaFile(folder, projectName);
		migrationLogPrint("Deleted old workspace MetaData file: " + oldProjectMetaFile.getAbsolutePath());
	}

	private String moveWorkspaceJsonFile(File folder) {
		try {
			File parent = folder.getParentFile();
			File workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, SimpleMetaStore.WORKSPACE);
			JSONObject workspaceMetaData = SimpleMetaStoreUtil.readMetaFile(folder, SimpleMetaStore.WORKSPACE);
			if (!workspaceMetaData.has("UniqueId")) {
				migrationLogPrint("ERROR: workspace metadata is missing UniqueId " + workspaceMetaFile.toString()); //$NON-NLS-1$
				return null;
			}
			String workspaceId = workspaceMetaData.getString("UniqueId");
			File newWorkspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(parent, workspaceId);
			if (newWorkspaceMetaFile.exists()) {
				migrationLogPrint("ERROR: duplicate workspace metadata file at " + newWorkspaceMetaFile.toString()); //$NON-NLS-1$
				return null;
			}
			SimpleMetaStoreUtil.createMetaFile(parent, workspaceId, workspaceMetaData);
			
			migrationLogPrint("Created workspace MetaData file: " + newWorkspaceMetaFile.getAbsolutePath());

			File oldWorkspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, SimpleMetaStore.WORKSPACE);
			SimpleMetaStoreUtil.deleteMetaFile(folder, SimpleMetaStore.WORKSPACE);
			migrationLogPrint("Deleted old workspace MetaData file: " + oldWorkspaceMetaFile.getAbsolutePath());
			return workspaceId;
		} catch (JSONException e) {
			throw new RuntimeException("SimpleMetaStoreMigrationV2: could not update file: " + e.getLocalizedMessage());
		}
	}

	private void updateOrganizationalFolder(File folder) {
		File[] files = folder.listFiles();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.isDirectory()) {
				// process user folder "anthony" in /serverworkspace/an/anthony
				updateUserFolder(next);
			} else {
				migrationLogPrint("INFO: organizational folder contains invalid metadata: deleted orphan file " + next.toString()); //$NON-NLS-1$
				deleteFile(next);
			}
		}
	}

	private void updateOrionVersion(File parent, String name) {
		try {
			JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(parent, name);
			jsonObject.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStoreV2.VERSION);
			SimpleMetaStoreUtil.updateMetaFile(parent, name, jsonObject);
			File metaFile = SimpleMetaStoreUtil.retrieveMetaFile(parent, name);
			migrationLogPrint("Updated Orion version in MetaData file: " + metaFile.getAbsolutePath());
		} catch (JSONException e) {
			throw new RuntimeException("SimpleMetaStoreMigrationV2: could not update file: " + e.getLocalizedMessage());
		}
	}

	private void updateUserFolder(File folder) {
		if (!SimpleMetaStoreUtil.isMetaFile(folder, SimpleMetaStore.USER)) {
			migrationLogPrint("INFO: user folder contains invalid metadata: deleted orphan folder " + folder.toString()); //$NON-NLS-1$
			deleteFile(folder);
			return;
		}
		migrationLogPrint("Processing user: " + folder.getName());
		updateOrionVersion(folder, SimpleMetaStore.USER);
		File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, SimpleMetaStore.USER);
		File[] files = folder.listFiles();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.getName().equals(userMetaFile.getName())) {
				continue;
			} else if (next.isDirectory()) {
				// process workspace folder in /serverworkspace/an/anthony
				updateWorkspaceFolder(next);
			} else {
				migrationLogPrint("INFO: user folder contains invalid metadata: orphan orphan file " + next.toString()); //$NON-NLS-1$
				deleteFile(next);
			}
		}
	}

	private void updateWorkspaceFolder(File folder) {
		try {
			if (!SimpleMetaStoreUtil.isMetaFile(folder, SimpleMetaStore.WORKSPACE)) {
				migrationLogPrint("INFO: workspace folder contains invalid metadata: deleted orphan folder " + folder.toString()); //$NON-NLS-1$
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
				migrationLogPrint("ERROR: workspace metadata is missing ProjectNames " + workspaceMetaFile.toString()); //$NON-NLS-1$
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
						migrationLogPrint("INFO: workspace folder contains invalid metadata: deleted orphan project folder " + next.toString()); //$NON-NLS-1$
						deleteFile(next);
					}
				} else if (next.isFile()) {
					// process project folder in /serverworkspace/an/anthony/workspace
					if (next.getName().endsWith(SimpleMetaStoreUtil.METAFILE_EXTENSION)) {
						String name = next.getName().substring(0, next.getName().length() - SimpleMetaStoreUtil.METAFILE_EXTENSION.length());
						if (!projectNameList.contains(name)) {
							migrationLogPrint("INFO: workspace folder contains invalid metadata: deleted orphan project file " + next.toString()); //$NON-NLS-1$
							deleteFile(next);
						} else {
							updateOrionVersion(folder, name);
							moveProjectJsonFile(folder, name);
						}
					} else {
						migrationLogPrint("INFO: workspace folder contains invalid metadata: deleted orphan file " + next.toString()); //$NON-NLS-1$
						deleteFile(next);
					}
				} else {
					migrationLogPrint("INFO: workspace folder contains invalid metadata: deleted orphan file " + next.toString()); //$NON-NLS-1$
					deleteFile(next);
				}
			}
		} catch (JSONException e) {
			throw new RuntimeException("SimpleMetaStoreMigrationV2: could not update file: " + e.getLocalizedMessage());
		}
	}

}
