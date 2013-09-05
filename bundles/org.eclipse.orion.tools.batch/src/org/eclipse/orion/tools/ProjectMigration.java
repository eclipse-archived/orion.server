/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
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

public class ProjectMigration {
	private static final String WORKSPACE_ROOT = "/workspace/foo.legacy";
	private static final String METADATA_DIR = WORKSPACE_ROOT + "/.metadata/.plugins/org.eclipse.orion.server.core/.settings/";
	private static final String PROJECT_PREFS = METADATA_DIR + "Projects.prefs";
	private static final String WORKSPACE_PREFS = METADATA_DIR + "Workspaces.prefs";
	private static final String USER_PREFS = METADATA_DIR + "Users.prefs";

	//private static final String CONTENT_LOCATION = "/home/data/nfs/serverworkspace";
	private static final String CONTENT_LOCATION = WORKSPACE_ROOT;

	private static final boolean ORION_FILE_LAYOUT_USERTREE = false;

	private final static String ORION_METASTORE_VERSION = "OrionMetastoreVersion";
	private final static int VERSION = 1;

	public static void main(String[] arguments) throws IOException, JSONException {
		new ProjectMigration().run();
	}
	
	private PrintStream migrationLog;

	private void createFolder(File newFolder, String message) {
		if (!newFolder.isDirectory() || !newFolder.exists()) {
			newFolder.mkdirs();
			migrationLogPrint("Created " + message + " folder: " + newFolder.getAbsolutePath());
		} else {
			migrationLogPrint(message + " folder exists: " + newFolder.getAbsolutePath());
		}
	}

	private void createOrUpdateMetaFile(File metaFile, JSONObject jsonObject, String message) {
		if (metaFile.exists()) {
			migrationLogPrint("Updated existing " + message + " file: " + metaFile.getAbsolutePath());
		} else {
			migrationLogPrint("Created " + message + " file: " + metaFile.getAbsolutePath());
		}
		writeMetaFile(metaFile, jsonObject);
	}

	private File createOrUpdateMetaStoreRoot() {
		File metaStoreRootFolder = new File(CONTENT_LOCATION + File.separator + "metastore");
		createFolder(metaStoreRootFolder, "Root");
		File metaStoreRootFile = new File(metaStoreRootFolder, "metastore.json");
		JSONObject metaStoreRootJSON = new JSONObject();
		metaStoreRootJSON.put(ORION_METASTORE_VERSION, VERSION);
		createOrUpdateMetaFile(metaStoreRootFile, metaStoreRootJSON, "root MetaData");
		return metaStoreRootFolder;
	}

	private File createOrUpdateUser(File metaStoreRootFolder, String userId, Map<String, String> userProperties) {
		String userName = userProperties.get("UserName");
		
		if (ORION_FILE_LAYOUT_USERTREE) {
			String currentUserPrefix = userId.substring(0, Math.min(2, userId.length()));
			File currentUserHome = new File(CONTENT_LOCATION + File.separator + currentUserPrefix + File.separator + userId);
			if (!currentUserHome.isDirectory() || !currentUserHome.exists()) {
				System.out.print(" NO HOME DIR");
			}
		}

		File newUserHome = new File(metaStoreRootFolder + File.separator + userName);
		if (ORION_FILE_LAYOUT_USERTREE) {
			String newUserPrefix = userName.substring(0, Math.min(2, userName.length()));
			newUserHome = new File(metaStoreRootFolder + File.separator + newUserPrefix + File.separator + userName);
		}
		createFolder(newUserHome, "User");

		JSONObject newUserJSON = getUserJSONfromProperties(userProperties);
		File newUserMetaFile = new File(newUserHome, "user.json");
		createOrUpdateMetaFile(newUserMetaFile, newUserJSON, "user MetaData");
		
		return newUserHome;
	}

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
	
	private JSONObject getUserJSONfromProperties(Map<String, String> userProperties) {
		JSONObject userJSON = new JSONObject();
		userJSON.put(ORION_METASTORE_VERSION, VERSION);
		String userName = userProperties.get("UserName");
		String fullName = userProperties.get("Name");
		userJSON.put("UniqueId", userName);
		userJSON.put("UserName", userName);
		userJSON.put("FullName", fullName);
		JSONObject properties = new JSONObject();
		JSONArray userRights = new JSONArray(userProperties.get("UserRights"));
		properties.put("UserRights", userRights);
		properties.put("UserRightsVersion", userProperties.get("UserRightsVersion"));
		userJSON.put("Properties", properties);
		String workspaces = userProperties.get("Workspaces");
		if (workspaces == null) {
			userJSON.put("WorkspaceIds", new JSONArray());
		} else {
			JSONArray currentWorkspacesJSON = new JSONArray(workspaces);
			JSONArray newWorkspacesJSON = new JSONArray();
			for (int i = 0; i < currentWorkspacesJSON.length(); i++) {
				// legacy workspace property looks like [{"Id"\:"anthony","LastModified"\:1351775348697}]
				JSONObject object = currentWorkspacesJSON.getJSONObject(i);
				String workspaceId = object.getString("Id");
				if (workspaceId != null) {
					// Add the default workspace name here
					newWorkspacesJSON.put(workspaceId + "-Orion Content");
					break;
				}
			}
			userJSON.put("WorkspaceIds", newWorkspacesJSON);
		}
		return userJSON;
	}

	protected void getUsersFromMetadata(Map<String, Map<String, String>> users) {
		int userNumber = 1;
		for (String userId : users.keySet()) {
			//System.out.print("User: " + userId + " (" + userNumber++ + " of " + allUsers.size() + ") ");
			Map<String, String> properties = users.get(userId);
			String workspaces = properties.get("Workspaces");
			if (workspaces == null) {
				System.out.println("ERROR userId " + userId + " (" + userNumber++ + " of " + users.size() + ") has no workspaces");
				continue;
			}
			for (String propertyKey : properties.keySet()) {
				if (propertyKey.equals("UserRightsVersion")) {
					continue;
				} else if (propertyKey.equals("Workspaces")) {
					continue;
				} else if (propertyKey.equals("UserName")) {
					continue;
				} else if (propertyKey.equals("Name")) {
					continue;
				} else if (propertyKey.equals("Id")) {
					continue;
				} else if (propertyKey.equals("Guest")) {
					continue;
				} else if (propertyKey.equals("UserRights") || propertyKey.startsWith("operations///task") || propertyKey.startsWith("operations///gitapi") || propertyKey.startsWith("edit/outline") || propertyKey.startsWith("/edit/outline") || propertyKey.startsWith("/editor/settings") || propertyKey.startsWith("/git/config") || propertyKey.startsWith("cm/configurations") || propertyKey.startsWith("/cm/configurations") || propertyKey.startsWith("window/favorites") || propertyKey.startsWith("/window/favorites") || propertyKey.startsWith("window/views") || propertyKey.startsWith("/window/views") || propertyKey.startsWith("/plugins/http:") || propertyKey.startsWith("plugins///file") || propertyKey.startsWith("plugins/https:") || propertyKey.startsWith("plugins//https:")
						|| propertyKey.startsWith("plugins//http:") || propertyKey.startsWith("plugins/http:") || propertyKey.startsWith("/plugins/https:") || propertyKey.startsWith("/plugins//https:") || propertyKey.startsWith("/plugins//http:") || propertyKey.startsWith("/plugins/http:") || propertyKey.startsWith("SiteConfigurations")) {
					continue;
				}
				System.out.println(userId + " " + propertyKey + "=" + properties.get(propertyKey) + " ");
			}
		}
	}

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

	private void migrationLogClose() {
		migrationLog.close();
	}

	private void migrationLogOpen(File workspaceRoot) throws FileNotFoundException {
		File logFile = new File(workspaceRoot, "migration.log");
		FileOutputStream stream = new FileOutputStream(logFile);
		migrationLog = new PrintStream(stream);
	}

	private void migrationLogPrint(String message) {
		migrationLog.println(message);
	}

	/**
	 * Executes the project migration.
	 * @throws IOException 
	 * @throws JSONException 
	 */
	public void run() throws IOException, JSONException {
		File workspaceRoot = new File(WORKSPACE_ROOT);
		migrationLogOpen(workspaceRoot);
		migrationLogPrint("Migrating MetaStore from legacy to simple.");

		Map<String, Map<String, String>> users = getMetadataFromFile(USER_PREFS);
		Map<String, Map<String, String>> workspaces = getMetadataFromFile(WORKSPACE_PREFS);
		Map<String, Map<String, String>> projects = getMetadataFromFile(PROJECT_PREFS);

		File metaStoreRootFolder = createOrUpdateMetaStoreRoot();

		int userSize = users.size();
		int userCount = 1;
		for (String userId : users.keySet()) {
			Map<String, String> userProperties = users.get(userId);
			String userName = userProperties.get("UserName");
			migrationLogPrint("Processing UserId " + userId + " (" + userCount++ + " of " + userSize + ") UserName " + userName);

			File newUserHome = createOrUpdateUser(metaStoreRootFolder, userId, userProperties);
			
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

	private void writeMetaFile(File file, JSONObject jsonObject) {
		try {
			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(jsonObject.toString());
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, file IO error", e);
		}
	}
}
