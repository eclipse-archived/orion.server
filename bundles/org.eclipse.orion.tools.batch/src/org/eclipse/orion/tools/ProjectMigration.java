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
package org.eclipse.orion.tools;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.orion.tools.json.JSONArray;
import org.eclipse.orion.tools.json.JSONException;
import org.eclipse.orion.tools.json.JSONObject;

/**
 * Migrate the Orion metadata from the legacy metadata storage (Orion 3.0 CompatibilityMetaStore) to the new 
 * simple metadata storage (Orion 4.0 SimpleMetaStore).
 * 
 * @author Anthony Hunter
 */
public class ProjectMigration {
	private static final String METADATA_DIR = "/.metadata/.plugins/org.eclipse.orion.server.core/.settings/";
	private final static String ORION_METASTORE_VERSION = "OrionMetastoreVersion";
	private static String orionOperationsFile = null;
	private static String orionProjectPrefsFile = null;
	private static String orionSecureStorageFile = null;
	private static String orionSitesPrefsFile = null;
	private static String orionUserPrefsFile = null;
	private static String orionWorkspacePrefsFile = null;
	private static String orionWorkspaceRoot = null;

	private static final String SECURESTORAGE = "/.metadata/.plugins/org.eclipse.orion.server.user.securestorage/user_store";
	private final static int VERSION = 1;

	public static void main(String[] arguments) throws IOException, JSONException, URISyntaxException {
		new ProjectMigration(arguments).run();
	}

	/**
	 * Retrieve the folder with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the folder.
	 * @return The folder.
	 */
	private static File retrieveMetaFolder(File parent, String name) {
		return new File(parent, name);
	}

	private PrintStream migrationLog;

	/**
	 * Constructor for a new ProjectMigration
	 * @param arguments command line arguments.
	 */
	public ProjectMigration(String[] arguments) {
		parseArgs(arguments);
	}

	/**
	 * Create a new MetaFile with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @param jsonObject The JSON containing the data to save in the MetaFile.
	 * @param message String to add to the message logged.
	 * @return true if the creation was successful.
	 */
	private boolean createMetaFile(File parent, String name, JSONObject jsonObject, String message) {
		try {
			if (isMetaFile(parent, name)) {
				throw new RuntimeException("Meta File Error, already exists, use update");
			}
			if (!parent.exists()) {
				throw new RuntimeException("Meta File Error, parent folder does not exist");
			}
			if (!parent.isDirectory()) {
				throw new RuntimeException("Meta File Error, parent is not a folder");
			}
			File newFile = retrieveMetaFile(parent, name);
			FileWriter fileWriter = new FileWriter(newFile);
			fileWriter.write(jsonObject.toString(4));
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
			migrationLogPrint("Created " + message + " file: " + newFile.getAbsolutePath());
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, file IO error", e);
		} catch (JSONException e) {
			throw new RuntimeException("Meta File Error, JSON error", e);
		}
		return true;
	}

	/**
	 * Create a new folder with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the folder.
	 * @return true if the creation was successful.
	 */
	private boolean createMetaFolder(File parent, String name, String message) {
		if (!parent.exists()) {
			throw new RuntimeException("Meta File Error, parent folder does not exist");
		}
		if (!parent.isDirectory()) {
			throw new RuntimeException("Meta File Error, parent is not a folder");
		}
		File newFolder = new File(parent, name);
		if (newFolder.exists()) {
			throw new RuntimeException("Meta File Error, folder already exists");
		}
		if (!newFolder.mkdir()) {
			throw new RuntimeException("Meta File Error, cannot create folder");
		}
		migrationLogPrint("Created " + message + " folder: " + newFolder.getAbsolutePath());
		return true;
	}

	/**
	 * Create a new user folder with the provided name under the provided parent folder.
	 * The file layout settings are consulted to determine if a flat layout or a usertree layout is used. 
	 * @param parent the parent folder.
	 * @param userName the user name.
	 * @return true if the creation was successful.
	 */
	private boolean createMetaUserFolder(File parent, String userName) {
		if (!parent.exists()) {
			throw new RuntimeException("Meta File Error, parent folder does not exist");
		}
		if (!parent.isDirectory()) {
			throw new RuntimeException("Meta File Error, parent is not a folder");
		}

		//the user-tree layout organises projects by the user who created it: metastore/an/anthony
		String userPrefix = userName.substring(0, Math.min(2, userName.length()));
		File orgFolder = new File(parent, userPrefix);
		if (!orgFolder.exists()) {
			if (!orgFolder.mkdir()) {
				throw new RuntimeException("Meta File Error, cannot create folder");
			}
		}
		return createMetaFolder(orgFolder, userName, "User");
	}

	/**
	 * Create or update the root folder of the meta store. The result will be a root folder and a metastore.json.
	 * @return the root metadata folder.
	 * @throws IOException 
	 */
	private File createOrUpdateMetaStoreRoot() throws IOException {
		File metaStoreRootFolder = new File(orionWorkspaceRoot);
		if (!isMetaFile(metaStoreRootFolder, "metastore")) {
			JSONObject metaStoreRootJSON = new JSONObject();
			metaStoreRootJSON.put(ORION_METASTORE_VERSION, VERSION);
			createMetaFile(metaStoreRootFolder, "metastore", metaStoreRootJSON, "root MetaData");
		}
		return metaStoreRootFolder;
	}

	/**
	 * Create or update the metadata for a user. The result will be a user folder and a user.json.
	 * @param metaStoreRootFolder The root of the meta store.
	 * @param userId The userId of the user.
	 * @param userProperties properties for a user.
	 * @param sites list of site configuration properties.
	 * @param operations list of operations properties.
	 * @return the user metadata folder.
	 * @throws IOException 
	 */
	private File createOrUpdateUser(File metaStoreRootFolder, String userId, Map<String, String> userProperties, Map<String, Map<String, String>> sites, Map<String, Map<String, String>> operations) throws IOException {
		String userName = userProperties.get("UserName");

		if (!isMetaUserFolder(metaStoreRootFolder, userName)) {
			createMetaUserFolder(metaStoreRootFolder, userName);
		}
		File userMetaFolder = readMetaUserFolder(metaStoreRootFolder, userName);

		JSONObject newUserJSON = getUserJSONfromProperties(userProperties, sites, operations);
		if (isMetaFile(userMetaFolder, "user")) {
			updateMetaFile(userMetaFolder, "user", newUserJSON, "user MetaData");
		} else {
			createMetaFile(userMetaFolder, "user", newUserJSON, "user MetaData");
		}

		return userMetaFolder;
	}

	/**
	 * Get a metadata list from the provided file containing properties. The metadata is stored in a Map
	 * with the key being the id and the value being a list of properties.
	 * @param file file containing properties.
	 * @return metadata list.
	 */
	private Map<String, Map<String, String>> getMetadataFromFile(String file) {
		Map<String, Map<String, String>> metaData = new HashMap<String, Map<String, String>>();
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
		return metaData;
	}

	/**
	 * Get a project JSON object from the list of properties for a project.
	 * @param projectProperties properties for a project.
	 * @return the JSON object (project.json).
	 */
	private JSONObject getProjectJSONfromProperties(Map<String, String> projectProperties) {
		JSONObject projectJSON = new JSONObject();
		projectJSON.put(ORION_METASTORE_VERSION, VERSION);
		String contentLocation = projectProperties.get("ContentLocation");
		String name = projectProperties.get("Name");
		projectJSON.put("UniqueId", name);
		projectJSON.put("FullName", name);
		projectJSON.put("ContentLocation", contentLocation);
		projectJSON.put("Properties", new JSONObject());
		return projectJSON;
	}

	/**
	 * Get all the properties from the provided file containing properties.
	 * @param file file containing properties.
	 * @return properties list.
	 */
	private Properties getPropertiesFromFile(String file) {
		Properties properties = new Properties();
		BufferedInputStream inStream;
		try {
			inStream = new BufferedInputStream(new FileInputStream(new File(file)));
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
	 * Get a metadata list from the provided secure storage file containing properties. The metadata is stored in a Map
	 * with the key being the id and the value being a list of properties.
	 * @param file secure storage file containing properties.
	 * @return metadata list.
	 * @throws IOException 
	 */
	private Map<String, Map<String, String>> getSecureStorageMetadataFromFile(String file) throws IOException {
		Map<String, Map<String, String>> metaData = new HashMap<String, Map<String, String>>();

		// read the secure storage file
		FileReader fileReader = new FileReader(file);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {

			//process a line in the file
			if (line.startsWith("#") && line.indexOf("/") == -1) {
				// Skip these lines:
				// #Equinox secure storage version 1.0
				// #Fri Sep 06 12:42:13 EDT 2013
				continue;
			}
			if (line.startsWith("org.eclipse.equinox.security.preferences")) {
				// Skip these lines:
				// org.eclipse.equinox.security.preferences.cipher=PBEWithMD5AndDES
				// org.eclipse.equinox.security.preferences.keyFactory=PBEWithMD5AndDES
				// org.eclipse.equinox.security.preferences.version=1
				continue;
			}
			String keyString = line.substring(0, line.indexOf("="));
			String propertyValue = line.substring(line.indexOf("=") + 1);
			String userString = keyString.replace("/users/", "");
			String uniqueId = userString.substring(0, userString.indexOf("/"));
			String propertyKey = userString.substring(userString.indexOf("/") + 1);
			if (metaData.containsKey(uniqueId)) {
				Map<String, String> propertyMap = metaData.get(uniqueId);
				propertyMap.put(propertyKey, propertyValue);
			} else {
				Map<String, String> propertyMap = new HashMap<String, String>();
				propertyMap.put(propertyKey, propertyValue);
				metaData.put(uniqueId, propertyMap);
			}
		}
		bufferedReader.close();
		return metaData;
	}

	/**
	 * Get a user JSON object from the list of properties for a user. The site configuration and operations for a user also 
	 * are loaded into the JSON object.
	 * @param userProperties properties for a user.
	 * @param sites list of site configuration properties.
	 * @param operations list of operations properties.
	 * @return the JSON object (user.json).
	 */
	private JSONObject getUserJSONfromProperties(Map<String, String> userProperties, Map<String, Map<String, String>> sites, Map<String, Map<String, String>> operations) {
		JSONObject userJSON = new JSONObject();
		userJSON.put(ORION_METASTORE_VERSION, VERSION);
		String oldUserId = userProperties.get("Id");
		String userName = userProperties.get("UserName");
		userJSON.put("UniqueId", userName);
		userJSON.put("UserName", userName);
		String fullName = userProperties.get("Name");
		userJSON.put("FullName", fullName);
		userJSON.put("WorkspaceIds", new JSONArray());
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
				for (String sitePropertyKey : siteProperties.keySet()) {
					String sitePropertyValue = siteProperties.get(sitePropertyKey);
					if (sitePropertyKey.equals("Workspace")) {
						newSite.put(sitePropertyKey, sitePropertyValue + "-OrionContent");
					} else {
						newSite.put(sitePropertyKey, sitePropertyValue);
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
	}

	/**
	 * Get a workspace JSON object from the list of properties for a workspace.
	 * @param workspaceProperties properties for a workspace.
	 * @return the JSON object (workspace.json).
	 */
	private JSONObject getWorkspaceJSONfromProperties(Map<String, String> workspaceProperties) {
		JSONObject workspaceJSON = new JSONObject();
		workspaceJSON.put(ORION_METASTORE_VERSION, VERSION);
		String name = workspaceProperties.get("Name");
		String id = workspaceProperties.get("Id");
		workspaceJSON.put("UniqueId", id + "-" + name.replace(" ", "").replace("#", ""));
		workspaceJSON.put("FullName", name);
		workspaceJSON.put("UserId", id);
		workspaceJSON.put("Properties", new JSONObject());
		JSONArray projectNames = new JSONArray(workspaceProperties.get("ProjectNames"));
		workspaceJSON.put("ProjectNames", projectNames);
		return workspaceJSON;
	}

	/**
	 * Determine if the provided name is a MetaFile under the provided parent folder.
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return true if the name is a MetaFile.
	 */
	private boolean isMetaFile(File parent, String name) {
		if (!parent.exists()) {
			return false;
		}
		if (!parent.isDirectory()) {
			return false;
		}
		File savedFile = retrieveMetaFile(parent, name);
		if (!savedFile.exists()) {
			return false;
		}
		if (!savedFile.isFile()) {
			return false;
		}
		return true;
	}

	/**
	 * Determine if the provided parent folder contains a MetaFile with the provided name
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return true if the parent is a folder with a MetaFile.
	 */
	private boolean isMetaFolder(File parent, String name) {
		if (!parent.exists()) {
			return false;
		}
		if (!parent.isDirectory()) {
			return false;
		}
		File savedFolder = retrieveMetaFolder(parent, name);
		if (!savedFolder.exists()) {
			return false;
		}
		if (!savedFolder.isDirectory()) {
			return false;
		}
		return true;
	}

	/**
	 * Determine if the provided user name is a MetaFolder under the provided parent folder.
	 * The file layout settings are consulted to determine if a flat layout or a usertree layout is used. 
	 * @param parent the parent folder.
	 * @param userName the user name.
	 * @return true if the parent is a folder with a user MetaFile.
	 */
	private boolean isMetaUserFolder(File parent, String userName) {
		if (!parent.exists()) {
			throw new RuntimeException("Meta File Error, parent folder does not exist");
		}
		if (!parent.isDirectory()) {
			throw new RuntimeException("Meta File Error, parent is not a folder");
		}

		//the user-tree layout organises projects by the user who created it: metastore/an/anthony
		String userPrefix = userName.substring(0, Math.min(2, userName.length()));
		File orgFolder = new File(parent, userPrefix);
		if (!orgFolder.exists()) {
			return false;
		}
		return isMetaFolder(orgFolder, userName);
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
	private void migrationLogOpen(File workspaceRoot) throws FileNotFoundException {
		File metadataFolder = new File(workspaceRoot, ".metadata");
		File logFile = new File(metadataFolder, "migration.log");
		FileOutputStream stream = new FileOutputStream(logFile);
		System.err.println("ProjectMigration: migration log is at " + logFile.getAbsolutePath());
		migrationLog = new PrintStream(stream);
	}

	/**
	 * Print a message to the migration log.
	 * @param message the message.
	 */
	private void migrationLogPrint(String message) {
		migrationLog.println(message);
	}

	/**
	 * Parse the project migration application command line arguments.
	 * @param arguments command line arguments.
	 * @return true if the command line arguments are acceptable.
	 */
	private void parseArgs(String[] arguments) {
		if (arguments.length == 0) {
			usage();
		}
		for (int i = 0; i < arguments.length; i++) {
			if ("-root".equals(arguments[i])) {
				//the workspace root to migrate
				orionWorkspaceRoot = arguments[++i];
			} else {
				usage();
			}
		}
		if (orionWorkspaceRoot == null) {
			usage();
		}
		orionProjectPrefsFile = orionWorkspaceRoot + METADATA_DIR + "Projects.prefs";
		orionWorkspacePrefsFile = orionWorkspaceRoot + METADATA_DIR + "Workspaces.prefs";
		orionUserPrefsFile = orionWorkspaceRoot + METADATA_DIR + "Users.prefs";
		orionSitesPrefsFile = orionWorkspaceRoot + METADATA_DIR + "SiteConfigurations.prefs";
		orionOperationsFile = orionWorkspaceRoot + METADATA_DIR + "Operations.prefs";
		orionSecureStorageFile = orionWorkspaceRoot + SECURESTORAGE;
	}

	/**
	 * Get the user folder with the provided name under the provided parent folder.
	 * The file layout settings are consulted to determine if a flat layout or a usertree layout is used. 
	 * @param parent the parent folder.
	 * @param userName the user name.
	 * @return the folder.
	 */
	private File readMetaUserFolder(File parent, String userName) {
		//the user-tree layout organises projects by the user who created it: metastore/an/anthony
		String userPrefix = userName.substring(0, Math.min(2, userName.length()));
		File orgFolder = new File(parent, userPrefix);
		return new File(orgFolder, userName);
	}

	/**
	 * Retrieve the MetaFile with the provided name under the parent folder.
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return The MetaFile.
	 */
	private File retrieveMetaFile(File parent, String name) {
		return new File(parent, name + ".json");
	}

	/**
	 * Executes the project migration.
	 * @throws IOException 
	 * @throws JSONException 
	 * @throws URISyntaxException 
	 */
	public void run() throws IOException, JSONException, URISyntaxException {
		File workspaceRoot = new File(orionWorkspaceRoot);
		migrationLogOpen(workspaceRoot);
		migrationLogPrint("Migrating MetaStore from legacy to simple.");

		Map<String, Map<String, String>> users = getMetadataFromFile(orionUserPrefsFile);
		Map<String, Map<String, String>> workspaces = getMetadataFromFile(orionWorkspacePrefsFile);
		Map<String, Map<String, String>> projects = getMetadataFromFile(orionProjectPrefsFile);
		Map<String, Map<String, String>> sites = getMetadataFromFile(orionSitesPrefsFile);
		Map<String, Map<String, String>> operations = getMetadataFromFile(orionOperationsFile);

		File metaStoreRootFolder = createOrUpdateMetaStoreRoot();
		Map<String, String> secureStoreUsers = new HashMap<String, String>();

		int userSize = users.size();
		int userCount = 1;
		for (String userId : users.keySet()) {
			Map<String, String> userProperties = users.get(userId);
			String userName = userProperties.get("UserName");
			migrationLogPrint("Processing UserId " + userId + " (" + userCount++ + " of " + userSize + ") UserName " + userName);
			secureStoreUsers.put(userId, userName);

			File newUserHome = createOrUpdateUser(metaStoreRootFolder, userId, userProperties, sites, operations);

			String userWorkspaceProperty = userProperties.get("Workspaces");
			if (userWorkspaceProperty == null) {
				migrationLogPrint("User " + userName + " has no workspaces.");
			} else {
				JSONObject workspaceObject = new JSONArray(userWorkspaceProperty).getJSONObject(0);
				String workspaceId = workspaceObject.getString("Id");
				Map<String, String> workspaceProperties = workspaces.get(workspaceId);
				String encodedWorkspaceName = workspaceProperties.get("Name").replace(" ", "").replace("#", "");
				if (!isMetaFolder(newUserHome, encodedWorkspaceName)) {
					createMetaFolder(newUserHome, encodedWorkspaceName, "Workspace");
				}
				File newWorkspaceHome = retrieveMetaFolder(newUserHome, encodedWorkspaceName);

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
						URI contentLocation = new URI(newProjectJSON.getString("ContentLocation"));
						if (contentLocation.getScheme().equals("file")) {
							File oldContentLocation = new File(contentLocation);
							if (oldContentLocation.toString().startsWith(workspaceRoot.toString())) {
								File newContentLocation = retrieveMetaFolder(newWorkspaceHome, projectName);
								newProjectJSON.put("ContentLocation", newContentLocation.toURI());
								oldContentLocation.renameTo(newContentLocation);
								migrationLogPrint("Moved Project folder: " + oldContentLocation.getAbsolutePath() + " to: " + newContentLocation.getAbsolutePath());
							}
						}

						// Add the WorkspaceId to the projectJSON
						String name = workspaceProperties.get("Name");
						String id = workspaceProperties.get("Id");
						newProjectJSON.put("WorkspaceId", id + "-" + name.replace(" ", "").replace("#", ""));

						// save projectJSON
						if (isMetaFile(newWorkspaceHome, projectName)) {
							updateMetaFile(newWorkspaceHome, projectName, newProjectJSON, "project MetaData");
						} else {
							createMetaFile(newWorkspaceHome, projectName, newProjectJSON, "project MetaData");
						}
					}

					workspaceProperties.put("ProjectNames", projectNamesJSON.toString());
				}

				JSONObject newWorkspaceJSON = getWorkspaceJSONfromProperties(workspaceProperties);
				if (isMetaFile(newWorkspaceHome, "workspace")) {
					updateMetaFile(newWorkspaceHome, "workspace", newWorkspaceJSON, "workspace MetaData");
				} else {
					createMetaFile(newWorkspaceHome, "workspace", newWorkspaceJSON, "workspace MetaData");
				}

			}
		}

		// handle the secure storage
		Map<String, Map<String, String>> secureStore = getSecureStorageMetadataFromFile(orionSecureStorageFile);
		writeSecureStorage(secureStore, secureStoreUsers);
		migrationLogClose();
	}

	/**
	 * Update the existing MetaFile with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @param jsonObject The JSON containing the data to update in the MetaFile.
	 * @param message String to add to the message logged.
	 * @return
	 */
	private boolean updateMetaFile(File parent, String name, JSONObject jsonObject, String message) {
		try {
			if (!isMetaFile(parent, name)) {
				throw new RuntimeException("Meta File Error, cannot update, does not exist.");
			}
			File savedFile = retrieveMetaFile(parent, name);

			FileWriter fileWriter = new FileWriter(savedFile);
			fileWriter.write(jsonObject.toString(4));
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
			migrationLogPrint("Updated existing " + message + " file: " + savedFile.getAbsolutePath());
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, file IO error", e);
		} catch (JSONException e) {
			throw new RuntimeException("Meta File Error, JSON error", e);
		}
		return true;
	}

	/**
	 * Print the usage and exit;
	 */
	private void usage() {
		System.err.println("ProjectMigration: Usage: java -jar ProjectMigration.jar -root folder");
		System.exit(1);
	}

	private void writeSecureStorage(Map<String, Map<String, String>> secureStore, Map<String, String> secureStoreUsers) throws IOException {
		migrationLogPrint("Processing Secure Storage");
		FileWriter fileWriter = new FileWriter(orionSecureStorageFile);
		fileWriter.write("#Equinox secure storage version 1.0");
		fileWriter.write("#Fri Sep 06 12:42:13 EDT 2013");
		fileWriter.write("org.eclipse.equinox.security.preferences.cipher=PBEWithMD5AndDES");
		fileWriter.write("org.eclipse.equinox.security.preferences.keyFactory=PBEWithMD5AndDES");
		fileWriter.write("org.eclipse.equinox.security.preferences.version=1");

		for (String userId : secureStore.keySet()) {
			String userName = secureStoreUsers.get(userId);
			migrationLogPrint("Processing UserId " + userId + " change to " + userName);
			Map<String, String> properties = secureStore.get(userId);
			for (String key : properties.keySet()) {
				String value = properties.get(key);
				String newKey = "/users/" + userName + "/" + key;
				String newLine = newKey + "=" + value;
				migrationLogPrint(newLine);
				fileWriter.write(newLine);
				fileWriter.write("\n");
			}
		}
		fileWriter.flush();
		fileWriter.close();
	}

}
