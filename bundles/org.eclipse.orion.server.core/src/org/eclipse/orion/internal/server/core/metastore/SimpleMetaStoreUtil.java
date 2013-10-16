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

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to help with the create, read, update and delete of the files and folders
 * in a simple meta store.
 * @author Anthony Hunter
 *
 */
public class SimpleMetaStoreUtil {

	public static final String METAFILE_EXTENSION = ".json";
	public static final String SEPARATOR = "-";
	public final static String USER = "user";

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
			fileWriter.write(jsonObject.toString(4));
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
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
	public static boolean createMetaFolder(File parent, String name) {
		if (!parent.exists()) {
			throw new RuntimeException("Meta File Error, parent folder does not exist");
		}
		if (!parent.isDirectory()) {
			throw new RuntimeException("Meta File Error, parent is not a folder");
		}
		File newFolder = new File(parent, name);
		if (newFolder.exists()) {
			return true;
		}
		if (!newFolder.mkdir()) {
			throw new RuntimeException("Meta File Error, cannot create folder");
		}
		return true;
	}

	/**
	 * Create a new user folder with the provided name under the provided parent folder.
	 * @param parent the parent folder.
	 * @param userName the user name.
	 * @return true if the creation was successful.
	 */
	public static boolean createMetaUserFolder(File parent, String userName) {
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
		return createMetaFolder(orgFolder, userName);
	}

	/**
	 * Decode the user id from the workspace id. In the current implementation, the user id and
	 * workspace name, joined with a dash, is the workspaceId. 
	 * @param workspaceId The workspace id.
	 * @return The user id.
	 */
	public static String decodeUserIdFromWorkspaceId(String workspaceId) {
		if (workspaceId.indexOf(SEPARATOR) == -1) {
			return null;
		}
		return workspaceId.substring(0, workspaceId.indexOf(SEPARATOR));
	}

	/**
	 * Decode the workspace name from the workspace id. In the current implementation, the user name and
	 * workspace name, joined with a dash, is the workspaceId. The workspace name is not the actual workspace
	 * name as we have removed spaces and pound during the encoding.
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
	 * @return true if the deletion was successful.
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
	 * Delete the provided folder. The folder should be empty. If the exceptionWhenNotEmpty is false, then do not throw
	 * an exception when the folder is not empty, just return false. 
	 * @param parent The parent folder.
	 * @param name The name of the folder
	 * @return true if the deletion was successful.
	 */
	public static boolean deleteMetaFolder(File parent, String name, boolean exceptionWhenNotEmpty) {
		if (!isMetaFolder(parent, name)) {
			throw new RuntimeException("Meta File Error, cannot delete, does not exist.");
		}
		File folder = retrieveMetaFolder(parent, name);
		if (!folder.delete()) {
			if (exceptionWhenNotEmpty) {
				throw new RuntimeException("Meta File Error, cannot delete, not empty.");
			}
			return false;
		}
		return true;
	}

	/**
	 * Delete the user folder with the provided name under the provided parent folder.
	 * @param parent the parent folder.
	 * @param userName the user name.
	 * @return true if the creation was successful.
	 */
	public static boolean deleteMetaUserFolder(File parent, String userName) {
		String[] files = parent.list();
		if (files.length != 0) {
			throw new RuntimeException("Meta File Error, cannot delete, not empty.");
		}
		if (!parent.delete()) {
			throw new RuntimeException("Meta File Error, cannot delete folder.");
		}

		//the user-tree layout organises projects by the user who created it: metastore/an/anthony
		File orgFolder = parent.getParentFile();
		files = orgFolder.list();
		if (files.length != 0) {
			return true;
		}
		if (!orgFolder.delete()) {
			throw new RuntimeException("Meta File Error, cannot delete folder.");
		}
		return true;
	}

	/**
	 * Encode the workspace id from the user id and workspace id. In the current implementation, the 
	 * user name and workspace name, joined with a dash, is the workspaceId. The workspaceId also cannot 
	 * contain spaces or pound.
	 * @param userName The user name id.
	 * @param workspaceName The workspace name.
	 * @return The workspace id.
	 */
	public static String encodeWorkspaceId(String userName, String workspaceName) {
		String workspaceId = workspaceName.replace(" ", "").replace("#", "");
		return userName + SEPARATOR + workspaceId;
	}

	/**
	 * Determine if the provided name is a MetaFile under the provided parent folder.
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return true if the name is a MetaFile.
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
	 * Determine if the provided parent folder contains a MetaFile with the provided name
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return true if the parent is a folder with a MetaFile.
	 */
	public static boolean isMetaFolder(File parent, String name) {
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
	 * @param parent the parent folder.
	 * @param userName the user name.
	 * @return true if the parent is a folder with a user MetaFile.
	 */
	public static boolean isMetaUserFolder(File parent, String userName) {
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
		if (!isMetaFolder(orgFolder, userName)) {
			return false;
		}
		File userFolder = retrieveMetaFolder(orgFolder, userName);
		return isMetaFile(userFolder, USER);
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
	 * Retrieve the list of user folders under the parent folder.
	 * @param parent The parent folder.
	 * @return list of user folders.
	 */
	public static List<String> listMetaUserFolders(File parent) {
		//the user-tree layout organises projects by the user who created it: metastore/an/anthony
		List<String> userMetaFolders = new ArrayList<String>();
		for (File file : parent.listFiles()) {
			if (file.getName().equals(".metadata")) {
				// skip the eclipse workspace metadata folder
				continue;
			} else if (file.isFile() && file.getName().endsWith(METAFILE_EXTENSION)) {
				// skip the meta file
				continue;
			} else if (file.isDirectory()) {
				// org folder directory, so go into for users
				for (File userFolder : file.listFiles()) {
					if (isMetaUserFolder(parent, userFolder.getName())) {
						// user folder directory
						userMetaFolders.add(userFolder.getName());
						continue;
					}
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					logger.warn("Meta File Error, root contains invalid metadata: folder " + file.toString() + "/" + userFolder.getName()); //$NON-NLS-1$
				}
				continue;
			}
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.warn("Meta File Error, root contains invalid metadata: file " + file.toString()); //$NON-NLS-1$
		}
		return userMetaFolders;
	}

	/**
	 * Move the MetaFile in the provided parent folder. 
	 * @param parent The parent folder.
	 * @param oldName The old name of the MetaFile
	 * @param newName The new name of the MetaFile
	 * @return true if the move was successful.
	 */
	public static boolean moveMetaFile(File parent, String oldName, String newName) {
		if (!isMetaFile(parent, oldName)) {
			return false;
		}
		File oldFile = retrieveMetaFile(parent, oldName);
		File newFile = retrieveMetaFile(parent, newName);
		return oldFile.renameTo(newFile);
	}

	/**
	 * Move the MetaFolder in the provided parent folder. 
	 * @param parent The parent folder.
	 * @param oldName The old name of the MetaFile
	 * @param newName The new name of the MetaFile
	 * @return true if the move was successful.
	 */
	public static boolean moveMetaFolder(File parent, String oldName, String newName) {
		if (!isMetaFolder(parent, oldName)) {
			return false;
		}
		File oldFolder = retrieveMetaFolder(parent, oldName);
		File newFolder = retrieveMetaFolder(parent, newName);
		return oldFolder.renameTo(newFolder);
	}

	/**
	 * Move the MetaFolder to the new named MetaFolder. 
	 * @param oldUserMetaFolder The old MetaFolder.
	 * @param newUserMetaFolder The new MetaFolder.
	 * @return true if the move was successful.
	 */
	public static boolean moveUserMetaFolder(File oldUserMetaFolder, File newUserMetaFolder) {
		if (!oldUserMetaFolder.exists()) {
			throw new RuntimeException("Meta File Error, parent folder does not exist");
		}
		if (!oldUserMetaFolder.isDirectory()) {
			throw new RuntimeException("Meta File Error, parent is not a folder");
		}
		if (newUserMetaFolder.exists()) {
			throw new RuntimeException("Meta File Error, new folder already exists");
		}
		File orgFolder = newUserMetaFolder.getParentFile();
		if (!orgFolder.exists()) {
			if (!orgFolder.mkdir()) {
				throw new RuntimeException("Meta File Error, mkdir failed for " + orgFolder.toString());
			}
		}
		if (!oldUserMetaFolder.renameTo(newUserMetaFolder)) {
			throw new RuntimeException("Meta File Error, renameTo failed");
		}
		//the user-tree layout organises projects by the user who created it: metastore/an/anthony
		orgFolder = oldUserMetaFolder.getParentFile();
		String[] files = orgFolder.list();
		if (files.length != 0) {
			return true;
		}
		if (!orgFolder.delete()) {
			throw new RuntimeException("Meta File Error, cannot delete folder.");
		}
		return true;
	}

	/**
	 * Get the JSON from the MetaFile in the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @return The JSON containing the data in the MetaFile.
	 */
	public static JSONObject readMetaFile(File parent, String name) {
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
	 * Get the MetaFolder in the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFolder
	 * @return The JSON containing the data in the MetaFile.
	 */
	public static File readMetaFolder(File parent, String name) {
		if (!isMetaFolder(parent, name)) {
			return null;
		}
		return retrieveMetaFolder(parent, name);
	}

	/**
	 * Get the user folder with the provided name under the provided parent folder.
	 * @param parent the parent folder.
	 * @param userName the user name.
	 * @return the folder.
	 */
	public static File readMetaUserFolder(File parent, String userName) {
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
	 * Update the existing MetaFile with the provided name under the provided parent folder. 
	 * @param parent The parent folder.
	 * @param name The name of the MetaFile
	 * @param jsonObject The JSON containing the data to update in the MetaFile.
	 * @return
	 */
	public static boolean updateMetaFile(File parent, String name, JSONObject jsonObject) {
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
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, file IO error", e);
		} catch (JSONException e) {
			throw new RuntimeException("Meta File Error, JSON error", e);
		}
		return true;
	}

}
