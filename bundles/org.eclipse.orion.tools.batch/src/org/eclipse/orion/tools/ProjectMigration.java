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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
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
	private static boolean orionFileLayoutUsertree = false;
	private static String orionOperationsFile = null;
	private static String orionProjectPrefsFile = null;
	private static String orionSitesPrefsFile = null;
	private static String orionUserPrefsFile = null;
	private static String orionWorkspacePrefsFile = null;

	private static String orionWorkspaceRoot = null;
	private final static int VERSION = 1;

	public static void main(String[] arguments) throws IOException, JSONException {
		new ProjectMigration(arguments).run();
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
	 * Creates a folder, handling the case that the folder may already be there.
	 * @param newFolder The folder to create
	 * @param message String to add to the message logged.
	 */
	private void createFolder(File newFolder, String message) {
		if (!newFolder.isDirectory() || !newFolder.exists()) {
			newFolder.mkdirs();
			migrationLogPrint("Created " + message + " folder: " + newFolder.getAbsolutePath());
		} else {
			migrationLogPrint(message + " folder exists: " + newFolder.getAbsolutePath());
		}
	}

	/**
	 * Create or update the meta file (file.json) with the provided JSON object. 
	 * @param metaFile The folder to contain the meta file.
	 * @param jsonObject The JSON object.
	 * @param message String to add to the message logged.
	 */
	private void createOrUpdateMetaFile(File metaFile, JSONObject jsonObject, String message) {
		if (metaFile.exists()) {
			migrationLogPrint("Updated existing " + message + " file: " + metaFile.getAbsolutePath());
		} else {
			migrationLogPrint("Created " + message + " file: " + metaFile.getAbsolutePath());
		}
		writeMetaFile(metaFile, jsonObject);
	}

	/**
	 * Create or update the root folder of the meta store. The result will be a root folder and a metastore.json.
	 * @return the root metadata folder.
	 */
	private File createOrUpdateMetaStoreRoot() {
		File metaStoreRootFolder = new File(orionWorkspaceRoot + File.separator + "metastore");
		createFolder(metaStoreRootFolder, "Root");
		File metaStoreRootFile = new File(metaStoreRootFolder, "metastore.json");
		JSONObject metaStoreRootJSON = new JSONObject();
		metaStoreRootJSON.put(ORION_METASTORE_VERSION, VERSION);
		createOrUpdateMetaFile(metaStoreRootFile, metaStoreRootJSON, "root MetaData");
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
	 */
	private File createOrUpdateUser(File metaStoreRootFolder, String userId, Map<String, String> userProperties, Map<String, Map<String, String>> sites, Map<String, Map<String, String>> operations) {
		String userName = userProperties.get("UserName");

		File newUserHome = new File(metaStoreRootFolder + File.separator + userName);
		if (orionFileLayoutUsertree) {
			String newUserPrefix = userName.substring(0, Math.min(2, userName.length()));
			newUserHome = new File(metaStoreRootFolder + File.separator + newUserPrefix + File.separator + userName);
		}
		createFolder(newUserHome, "User");

		JSONObject newUserJSON = getUserJSONfromProperties(userProperties, sites, operations);
		File newUserMetaFile = new File(newUserHome, "user.json");
		createOrUpdateMetaFile(newUserMetaFile, newUserJSON, "user MetaData");

		return newUserHome;
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
			if (key.equals("eclipse.preferences.version")) {
				continue;
			}
			String keyString = (String) key;
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
		String id = projectProperties.get("Id");
		projectJSON.put("UniqueId", id + "-" + name);
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
		workspaceJSON.put("UniqueId", id + "-" + name);
		workspaceJSON.put("FullName", name);
		workspaceJSON.put("Properties", new JSONObject());
		JSONArray projectNames = new JSONArray(workspaceProperties.get("ProjectNames"));
		workspaceJSON.put("ProjectNames", projectNames);
		return workspaceJSON;
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
		File logFile = new File(workspaceRoot, "migration.log");
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
			if ("-flat".equals(arguments[i])) {
				//the flat layout organises projects at the root /project
				orionFileLayoutUsertree = false;
			} else if ("-usertree".equals(arguments[i])) {
				//the user-tree layout organises projects by the user who created it: pr/project
				orionFileLayoutUsertree = true;
			} else if ("-root".equals(arguments[i])) {
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
	}

	/**
	 * Executes the project migration.
	 * @throws IOException 
	 * @throws JSONException 
	 */
	public void run() throws IOException, JSONException {
		File workspaceRoot = new File(orionWorkspaceRoot);
		migrationLogOpen(workspaceRoot);
		migrationLogPrint("Migrating MetaStore from legacy to simple.");

		Map<String, Map<String, String>> users = getMetadataFromFile(orionUserPrefsFile);
		Map<String, Map<String, String>> workspaces = getMetadataFromFile(orionWorkspacePrefsFile);
		Map<String, Map<String, String>> projects = getMetadataFromFile(orionProjectPrefsFile);
		Map<String, Map<String, String>> sites = getMetadataFromFile(orionSitesPrefsFile);
		Map<String, Map<String, String>> operations = getMetadataFromFile(orionOperationsFile);

		File metaStoreRootFolder = createOrUpdateMetaStoreRoot();

		int userSize = users.size();
		int userCount = 1;
		for (String userId : users.keySet()) {
			Map<String, String> userProperties = users.get(userId);
			String userName = userProperties.get("UserName");
			migrationLogPrint("Processing UserId " + userId + " (" + userCount++ + " of " + userSize + ") UserName " + userName);

			File newUserHome = createOrUpdateUser(metaStoreRootFolder, userId, userProperties, sites, operations);

			String userWorkspaceProperty = userProperties.get("Workspaces");
			if (userWorkspaceProperty == null) {
				migrationLogPrint("User " + userName + " has no workspaces.");
			} else {
				JSONObject workspaceObject = new JSONArray(userWorkspaceProperty).getJSONObject(0);
				String workspaceId = workspaceObject.getString("Id");
				Map<String, String> workspaceProperties = workspaces.get(workspaceId);
				String workspaceName = workspaceProperties.get("Name");
				File newWorkspaceHome = new File(newUserHome + File.separator + workspaceName);
				createFolder(newWorkspaceHome, "Workspace");

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
						File newProjectHome = new File(newWorkspaceHome + File.separator + projectName);
						createFolder(newProjectHome, "Project");
						projectNamesJSON.put(projectName);
						JSONObject newProjectJSON = getProjectJSONfromProperties(projectProperties);
						File newProjectMetaFile = new File(newProjectHome, "project.json");
						createOrUpdateMetaFile(newProjectMetaFile, newProjectJSON, "project MetaData");
					}
					workspaceProperties.put("ProjectNames", projectNamesJSON.toString());
				}

				JSONObject newWorkspaceJSON = getWorkspaceJSONfromProperties(workspaceProperties);
				File newWorkspaceMetaFile = new File(newWorkspaceHome, "workspace.json");
				createOrUpdateMetaFile(newWorkspaceMetaFile, newWorkspaceJSON, "workspace MetaData");

			}
		}
		migrationLogClose();
	}

	/**
	 * Print the usage and exit;
	 */
	private void usage() {
		System.err.println("ProjectMigration: Usage: java -jar ProjectMigration.jar [ -flat | -usertree ] -root folder");
		System.exit(1);
	}

	/**
	 * Write the JSON object to the provided file.
	 * @param file The file to write to.
	 * @param jsonObject the JSON object.
	 */
	private void writeMetaFile(File file, JSONObject jsonObject) {
		try {
			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(jsonObject.toString(4));
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, file IO error", e);
		}
	}
}
