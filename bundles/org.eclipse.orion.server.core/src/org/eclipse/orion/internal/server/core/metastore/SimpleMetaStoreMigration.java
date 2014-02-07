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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.crypto.spec.PBEKeySpec;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrate the MetaStore from the legacy store to the simple store.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStoreMigration {

	private PrintStream migrationLog;

	private static final String METADATA_DIR = "/.metadata/.plugins/org.eclipse.orion.server.core/.settings/";
	private static final String SECURESTORAGE = "/.metadata/.plugins/org.eclipse.orion.server.user.securestorage/user_store";
	public static final String ORION_STORAGE_PASSWORD = "orion.storage.password"; //$NON-NLS-1$

	/**
	 * Specifies if it is possible to prompt user. Expected value: {@link Boolean}.
	 */
	static final public String PROMPT_USER = "org.eclipse.equinox.security.storage.promptUser"; //$NON-NLS-1$

	/**
	 * Storage will use this password. Expected value: {@link PBEKeySpec}.
	 */
	static final public String DEFAULT_PASSWORD = "org.eclipse.equinox.security.storage.defaultPassword"; //$NON-NLS-1$

	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;

	public boolean isMigrationRequired(File rootLocation) {
		File secureStorage = new File(rootLocation, SECURESTORAGE);
		File orionUserPrefsFile = new File(rootLocation, METADATA_DIR + "Users.prefs");
		if (secureStorage.exists() || orionUserPrefsFile.exists()) {
			// Legacy files exist, migration is required
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
			File logFile = new File(metadataFolder, "migration.log");
			FileOutputStream stream = new FileOutputStream(logFile);
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.info("Simple storage migration log is at " + logFile.getAbsolutePath());
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

	public void doMigration(File rootLocation) {
		try {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.info("Starting simple storage migration."); //$NON-NLS-1$
			migrationLogOpen(rootLocation);
			migrationLogPrint("Migrating MetaStore from legacy to simple.");

			// make sure the task service is running before starting migration.
			getTaskService();

			File orionProjectPrefsFile = new File(rootLocation, METADATA_DIR + "Projects.prefs");
			File orionWorkspacePrefsFile = new File(rootLocation, METADATA_DIR + "Workspaces.prefs");
			File orionUserPrefsFile = new File(rootLocation, METADATA_DIR + "Users.prefs");
			File orionSitesPrefsFile = new File(rootLocation, METADATA_DIR + "SiteConfigurations.prefs");
			File orionOperationsFile = new File(rootLocation, METADATA_DIR + "Operations.prefs");
			File orionSecureStorageFile = new File(rootLocation, SECURESTORAGE);

			Map<String, Map<String, String>> users = getMetadataFromPropertiesFile(orionUserPrefsFile);
			Map<String, Map<String, String>> usersSecureStorage = getMetadataFromSecureStorage(orionSecureStorageFile);
			Map<String, Map<String, String>> workspaces = getMetadataFromPropertiesFile(orionWorkspacePrefsFile);
			Map<String, Map<String, String>> projects = getMetadataFromPropertiesFile(orionProjectPrefsFile);
			Map<String, Map<String, String>> sites = getMetadataFromPropertiesFile(orionSitesPrefsFile);
			Map<String, Map<String, String>> operations = getMetadataFromPropertiesFile(orionOperationsFile);

			File metaStoreRootFolder = createOrUpdateMetaStoreRoot(rootLocation);

			int userSize = users.size();
			int userCount = 1;
			for (String userId : users.keySet()) {
				Map<String, String> userProperties = users.get(userId);
				Map<String, String> usersSecureStorageProperties = usersSecureStorage.get(userId);

				String userName = userProperties.get("UserName");
				if (userName == null || userName.equals("") || (userName.equals(userId) && userName.length() <= 2)) {
					// the userId and userName are both A in the users.pref, use the login value in the secure store.
					if (usersSecureStorageProperties == null) {
						migrationLogPrint("Processing UserId " + userId + " (" + userCount++ + " of " + userSize + ") ");
						migrationLogPrint("ERROR: Did not migrate user: no UserName for userId: " + userId);
						continue;
					}
					String login = usersSecureStorageProperties.get("login");
					userName = login;
					userProperties.put("UserName", login);
					if (!userProperties.containsKey("Id")) {
						userProperties.put("Id", login);
					}
				}
				migrationLogPrint("Processing UserId " + userId + " (" + userCount++ + " of " + userSize + ") UserName " + userName);

				File newUserHome = createOrUpdateUser(metaStoreRootFolder, userId, userProperties, usersSecureStorage, sites, operations);
				if (newUserHome == null) {
					migrationLogPrint("Did not migrate user: " + userName);
					continue;
				}

				String userWorkspaceProperty = userProperties.get("Workspaces");
				if (userWorkspaceProperty == null) {
					migrationLogPrint("User " + userName + " has no workspaces.");
				} else {
					JSONObject workspaceObject = new JSONArray(userWorkspaceProperty).getJSONObject(0);
					String workspaceId = workspaceObject.getString("Id");
					Map<String, String> workspaceProperties = workspaces.get(workspaceId);
					if (workspaceProperties == null) {
						workspaceProperties = new HashMap<String, String>();
					}
					String encodedWorkspaceName = "OrionContent";
					if (!SimpleMetaStoreUtil.isMetaFolder(newUserHome, encodedWorkspaceName)) {
						SimpleMetaStoreUtil.createMetaFolder(newUserHome, encodedWorkspaceName);
						migrationLogPrint("Created Workspace folder: " + SimpleMetaStoreUtil.readMetaFolder(newUserHome, encodedWorkspaceName).getAbsolutePath());
					}
					File newWorkspaceHome = SimpleMetaStoreUtil.readMetaFolder(newUserHome, encodedWorkspaceName);

					String workspaceProjectProperty = workspaceProperties.get("Projects");
					if (workspaceProjectProperty == null || workspaceProjectProperty.equals("[]")) {
						migrationLogPrint("User " + userName + " has no projects.");
						workspaceProperties.put("ProjectNames", "[]");
						workspaceProperties.put("UserId", userName);
					} else {
						JSONArray workspaceProjectsArray = new JSONArray(workspaceProjectProperty);
						JSONArray projectNamesJSON = new JSONArray();
						for (int i = 0; i < workspaceProjectsArray.length(); i++) {
							JSONObject projectObject = workspaceProjectsArray.getJSONObject(i);
							String projectId = projectObject.getString("Id");
							Map<String, String> projectProperties = projects.get(projectId);
							String projectName = projectProperties.get("Name");
							if (projectName.equals("workspace")) {
								// you cannot name a project "workspace", simply append a number to the name.
								migrationLogPrint("Changed project name from: workspace to: workspace1 .");
								projectName = "workspace1";
								projectProperties.put("Name", projectName);
							}
							JSONObject newProjectJSON = getProjectJSONfromProperties(projectProperties);
							String newProjectId = newProjectJSON.getString("UniqueId");

							// if the content location is local, update the content location with the new name
							if (newProjectJSON.has("ContentLocation")) {
								String contentLocation = newProjectJSON.getString("ContentLocation");
								if (contentLocation.equals(projectId)) {
									// This is the Orion 0.2 storage format
									File oldContentLocation = new File(metaStoreRootFolder, contentLocation);
									File newContentLocation = SimpleMetaStoreUtil.retrieveMetaFolder(newWorkspaceHome, newProjectId);
									newProjectJSON.put("ContentLocation", newContentLocation.toURI());
									oldContentLocation.renameTo(newContentLocation);
									migrationLogPrint("Moved Project folder: " + oldContentLocation.getAbsolutePath() + " to: " + newContentLocation.getAbsolutePath());
								} else {
									URI contentLocationURI = new URI(contentLocation);
									if (contentLocationURI.getScheme().equals("file")) {
										File oldContentLocation = new File(contentLocationURI);
										if (oldContentLocation.toString().startsWith(rootLocation.toString())) {
											File newContentLocation = SimpleMetaStoreUtil.retrieveMetaFolder(newWorkspaceHome, newProjectId);
											if (!oldContentLocation.exists() || !oldContentLocation.isDirectory()) {
												// could not move to the new content location, old content folder is missing
												migrationLogPrint("ERROR: Could not handle project folder: " + oldContentLocation.getAbsolutePath() + ": the folder does not exist.");
												continue;
											} else if (!oldContentLocation.renameTo(newContentLocation)) {
												// could not move to the new content location, likely an invalid project name
												migrationLogPrint("ERROR: Could not move project folder: " + oldContentLocation.getAbsolutePath() + " to: " + newContentLocation.getAbsolutePath() + ": bad project name: " + projectName);
												continue;
											} else {
												migrationLogPrint("Moved Project folder: " + oldContentLocation.getAbsolutePath() + " to: " + newContentLocation.getAbsolutePath());
												newProjectJSON.put("ContentLocation", newContentLocation.toURI());
											}
										}
									}
								}
							} else {
								migrationLogPrint("ERROR: Skipped Project : " + projectName + ", the project has no ContentLocation.");
								continue;
							}

							// Add the WorkspaceId to the projectJSON
							newProjectJSON.put("WorkspaceId", userName + "-OrionContent");

							// save projectJSON
							if (SimpleMetaStoreUtil.isMetaFile(newWorkspaceHome, newProjectId)) {
								SimpleMetaStoreUtil.updateMetaFile(newWorkspaceHome, newProjectId, newProjectJSON);
								File projectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(newWorkspaceHome, newProjectId);
								migrationLogPrint("Updated project MetaData file: " + projectMetaFile.getAbsolutePath());
								projectNamesJSON.put(projectName);
							} else {
								if (!SimpleMetaStoreUtil.createMetaFile(newWorkspaceHome, newProjectId, newProjectJSON)) {
									migrationLogPrint("ERROR: Skipped Project : " + projectName + ", could not save project json file, likely bad project name: " + projectName + ".");
									continue;
								}
								File projectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(newWorkspaceHome, newProjectId);
								migrationLogPrint("Created project MetaData file: " + projectMetaFile.getAbsolutePath());
								projectNamesJSON.put(projectName);
							}
						}

						workspaceProperties.put("ProjectNames", projectNamesJSON.toString());
						workspaceProperties.put("UserId", userName);
					}

					JSONObject newWorkspaceJSON = getWorkspaceJSONfromProperties(workspaceProperties);
					if (SimpleMetaStoreUtil.isMetaFile(newWorkspaceHome, SimpleMetaStore.WORKSPACE)) {
						SimpleMetaStoreUtil.updateMetaFile(newWorkspaceHome, SimpleMetaStore.WORKSPACE, newWorkspaceJSON);
						File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(newWorkspaceHome, SimpleMetaStore.WORKSPACE);
						migrationLogPrint("Updated workspace MetaData file: " + userMetaFile.getAbsolutePath());
					} else {
						SimpleMetaStoreUtil.createMetaFile(newWorkspaceHome, SimpleMetaStore.WORKSPACE, newWorkspaceJSON);
						File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(newWorkspaceHome, SimpleMetaStore.WORKSPACE);
						migrationLogPrint("Created workspace MetaData file: " + userMetaFile.getAbsolutePath());
					}
				}
			}

			// delete empty orphaned folders under the metastore root
			String[] metaStoreRootfiles = metaStoreRootFolder.list();
			for (int i = 0; i < metaStoreRootfiles.length; i++) {
				File orgFolder = new File(metaStoreRootFolder, metaStoreRootfiles[i]);
				if (!orgFolder.isDirectory()) {
					continue;
				} else if (orgFolder.getName().equals(".metadata")) {
					// skip the eclipse workspace metadata folder
					continue;
				}
				String[] orgFolderFiles = orgFolder.list();
				for (int o = 0; o < orgFolderFiles.length; o++) {
					File userFolder = new File(orgFolder, orgFolderFiles[o]);
					if (!userFolder.isDirectory()) {
						continue;
					}
					String[] userFolderFiles = userFolder.list();
					for (int u = 0; u < userFolderFiles.length; u++) {
						File workspaceFolder = new File(userFolder, userFolderFiles[u]);
						if (!workspaceFolder.isDirectory()) {
							continue;
						}
						String[] workspaceFolderFiles = workspaceFolder.list();
						if (workspaceFolderFiles.length == 0) {
							workspaceFolder.delete();
							migrationLogPrint("Deleted empty workspace folder: " + workspaceFolder.getAbsolutePath());
						}
					}
					userFolderFiles = userFolder.list();
					if (userFolderFiles.length == 0) {
						userFolder.delete();
						migrationLogPrint("Deleted empty user folder: " + userFolder.getAbsolutePath());
					}
				}
				orgFolderFiles = orgFolder.list();
				if (orgFolderFiles.length == 0) {
					orgFolder.delete();
					migrationLogPrint("Deleted empty organization folder: " + orgFolder.getAbsolutePath());
				}
			}

			migrationLogClose();
			logger.info("Completed simple storage migration."); //$NON-NLS-1$
		} catch (JSONException e) {
			LogHelper.log(e);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		}
	}

	private Map<String, Map<String, String>> getMetadataFromSecureStorage(File orionSecureStorageFile) {
		ISecurePreferences storage = initSecurePreferences(orionSecureStorageFile);
		Map<String, Map<String, String>> metaData = new HashMap<String, Map<String, String>>();
		ISecurePreferences usersSecureStorage = storage.node("users");
		for (String uniqueId : usersSecureStorage.childrenNames()) {
			try {
				ISecurePreferences userProfileNode = usersSecureStorage.node(uniqueId);
				Map<String, String> propertyMap = new HashMap<String, String>();
				String login = userProfileNode.get("login", uniqueId);
				propertyMap.put("login", login);
				String name = userProfileNode.get("name", "");
				propertyMap.put("name", name);
				String password = userProfileNode.get("password", "");
				String encryptedPassword = SimpleUserPasswordUtil.encryptPassword(password);
				propertyMap.put("password", encryptedPassword);
				String email = userProfileNode.get("email", null);
				if (email != null) {
					propertyMap.put("email", email);
				}
				boolean blocked = userProfileNode.getBoolean("blocked", false);
				if (blocked) {
					propertyMap.put("blocked", "true");
				}
				String emailConfirmation = userProfileNode.get("email_confirmation", null);
				if (emailConfirmation != null) {
					propertyMap.put("email_confirmation", emailConfirmation);
				}
				ISecurePreferences generalUserProfile = userProfileNode.node("profile/general");
				if (generalUserProfile != null && generalUserProfile.get("lastlogintimestamp", null) != null) {
					String lastlogintimestamp = generalUserProfile.get("lastlogintimestamp", "");
					propertyMap.put("lastlogintimestamp", lastlogintimestamp);
				}
				if (generalUserProfile != null && generalUserProfile.get("GitName", null) != null) {
					String gitName = generalUserProfile.get("GitName", "");
					propertyMap.put("GitName", gitName);
				}
				if (generalUserProfile != null && generalUserProfile.get("GitMail", null) != null) {
					String gitMail = generalUserProfile.get("GitMail", "");
					propertyMap.put("GitMail", gitMail);
				}

				if (userProfileNode.nodeExists("properties")) {
					JSONObject profileProperties = new JSONObject();
					for (String key : userProfileNode.node("properties").keys()) {
						String value = userProfileNode.node("properties").get(key, null);
						profileProperties.put(key, value);

					}
					propertyMap.put("profileProperties", profileProperties.toString());
				}
				metaData.put(uniqueId, propertyMap);
			} catch (StorageException e) {
				migrationLogPrint("ERROR: StorageException reading user_store with userId: " + uniqueId);
			} catch (JSONException e) {
				migrationLogPrint("ERROR: JSONException reading user_store with userId: " + uniqueId);
			}
		}
		return metaData;
	}

	private ISecurePreferences initSecurePreferences(File orionSecureStorageFile) {
		try {
			ISecurePreferences storage = null;
			URL location = orionSecureStorageFile.toURI().toURL();
			if (location != null) {
				Map<String, Object> options = new HashMap<String, Object>();
				options.put(PROMPT_USER, Boolean.FALSE);
				String password = System.getProperty(ORION_STORAGE_PASSWORD, ""); //$NON-NLS-1$
				options.put(DEFAULT_PASSWORD, new PBEKeySpec(password.toCharArray()));
				try {
					storage = SecurePreferencesFactory.open(location, options);
				} catch (IOException e) {
					LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Error initializing user storage location", e)); //$NON-NLS-1$
				}
			} else {
				LogHelper.log(new Status(IStatus.WARNING, ServerConstants.PI_SERVER_CORE, "No instance location set. Storing user data in user home directory")); //$NON-NLS-1$
			}
			return storage;
		} catch (MalformedURLException e1) {
			LogHelper.log(e1);
		}
		return null;
	}

	/**
	 * Get a metadata list from the provided file containing properties. The metadata is stored in a Map
	 * with the key being the id and the value being a list of properties.
	 * @param file file containing properties.
	 * @return metadata list.
	 */
	private Map<String, Map<String, String>> getMetadataFromPropertiesFile(File file) {
		Map<String, Map<String, String>> metaData = new HashMap<String, Map<String, String>>();
		if (file.exists()) {
			Properties properties = getPropertiesFromFile(file);
			for (Object key : properties.keySet()) {
				String keyString = (String) key;
				if (keyString.equals("eclipse.preferences.version")) {
					continue;
				}
				String uniqueId = keyString.substring(0, keyString.indexOf("/"));
				String propertyKey = keyString.substring(keyString.indexOf("/") + 1);
				String propertyValue = properties.getProperty(keyString);
				if (metaData.containsKey(uniqueId)) {
					Map<String, String> propertyMap = metaData.get(uniqueId);
					propertyMap.put(propertyKey, propertyValue);
				} else {
					Map<String, String> propertyMap = new HashMap<String, String>();
					propertyMap.put(propertyKey, propertyValue);
					metaData.put(uniqueId, propertyMap);
				}
			}
		}
		return metaData;
	}

	/**
	 * Get all the properties from the provided file containing properties.
	 * @param file file containing properties.
	 * @return properties list.
	 */
	private Properties getPropertiesFromFile(File file) {
		Properties properties = new Properties();
		BufferedInputStream inStream;
		try {
			inStream = new BufferedInputStream(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			LogHelper.log(e);
			return null;
		}
		try {
			properties.load(inStream);
		} catch (IOException e) {
			LogHelper.log(e);
			return null;
		} finally {
			try {
				inStream.close();
			} catch (IOException e) {
				LogHelper.log(e);
				return null;
			}
		}
		return properties;
	}

	/**
	 * Create or update the root folder of the meta store. The result will be a root folder and a metastore.json.
	 * @return the root metadata folder.
	 * @throws IOException 
	 */
	private File createOrUpdateMetaStoreRoot(File metaStoreRootFolder) {
		try {
			if (!SimpleMetaStoreUtil.isMetaFile(metaStoreRootFolder, SimpleMetaStore.ROOT)) {
				JSONObject metaStoreRootJSON = new JSONObject();
				metaStoreRootJSON.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
				SimpleMetaStoreUtil.createMetaFile(metaStoreRootFolder, SimpleMetaStore.ROOT, metaStoreRootJSON);
				File rootMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(metaStoreRootFolder, SimpleMetaStore.ROOT);
				migrationLogPrint("Created root MetaData file: " + rootMetaFile.getAbsolutePath());
			}
			return metaStoreRootFolder;
		} catch (JSONException e) {
			LogHelper.log(e);
		}
		return null;
	}

	/**
	 * Create or update the metadata for a user. The result will be a user folder and a user.json.
	 * @param metaStoreRootFolder The root of the meta store.
	 * @param userId The userId of the user.
	 * @param userProperties properties for a user.
	 * @param usersSecureStorage secure storage properties for a user.
	 * @param sites list of site configuration properties.
	 * @param operations list of operations properties.
	 * @return the user metadata folder.
	 * @throws IOException 
	 */
	private File createOrUpdateUser(File metaStoreRootFolder, String userId, Map<String, String> userProperties, Map<String, Map<String, String>> usersSecureStorage, Map<String, Map<String, String>> sites, Map<String, Map<String, String>> operations) {
		String userName = userProperties.get("UserName");

		JSONObject newUserJSON = getUserJSONfromProperties(userProperties, usersSecureStorage, sites, operations);
		if (newUserJSON == null) {
			return null;
		}

		if (!SimpleMetaStoreUtil.isMetaUserFolder(metaStoreRootFolder, userName)) {
			SimpleMetaStoreUtil.createMetaUserFolder(metaStoreRootFolder, userName);
		}
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(metaStoreRootFolder, userName);

		if (SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER)) {
			SimpleMetaStoreUtil.updateMetaFile(userMetaFolder, SimpleMetaStore.USER, newUserJSON);
			File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, SimpleMetaStore.USER);
			migrationLogPrint("Updated user MetaData file: " + userMetaFile.getAbsolutePath());
		} else {
			SimpleMetaStoreUtil.createMetaFile(userMetaFolder, SimpleMetaStore.USER, newUserJSON);
			File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, SimpleMetaStore.USER);
			migrationLogPrint("Created user MetaData file: " + userMetaFile.getAbsolutePath());
		}

		return userMetaFolder;
	}

	private ITaskService getTaskService() {
		if (taskService == null) {
			BundleContext context = Activator.getDefault().getContext();
			if (taskServiceRef == null) {
				taskServiceRef = context.getServiceReference(ITaskService.class);
				if (taskServiceRef == null)
					throw new IllegalStateException("Task service not available");
			}
			taskService = context.getService(taskServiceRef);
			if (taskService == null)
				throw new IllegalStateException("Task service not available");
		}
		return taskService;
	}

	/**
	 * Get a user JSON object from the list of properties for a user. The site configuration and operations for a user also 
	 * are loaded into the JSON object.
	 * @param userProperties properties for a user.
	 * @param usersSecureStorage secure storage properties for a user.
	 * @param sites list of site configuration properties.
	 * @param operations list of operations properties.
	 * @return the JSON object (user.json).
	 */
	private JSONObject getUserJSONfromProperties(Map<String, String> userProperties, Map<String, Map<String, String>> usersSecureStorage, Map<String, Map<String, String>> sites, Map<String, Map<String, String>> operations) {
		try {
			JSONObject userJSON = new JSONObject();
			userJSON.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
			String oldUserId = userProperties.get("Id");
			String userName = userProperties.get("UserName");
			userJSON.put("UserName", userName);
			userJSON.put("UniqueId", userName);
			Map<String, String> usersSecureStorageProperties = usersSecureStorage.get(oldUserId);
			if (usersSecureStorageProperties == null) {
				// This is the Orion 0.2 storage format
				usersSecureStorageProperties = usersSecureStorage.get(userName);
			}
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("name")) {
				String secureStorageFullName = usersSecureStorageProperties.get("name");
				userJSON.put("FullName", secureStorageFullName);
			} else if (userProperties.containsKey("FullName")) {
				String fullName = usersSecureStorageProperties.get("FullName");
				userJSON.put("FullName", fullName);
			}
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("email")) {
				String secureStorageEmail = usersSecureStorageProperties.get("email");
				userJSON.put("email", secureStorageEmail);
			}
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("password")) {
				String secureStoragePassword = usersSecureStorageProperties.get("password");
				userJSON.put("password", secureStoragePassword);
			}
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("lastlogintimestamp")) {
				String lastlogintimestamp = usersSecureStorageProperties.get("lastlogintimestamp");
				userJSON.put("lastlogintimestamp", lastlogintimestamp);
			}
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("GitName")) {
				String gitName = usersSecureStorageProperties.get("GitName");
				userJSON.put("GitName", gitName);
			}
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("GitMail")) {
				String gitMail = usersSecureStorageProperties.get("GitMail");
				userJSON.put("GitMail", gitMail);
			}
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("blocked")) {
				String blocked = usersSecureStorageProperties.get("blocked");
				userJSON.put("blocked", blocked);
			}
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("email_confirmation")) {
				String email_confirmation = usersSecureStorageProperties.get("email_confirmation");
				userJSON.put("email_confirmation", email_confirmation);
			}
			userJSON.put("WorkspaceIds", new JSONArray());
			if (usersSecureStorageProperties != null && usersSecureStorageProperties.containsKey("profileProperties")) {
				String secureStorageProfileProperties = usersSecureStorageProperties.get("profileProperties");
				JSONObject jsonObject = new JSONObject(secureStorageProfileProperties);
				userJSON.put("profileProperties", jsonObject);
			}
			JSONObject properties = new JSONObject();
			for (String propertyKey : userProperties.keySet()) {
				String propertyValue = userProperties.get(propertyKey);
				if (propertyKey.equals("Id") || propertyKey.equals("UserName") || propertyKey.equals("Name") || propertyKey.equals("Guest")) {
					continue;
				} else if (propertyKey.equals("UserRights")) {
					JSONArray userRights = new JSONArray(propertyValue);
					for (int i = 0; i < userRights.length(); i++) {
						JSONObject userRight = userRights.getJSONObject(i);
						String uri = userRight.getString("Uri");
						if (uri.startsWith("/users/") && !uri.endsWith("*")) {
							// update the userId in the UserRight
							userRight.put("Uri", "/users/" + userName);
						} else if (uri.startsWith("/workspace/") && !uri.endsWith("*")) {
							// update the workspaceId in the UserRight
							userRight.put("Uri", "/workspace/" + userName + "-OrionContent");
						} else if (uri.startsWith("/workspace/") && uri.endsWith("*")) {
							// update the workspaceId in the UserRight
							userRight.put("Uri", "/workspace/" + userName + "-OrionContent/*");
						} else if (uri.startsWith("/file/") && !uri.endsWith("*")) {
							// update the workspaceId in the UserRight
							userRight.put("Uri", "/file/" + userName + "-OrionContent");
						} else if (uri.startsWith("/file/") && uri.endsWith("*")) {
							// update the workspaceId in the UserRight
							userRight.put("Uri", "/file/" + userName + "-OrionContent/*");
						} else if (!uri.equals("/users/*") && !uri.equals("/users")) {
							migrationLogPrint("Missed userRight: " + userRight);
						}
					}
					properties.put("UserRights", userRights);
				} else if (propertyKey.equals("Workspaces")) {
					JSONArray newWorkspacesJSON = new JSONArray();
					// Add the default workspace name here, only one workspace is used, ignore any old names
					newWorkspacesJSON.put(userName + "-OrionContent");
					userJSON.put("WorkspaceIds", newWorkspacesJSON);
				} else if (propertyKey.equals("UserRightsVersion")) {
					// Always use version 3 format of user rights
					properties.put("UserRightsVersion", "3");
				} else if (propertyKey.startsWith("SiteConfigurations")) {
					// value is a site id
					JSONObject siteConfigurations;
					if (properties.has("SiteConfigurations")) {
						siteConfigurations = properties.getJSONObject("SiteConfigurations");
					} else {
						siteConfigurations = new JSONObject();
					}
					// get the rest of the site configuration properties for this user
					JSONObject newSite = new JSONObject();
					Map<String, String> siteProperties = sites.get(propertyValue);
					// if siteProperties is null then this site is not in SiteConfigurations.conf and should be dropped.
					if (siteProperties != null) {
						for (String sitePropertyKey : siteProperties.keySet()) {
							String sitePropertyValue = siteProperties.get(sitePropertyKey);
							if (sitePropertyKey.equals("Workspace")) {
								newSite.put(sitePropertyKey, sitePropertyValue + "-OrionContent");
							} else if (sitePropertyKey.equals("Mappings")) {
								JSONArray mappings = new JSONArray(sitePropertyValue);
								for (int i = 0, size = mappings.length(); i < size; i++) {
									JSONObject mapping = mappings.getJSONObject(i);
									String target = mapping.getString("Target");
									if (target.startsWith("/" + userName + "/")) {
										// next need to correct the workspace id in the mapping
										target = target.replace("/" + userName + "/", "/" + userName + "-OrionContent/");
										mapping.put("Target", target);
										mappings.put(mapping);
									}
								}
								newSite.put(sitePropertyKey, mappings);
							} else {
								newSite.put(sitePropertyKey, sitePropertyValue);
							}
						}
						siteConfigurations.put(propertyValue, newSite);
						properties.put("SiteConfigurations", siteConfigurations);
					}
				} else if (propertyKey.startsWith("plugins//")) {
					// remove the extra slash from the plugins property
					propertyKey = propertyKey.replace("plugins//", "plugins/");
					properties.put(propertyKey, propertyValue);
				} else {
					// simple property
					if (propertyKey.startsWith("/")) {
						// remove the leading slash from the property
						properties.put(propertyKey.substring(1), propertyValue);
					} else {
						properties.put(propertyKey, propertyValue);
					}
				}
			}
			// get the operation properties for this user
			Map<String, String> operationProperties = operations.get(oldUserId);
			if (operationProperties != null) {
				ITaskService taskService = getTaskService();

				for (String operationPropertyKey : operationProperties.keySet()) {
					String operationPropertyValue = operationProperties.get(operationPropertyKey);
					String[] operation = operationPropertyKey.split("/");
					String taskId = operation[operation.length - 1];
					TaskInfo taskInfo = taskService.getTask(oldUserId, taskId, true);
					if (taskInfo == null) {
						migrationLogPrint("Deleted orphan operation that does not have a matching task: " + operationPropertyKey);
					} else {
						properties.put(operationPropertyKey, operationPropertyValue);
					}
				}
			}

			userJSON.put("Properties", properties);
			return userJSON;

		} catch (JSONException e) {
			LogHelper.log(e);
		}
		return null;
	}

	/**
	 * Get a workspace JSON object from the list of properties for a workspace.
	 * @param workspaceProperties properties for a workspace.
	 * @return the JSON object (workspace.json).
	 */
	private JSONObject getWorkspaceJSONfromProperties(Map<String, String> workspaceProperties) {
		try {
			JSONObject workspaceJSON = new JSONObject();
			workspaceJSON.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
			String userId = workspaceProperties.get("UserId");
			workspaceJSON.put("UniqueId", userId + "-OrionContent");
			workspaceJSON.put("FullName", "Orion Content");
			workspaceJSON.put("UserId", userId);
			workspaceJSON.put("Properties", new JSONObject());
			JSONArray projectNames = new JSONArray(workspaceProperties.get("ProjectNames"));
			workspaceJSON.put("ProjectNames", projectNames);
			return workspaceJSON;
		} catch (JSONException e) {
			LogHelper.log(e);
		}
		return null;
	}

	/**
	 * Get a project JSON object from the list of properties for a project.
	 * @param projectProperties properties for a project.
	 * @return the JSON object (project.json).
	 */
	private JSONObject getProjectJSONfromProperties(Map<String, String> projectProperties) {
		try {
			JSONObject projectJSON = new JSONObject();
			projectJSON.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
			String contentLocation = projectProperties.get("ContentLocation");
			String name = projectProperties.get("Name");
			projectJSON.put("UniqueId", SimpleMetaStoreUtil.encodeProjectIdFromProjectName(name));
			projectJSON.put("FullName", name);
			projectJSON.put("ContentLocation", contentLocation);
			projectJSON.put("Properties", new JSONObject());
			return projectJSON;
		} catch (JSONException e) {
			LogHelper.log(e);
		}
		return null;
	}

}
