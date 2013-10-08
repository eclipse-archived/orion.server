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
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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

	public boolean isMigrationRequired(File rootLocation) {
		File secureStorage = new File(rootLocation, SECURESTORAGE);
		if (secureStorage.exists()) {
			// The secure storage file exists, so migration is required
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
	}

	public void doMigration(File rootLocation) {
		try {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.info("Starting simple storage migration."); //$NON-NLS-1$
			migrationLogOpen(rootLocation);
			migrationLogPrint("Migrating MetaStore from legacy to simple.");

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
				String userName = userProperties.get("UserName");
				if (userName.equals(userId) && userName.length() <= 2) {
					// the userId and userName are both A in the users.pref, use the login value in the secure store.
					Map<String, String> usersSecureStorageProperties = usersSecureStorage.get(userId);
					String login = usersSecureStorageProperties.get("login");
					userName = login;
					userProperties.put("UserName", login);
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
					String encodedWorkspaceName = workspaceProperties.get("Name").replace(" ", "").replace("#", "");
					if (!SimpleMetaStoreUtil.isMetaFolder(newUserHome, encodedWorkspaceName)) {
						SimpleMetaStoreUtil.createMetaFolder(newUserHome, encodedWorkspaceName);
						migrationLogPrint("Created Workspace folder: " + SimpleMetaStoreUtil.readMetaFolder(newUserHome, encodedWorkspaceName).getAbsolutePath());
					}
					File newWorkspaceHome = SimpleMetaStoreUtil.readMetaFolder(newUserHome, encodedWorkspaceName);

					String workspaceProjectProperty = workspaceProperties.get("Projects");
					if (workspaceProjectProperty == null || workspaceProjectProperty.equals("[]")) {
						migrationLogPrint("User " + userName + " has no projects.");
						workspaceProperties.put("ProjectNames", "[]");
					} else {
						JSONArray workspaceProjectsArray = new JSONArray(workspaceProjectProperty);
						JSONArray projectNamesJSON = new JSONArray();
						for (int i = 0; i < workspaceProjectsArray.length(); i++) {
							JSONObject projectObject = workspaceProjectsArray.getJSONObject(i);
							String projectId = projectObject.getString("Id");
							Map<String, String> projectProperties = projects.get(projectId);
							String projectName = projectProperties.get("Name");
							projectNamesJSON.put(projectName);
							JSONObject newProjectJSON = getProjectJSONfromProperties(projectProperties);

							// if the content location is local, update the content location with the new name
							if (newProjectJSON.has("ContentLocation")) {
								URI contentLocation = new URI(newProjectJSON.getString("ContentLocation"));
								if (contentLocation.getScheme().equals("file")) {
									File oldContentLocation = new File(contentLocation);
									if (oldContentLocation.toString().startsWith(rootLocation.toString())) {
										File newContentLocation = SimpleMetaStoreUtil.retrieveMetaFolder(newWorkspaceHome, projectName);
										newProjectJSON.put("ContentLocation", newContentLocation.toURI());
										oldContentLocation.renameTo(newContentLocation);
										migrationLogPrint("Moved Project folder: " + oldContentLocation.getAbsolutePath() + " to: " + newContentLocation.getAbsolutePath());
										// delete the old folder if it is empty
										File orgFolder = oldContentLocation.getParentFile();
										String[] files = orgFolder.list();
										if (files.length == 0) {
											orgFolder.delete();
											migrationLogPrint("Deleted empty parent folder: " + orgFolder.getAbsolutePath());
											orgFolder = orgFolder.getParentFile();
											files = orgFolder.list();
											if (files.length == 0) {
												orgFolder.delete();
												migrationLogPrint("Deleted empty parent folder: " + orgFolder.getAbsolutePath());
											}
										}
									}
								}
							}

							// Add the WorkspaceId to the projectJSON
							String name = workspaceProperties.get("Name");
							String id = workspaceProperties.get("Id");
							newProjectJSON.put("WorkspaceId", id + "-" + name.replace(" ", "").replace("#", ""));

							// save projectJSON
							if (SimpleMetaStoreUtil.isMetaFile(newWorkspaceHome, projectName)) {
								SimpleMetaStoreUtil.updateMetaFile(newWorkspaceHome, projectName, newProjectJSON);
								File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(newWorkspaceHome, projectName);
								migrationLogPrint("Updated project MetaData file: " + userMetaFile.getAbsolutePath());
							} else {
								SimpleMetaStoreUtil.createMetaFile(newWorkspaceHome, projectName, newProjectJSON);
								File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(newWorkspaceHome, projectName);
								migrationLogPrint("Created project MetaData file: " + userMetaFile.getAbsolutePath());
							}
						}

						workspaceProperties.put("ProjectNames", projectNamesJSON.toString());
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

			migrationLogClose();
			logger.info("Completed simple storage migration."); //$NON-NLS-1$
		} catch (JSONException e) {
			LogHelper.log(e);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		}
	}

	private Map<String, Map<String, String>> getMetadataFromSecureStorage(File orionSecureStorageFile) {
		try {
			ISecurePreferences storage = initSecurePreferences(orionSecureStorageFile);
			Map<String, Map<String, String>> metaData = new HashMap<String, Map<String, String>>();
			ISecurePreferences usersSecureStorage = storage.node("users");
			for (String uniqueId : usersSecureStorage.childrenNames()) {
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

				if (userProfileNode.nodeExists("properties")) {
					JSONObject profileProperties = new JSONObject();
					for (String key : userProfileNode.node("properties").keys()) {
						String value = userProfileNode.node("properties").get(key, null);
						profileProperties.put(key, value);

					}
					propertyMap.put("profileProperties", profileProperties.toString());
				}
				metaData.put(uniqueId, propertyMap);
			}
			return metaData;
		} catch (StorageException e) {
			LogHelper.log(e);
		} catch (JSONException e) {
			LogHelper.log(e);
		}
		return null;
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
			e.printStackTrace();
			return null;
		}
		try {
			properties.load(inStream);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				inStream.close();
			} catch (IOException e) {
				e.printStackTrace();
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
				// There is no secure storage entry, so return null
				return null;
			}
			String secureStorageFullName = usersSecureStorageProperties.get("name");
			userJSON.put("FullName", secureStorageFullName);
			String secureStorageEmail = usersSecureStorageProperties.get("email");
			userJSON.put("email", secureStorageEmail);
			String secureStoragePassword = usersSecureStorageProperties.get("password");
			userJSON.put("password", secureStoragePassword);
			userJSON.put("WorkspaceIds", new JSONArray());
			if (usersSecureStorageProperties.containsKey("profileProperties")) {
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
						if (uri.equals("/users/" + oldUserId)) {
							// update the userId in the UserRight
							userRight.put("Uri", "/users/" + userName);
						} else if (uri.equals("/workspace/" + userName)) {
							// update the workspaceId in the UserRight
							userRight.put("Uri", "/workspace/" + userName + "-OrionContent");
						} else if (uri.equals("/workspace/" + userName + "/*")) {
							// update the workspaceId in the UserRight
							userRight.put("Uri", "/workspace/" + userName + "-OrionContent/*");
						} else if (uri.equals("/file/" + userName)) {
							// update the workspaceId in the UserRight
							userRight.put("Uri", "/file/" + userName + "-OrionContent");
						} else if (uri.equals("/file/" + userName + "/*")) {
							// update the workspaceId in the UserRight
							userRight.put("Uri", "/file/" + userName + "-OrionContent/*");
						}
					}
					properties.put("UserRights", userRights);
				} else if (propertyKey.equals("Workspaces")) {
					JSONArray currentWorkspacesJSON = new JSONArray(propertyValue);
					JSONArray newWorkspacesJSON = new JSONArray();
					for (int i = 0; i < currentWorkspacesJSON.length(); i++) {
						// legacy workspace property looks like [{"Id"\:"anthony","LastModified"\:1351775348697}]
						JSONObject object = currentWorkspacesJSON.getJSONObject(i);
						String workspaceId = object.getString("Id");
						if (workspaceId != null) {
							// Add the default workspace name here
							newWorkspacesJSON.put(workspaceId + "-OrionContent");
							break;
						}
					}
					userJSON.put("WorkspaceIds", newWorkspacesJSON);
				} else if (propertyKey.equals("UserRightsVersion")) {
					properties.put("UserRightsVersion", propertyValue);
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
					if (siteProperties != null) {
						for (String sitePropertyKey : siteProperties.keySet()) {
							String sitePropertyValue = siteProperties.get(sitePropertyKey);
							if (sitePropertyKey.equals("Workspace")) {
								newSite.put(sitePropertyKey, sitePropertyValue + "-OrionContent");
							} else {
								newSite.put(sitePropertyKey, sitePropertyValue);
							}
						}
					}
					siteConfigurations.put(propertyValue, newSite);
					properties.put("SiteConfigurations", siteConfigurations);
				} else {
					// simple property
					properties.put(propertyKey, propertyValue);
				}
			}
			// get the operation properties for this user
			Map<String, String> operationProperties = operations.get(oldUserId);
			if (operationProperties != null) {
				for (String operationPropertyKey : operationProperties.keySet()) {
					String operationPropertyValue = operationProperties.get(operationPropertyKey);
					properties.put(operationPropertyKey, operationPropertyValue);
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
			String name = workspaceProperties.get("Name");
			String id = workspaceProperties.get("Id");
			workspaceJSON.put("UniqueId", id + "-" + name.replace(" ", "").replace("#", ""));
			workspaceJSON.put("FullName", name);
			workspaceJSON.put("UserId", id);
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
			projectJSON.put("UniqueId", name);
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
