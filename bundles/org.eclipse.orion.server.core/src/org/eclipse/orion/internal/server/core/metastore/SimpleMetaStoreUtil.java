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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A utility class to help with the create, read, update and delete of the files and folders
 * in a simple meta store.
 * @author Anthony Hunter
 *
 */
public class SimpleMetaStoreUtil {

	private static final String REGEX = "^[a-z][a-z0-9_-]*$";
	private static final Pattern PATTERN = Pattern.compile(REGEX);
	private static final int MINIMUM_LENGTH = 1;
	private static final int MAXIMUM_LENGTH = 31;
	public static final String ROOT = "metastore";
	public static final String SEPARATOR = "-";
	public static final String METAFILE_EXTENSION = ".json";

	/**
	 * Create a new MetaFile with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @param jsonObject The JSON containing the data to save in the MetaFile.
	 * @return true if the creation was successful.
	 */
	public static boolean createMetaFile(File parent, String name, JSONObject jsonObject) {
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
			fileWriter.write(jsonObject.toString());
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, file IO error", e);
		}
		return true;
	}

	/**
	 * Create a new folder with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the folder.
	 * @return true if the creation was successful.
	 */
	public static boolean createMetaFolder(File parent, String name) {
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
		return true;
	}

	/**
	 * Create a new root MetaFile under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param jsonObject The JSON containing the data to save in the MetaFile.
	 * @return true if the creation was successful.
	 */
	public static boolean createMetaStoreRoot(File parent, JSONObject jsonObject) {
		if (!createMetaFolder(parent, ROOT)) {
			throw new RuntimeException("Meta File Error, cannot create root folder " + ROOT + " under " + parent.getAbsolutePath());
		}
		File metaStoreRootFolder = SimpleMetaStoreUtil.retrieveMetaFolder(parent, ROOT);
		return createMetaFile(metaStoreRootFolder, ROOT, jsonObject);
	}

	/**
	 * Decode the project name from the project id. In the current implementation, the project id and
	 * project name are the same value. 
	 * @param projectId The project id.
	 * @return The decoded project name.
	 */
	public static String decodeProjectNameFromProjectId(String projectId) {
		return projectId;
	}

	/**
	 * Decode the user name from the workspace id. In the current implementation, the user name and
	 * workspace name, joined with a dash, is the workspaceId. 
	 * @param workspaceId The workspace id.
	 * @return The user name.
	 */
	public static String decodeUserNameFromWorkspaceId(String workspaceId) {
		if (workspaceId.indexOf(SEPARATOR) == -1) {
			return null;
		}
		return workspaceId.substring(0, workspaceId.indexOf(SEPARATOR));
	}

	/**
	 * Decode the workspace name from the workspace id. In the current implementation, the user name and
	 * workspace name, joined with a dash, is the workspaceId. 
	 * @param workspaceId The workspace id.
	 * @return The workspace name.
	 */
	public static String decodeWorkspaceNameFromWorkspaceId(String workspaceId) {
		if (workspaceId.indexOf(SEPARATOR) == -1) {
			return null;
		}
		return workspaceId.substring(workspaceId.indexOf(SEPARATOR) + 1);
	}

	/**
	 * Delete the MetaFile with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return true of the creation was successful.
	 */
	public static boolean deleteMetaFile(File parent, String name) {
		if (!isMetaFile(parent, name)) {
			throw new RuntimeException("Meta File Error, cannot delete, does not exist.");
		}
		File savedFile = retrieveMetaFile(parent, name);
		if (!savedFile.delete()) {
			throw new RuntimeException("Meta File Error, cannot delete file.");
		}
		return true;
	}

	/**
	 * Delete the provided folder. The folder should be empty. 
	 * @param parent The parent folder.
	 * @return true of the creation was successful.
	 */
	public static boolean deleteMetaFolder(File parent) {
		String[] files = parent.list();
		if (files.length != 0) {
			throw new RuntimeException("Meta File Error, cannot delete, not empty.");
		}
		if (!parent.delete()) {
			throw new RuntimeException("Meta File Error, cannot delete folder.");
		}
		return true;

	}

	/**
	 * Delete the MetaStore root folder. 
	 * @param parent The parent folder.
	 * @return true of the creation was successful.
	 */
	public static boolean deleteMetaStoreRoot(File parent) {
		File metaStoreRootFolder = SimpleMetaStoreUtil.retrieveMetaFolder(parent, ROOT);
		return deleteMetaFile(metaStoreRootFolder, ROOT);
	}

	/**
	 * Encode the project id from the project name. In the current implementation, the project id and
	 * project name are the same value. 
	 * @param projectId The project name.
	 * @return The decoded project id.
	 */
	public static String encodeProjectId(String projectName) {
		return projectName;
	}

	/**
	 * Encode the workspace id from the user id and workspace id. In the current implementation, the 
	 * user name and workspace name, joined with a dash, is the workspaceId. 
	 * @param userName The user name id.
	 * @param workspaceName The workspace name.
	 * @return The workspace id.
	 */
	public static String encodeWorkspaceId(String userName, String workspaceName) {
		return userName + SEPARATOR + workspaceName;
	}

	/**
	 * Determine if the provided name is a MetaFile under the provided parent folder.
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return true if the parent and name is a MetaFile.
	 */
	public static boolean isMetaFile(File parent, String name) {
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
	 * Determine if the provided parent folder contains a MetaFile.
	 * @param parent The parent folder.
	 * @return true if the parent is a folder with a MetaFile.
	 */
	public static boolean isMetaFolder(File parent) {
		if (!parent.exists()) {
			return false;
		}
		if (!parent.isDirectory()) {
			return false;
		}
		for (File file : parent.listFiles()) {
			if (file.isDirectory()) {
				// directory, so continue
				continue;
			}
			if (file.isFile() && file.getName().endsWith(METAFILE_EXTENSION)) {
				// meta file, so continue
				continue;
			}
			throw new RuntimeException("Meta File Error, contains invalid metadata:" + parent.toString() + " at " + file.getName());
		}
		return true;
	}

	/**
	 * Determine if the provided folder is a folder with a root MetaFile. 
	 * @param parent The parent folder.
	 * @return true if the parent is a folder with a MetaFile.
	 */
	public static boolean isMetaStoreRoot(File parent) {
		File metaStoreRootFolder = SimpleMetaStoreUtil.retrieveMetaFolder(parent, ROOT);
		return isMetaFile(metaStoreRootFolder, ROOT);
	}

	/**
	 * Determines if the name is a valid user name (based on standard Linux naming rules).
	 * @param name The name to validate.
	 * @return true if the if the name is a valid.
	 */
	public static boolean isNameValid(String name) {
		boolean valid = false;
		if (name != null) {
			if ((name.length() >= MINIMUM_LENGTH) && (name.length() <= MAXIMUM_LENGTH)) {
				Matcher matcher = PATTERN.matcher(name);
				valid = matcher.find();
			}
		}
		return valid;
	}

	/**
	 * Retrieve the list of meta files under the parent folder.
	 * @param parent The parent folder.
	 * @return list of meta files.
	 */
	public static List<String> listMetaFiles(File parent) {
		List<String> savedFiles = new ArrayList<String>();
		for (File file : parent.listFiles()) {
			if (file.isDirectory()) {
				// directory, so add to list and continue
				savedFiles.add(file.getName());
				continue;
			}
			if (file.isFile() && file.getName().endsWith(METAFILE_EXTENSION)) {
				// meta file, so continue
				continue;
			}
			throw new RuntimeException("Meta File Error, contains invalid metadata:" + parent.toString() + " at " + file.getName());
		}
		return savedFiles;
	}

	/**
	 * Retrieve the MetaFile with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return The JSON containing the data in the MetaFile.
	 */
	public static JSONObject retrieveMetaFileJSON(File parent, String name) {
		JSONObject jsonObject;
		try {
			if (!isMetaFile(parent, name)) {
				return null;
			}
			File savedFile = retrieveMetaFile(parent, name);

			FileReader fileReader = new FileReader(savedFile);
			char[] chars = new char[(int) savedFile.length()];
			fileReader.read(chars);
			fileReader.close();
			jsonObject = new JSONObject(new String(chars));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, file IO error", e);
		} catch (JSONException e) {
			throw new RuntimeException("Meta File Error, could not build JSON", e);
		}
		return jsonObject;
	}

	/**
	 * Retrieve the MetaFile with the provided name under the parent folder.
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return The MetaFile.
	 */
	public static File retrieveMetaFile(File parent, String name) {
		return new File(parent, name + ".json");
	}

	/**
	 * Retrieve the folder with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the folder.
	 * @return The folder.
	 */
	public static File retrieveMetaFolder(File parent, String name) {
		return new File(parent, name);
	}

	/**
	 * Retrieve the JSON containing the data in the root MetaFile under the parent folder.
	 * @param parent The parent folder.
	 * @return The JSON containing the data in the root MetaFile.
	 */
	public static JSONObject retrieveMetaStoreRootJSON(File parent) {
		if (isMetaStoreRoot(parent)) {
			File metaStoreRootFolder = SimpleMetaStoreUtil.retrieveMetaFolder(parent, ROOT);
			return retrieveMetaFileJSON(metaStoreRootFolder, ROOT);
		}
		return null;
	}

	/**
	 * Update the existing MetaFile with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @param jsonObject The JSON containing the data to update in the MetaFile.
	 * @return
	 */
	public static boolean updateMetaFile(File parent, String name, JSONObject jsonObject) {
		try {
			if (!isMetaFile(parent, name)) {
				throw new RuntimeException("Meta File Error, cannot delete, does not exist.");
			}
			File savedFile = retrieveMetaFile(parent, name);

			FileWriter fileWriter = new FileWriter(savedFile);
			fileWriter.write(jsonObject.toString());
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, file IO error", e);
		}
		return true;
	}

}
