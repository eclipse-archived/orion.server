/*******************************************************************************
 * Copyright (c) 2013, 2015 IBM Corporation and others.
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.resources.FileLocker;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to help with the create, read, update and delete of the files and folders in a simple meta store.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStoreUtil {

	/**
	 * The folder where files are invalid metadata is moved rather than deleting outright.
	 */
	public static final String ARCHIVE = ".archive";

	/**
	 * The file scheme name of a URI
	 */
	public static final String FILE_SCHEMA = "file";

	/**
	 * All Orion metadata is saved in a file in JSON format with this file extension.
	 */
	public static final String METAFILE_EXTENSION = ".json";

	/**
	 * The JVM system property for the operating system
	 */
	public static String OPERATING_SYSTEM_NAME = System.getProperty("os.name").toLowerCase();

	/**
	 * The separator used to encode the workspace id.
	 */
	public static final String SEPARATOR = "-";

	/**
	 * The string used to encode the serverworkspace path in a project contentLocation, see Bug 436578
	 */
	public static final String SERVERWORKSPACE = "${SERVERWORKSPACE}";

	/**
	 * The metadata for a user is stored in a user.json file.
	 */
	public final static String USER = "user";

	/**
	 * Archive the provided file to clean the metadata storage of invalid metadata. This is an alternative to the
	 * warning "root contains invalid metadata", see Bug 437962
	 * 
	 * @param root
	 *            the root folder that will contain the archive.
	 * @param file
	 *            the invalid metadata.
	 */
	protected static void archive(File root, File file) {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
		if (!isMetaFolder(root, ARCHIVE)) {
			if (!SimpleMetaStoreUtil.createMetaFolder(root, ARCHIVE)) {
				logger.error("SimpleMetaStore.archive: could not create archive folder at: " + root.toString() + File.separator + ARCHIVE);
				return;
			}
		}
		String parentPath = root.toString();
		File archive = SimpleMetaStoreUtil.retrieveMetaFolder(root, ARCHIVE);
		String filePath = file.toString().substring(parentPath.length());
		File archivedMetaFile = new File(archive, filePath);
		File archivedMetaFileParentFolder = archivedMetaFile.getParentFile();
		if (!archivedMetaFileParentFolder.exists()) {
			archivedMetaFileParentFolder.mkdirs();
		}
		if (archivedMetaFile.exists() && file.isDirectory()) {
			file.delete();
			logger.error("Meta File Error, root contains invalid metadata: empty folder " + file.toString() + " deleted."); //$NON-NLS-1$
		} else {
			file.renameTo(archivedMetaFile);
			if (archivedMetaFile.isDirectory()) {
				logger.error("Meta File Error, root contains invalid metadata: folder " + file.toString() + " archived to " + archivedMetaFile.toString()); //$NON-NLS-1$
			} else {
				logger.error("Meta File Error, root contains invalid metadata: file " + file.toString() + " archived to " + archivedMetaFile.toString()); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Create a new MetaFile with the provided name under the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the MetaFile
	 * @param jsonObject
	 *            The JSON containing the data to save in the MetaFile.
	 * @return true if the creation was successful.
	 */
	public static boolean createMetaFile(File parent, String name, JSONObject jsonObject) {
		try {
			if (isMetaFile(parent, name)) {
				File savedFile = retrieveMetaFile(parent, name);
				throw new RuntimeException("Meta File Error, file " + savedFile.toString() + " already exists, use update");
			}
			if (!parent.exists()) {
				throw new RuntimeException("Meta File Error, parent folder does not exist");
			}
			if (!parent.isDirectory()) {
				throw new RuntimeException("Meta File Error, parent is not a folder");
			}
			File newFile = retrieveMetaFile(parent, name);
			FileLocker locker = new FileLocker(newFile);
			try {
				locker.lock();
			} catch (IOException e) {
				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
				logger.error("Meta File Error, file IO error, could not lock the file", e); //$NON-NLS-1$
				throw new RuntimeException("Meta File Error, file IO error, could not lock the file", e);
			}
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(newFile);
				Charset utf8 = Charset.forName("UTF-8");
				OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, utf8);
				outputStreamWriter.write(jsonObject.toString(4));
				outputStreamWriter.write("\n");
				outputStreamWriter.flush();
				outputStreamWriter.close();
				fileOutputStream.close();
			} finally {
				locker.release();
			}
		} catch (FileNotFoundException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error("Meta File Error, cannot create file under " + parent.toString() + ": invalid file name: " + name); //$NON-NLS-1$
			return false;
		} catch (IOException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error("Meta File Error, file IO error", e); //$NON-NLS-1$
			throw new RuntimeException("Meta File Error, file IO error", e);
		} catch (JSONException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error("Meta File Error, cannot create JSON file " + parent.toString() + File.separator + name + METAFILE_EXTENSION + " from disk, reason: " + e.getLocalizedMessage()); //$NON-NLS-1$
			throw new RuntimeException("Meta File Error, JSON error", e);
		}
		return true;
	}

	/**
	 * Create a new folder with the provided name under the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the folder.
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
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.warn("Meta File Error, cannot create folder under " + newFolder.toString() + ": invalid folder name: " + name); //$NON-NLS-1$
			return false;
		}
		return true;
	}

	/**
	 * Create a new user folder with the provided name under the provided parent folder.
	 * 
	 * @param parent
	 *            the parent folder.
	 * @param userName
	 *            the user name.
	 * @return true if the creation was successful.
	 */
	public static boolean createMetaUserFolder(File parent, String userName) {
		if (!parent.exists()) {
			throw new RuntimeException("Meta File Error, parent folder does not exist");
		}
		if (!parent.isDirectory()) {
			throw new RuntimeException("Meta File Error, parent is not a folder");
		}

		// the user-tree layout organises projects by the user who created it: metastore/an/anthony
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
	 * Decode the project's content location. The variable ${SERVERWORKSPACE} is replaced with the path of the root
	 * location (serverworkspace).
	 * 
	 * @param contentLocation
	 *            the decoded content location.
	 * @return the project's content location.
	 */
	public static String decodeProjectContentLocation(String contentLocation) {
		if (!contentLocation.startsWith(SERVERWORKSPACE)) {
			// does not include the variable so just return the existing contentLocation.
			return contentLocation;
		}
		String root = OrionConfiguration.getRootLocation().toURI().toString();
		String decodedcontentLocation = contentLocation.replace(SERVERWORKSPACE, root);
		return decodedcontentLocation;
	}

	/**
	 * Decode the project name from the project id. In the current implementation, the project id and workspace name are
	 * the same value. However, on Windows, we replace the bar character in the project name with three dashes since bar
	 * is a reserved character on Windows and cannot be used to save a project to disk.
	 * 
	 * @param projectId
	 *            The project id.
	 * @return The project id.
	 */
	public static String decodeProjectNameFromProjectId(String projectId) {
		if (OPERATING_SYSTEM_NAME.contains("windows")) {
			// only do the decoding of the reserved bar character on Windows
			return projectId.replaceAll("---", "\\|");
		}
		return projectId;
	}

	/**
	 * Decode the user id from the workspace id. In the current implementation, the user id and workspace name, joined
	 * with a dash, is the workspaceId.
	 * 
	 * @param workspaceId
	 *            The workspace id.
	 * @return The user id.
	 */
	public static String decodeUserIdFromWorkspaceId(String workspaceId) {
		if (workspaceId.lastIndexOf(SEPARATOR) == -1) {
			return null;
		}
		return workspaceId.substring(0, workspaceId.lastIndexOf(SEPARATOR));
	}

	/**
	 * Decode the workspace name from the workspace id. In the current implementation, the user name and workspace name,
	 * joined with a dash, is the workspaceId. The workspace name is not the actual workspace name as we have removed
	 * spaces and pound during the encoding.
	 * 
	 * @param workspaceId
	 *            The workspace id.
	 * @return The workspace name.
	 */
	public static String decodeWorkspaceNameFromWorkspaceId(String workspaceId) {
		if (workspaceId.lastIndexOf(SEPARATOR) == -1) {
			return null;
		}
		return workspaceId.substring(workspaceId.lastIndexOf(SEPARATOR) + 1);
	}

	/**
	 * Delete the MetaFile with the provided name under the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the MetaFile
	 * @return true if the deletion was successful.
	 */
	public static boolean deleteMetaFile(File parent, String name) {
		if (!isMetaFile(parent, name)) {
			throw new RuntimeException("Meta File Error, cannot delete, does not exist.");
		}
		File savedFile = retrieveMetaFile(parent, name);
		FileLocker locker = new FileLocker(savedFile);
		try {
			locker.lock();
			if (!savedFile.delete()) {
				throw new RuntimeException("Meta File Error, cannot delete file.");
			}
		} catch (IOException e) {
			throw new RuntimeException("Meta File Error, cannot delete file.", e);
		} finally {
			locker.release();
		}
		return true;
	}

	/**
	 * Delete the provided folder. The folder should be empty. If the exceptionWhenNotEmpty is false, then do not throw
	 * an exception when the folder is not empty, just return false.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the folder
	 * @param exceptionWhenNotEmpty
	 *            throw a RuntimeException when the provided folder is not empty.
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
	 * 
	 * @param parent
	 *            the parent folder.
	 * @param userName
	 *            the user name.
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

		// the user-tree layout organises projects by the user who created it: metastore/an/anthony
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
	 * Encode the project's content location. When the project's content location is a URI with a file based schema and
	 * the project is in the default location, we want to replace the path of the root location (serverworkspace) with a
	 * variable ${SERVERWORKSPACE}
	 * 
	 * @param contentLocation
	 *            the content location.
	 * @return the project's content location.
	 */
	public static String encodeProjectContentLocation(String contentLocation) {
		if (!contentLocation.startsWith(FILE_SCHEMA)) {
			// not a file based schema so just return the existing contentLocation.
			return contentLocation;
		}
		String root = OrionConfiguration.getRootLocation().toURI().toString();
		if (contentLocation.startsWith(root)) {
			String encodedcontentLocation = SERVERWORKSPACE.concat(contentLocation.substring(root.length()));
			return encodedcontentLocation;
		}
		return contentLocation;
	}

	/**
	 * Encode the project id from the project name. In the current implementation, the project id and project name are
	 * the same value. However, on Windows, we replace the bar character in the project name with three dashes since bar
	 * is a reserved character on Windows and cannot be used to save a project to disk.
	 * 
	 * @param projectName
	 *            The project name.
	 * @return The project id.
	 */
	public static String encodeProjectIdFromProjectName(String projectName) {
		if (OPERATING_SYSTEM_NAME.contains("windows")) {
			// only do the encoding of the reserved bar character on Windows
			return projectName.replaceAll("\\|", "---");
		}
		return projectName;
	}

	/**
	 * Encode the workspace id from the user id and workspace id. In the current implementation, the user name and
	 * workspace name, joined with a dash, is the workspaceId. The workspaceId also cannot contain spaces or pound.
	 * 
	 * @param userName
	 *            The user name id.
	 * @param workspaceName
	 *            The workspace name.
	 * @return The workspace id.
	 */
	public static String encodeWorkspaceId(String userName, String workspaceName) {
		String workspaceId = workspaceName.replace(" ", "").replace("#", "").replaceAll("-", "");
		return userName + SEPARATOR + workspaceId;
	}

	/**
	 * Determine if the provided name is a MetaFile under the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the MetaFile
	 * @return true if the name is a MetaFile.
	 */
	public static boolean isMetaFile(File parent, String name) {
		return retrieveMetaFile(parent, name).isFile();
	}

	/**
	 * Determine if the provided parent folder contains a MetaFile with the provided name
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the MetaFile
	 * @return true if the parent is a folder with a MetaFile.
	 */
	public static boolean isMetaFolder(File parent, String name) {
		return retrieveMetaFolder(parent, name).isDirectory();
	}

	/**
	 * Determine if the provided user name is a MetaFolder under the provided parent folder.
	 * 
	 * @param parent
	 *            the parent folder.
	 * @param userName
	 *            the user name.
	 * @return true if the parent is a folder with a user MetaFile.
	 */
	public static boolean isMetaUserFolder(File parent, String userName) {
		if (!parent.exists()) {
			throw new RuntimeException("Meta File Error, parent folder does not exist");
		}
		if (!parent.isDirectory()) {
			throw new RuntimeException("Meta File Error, parent is not a folder");
		}

		// the user-tree layout organises projects by the user who created it: metastore/an/anthony
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
	 * 
	 * @param parent
	 *            The parent folder.
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
	 * 
	 * @param parent
	 *            The parent folder.
	 * @return list of user folders.
	 */
	public static List<String> listMetaUserFolders(File parent) {
		// the user-tree layout organizes folders user: serverworkspace/an/anthony
		List<String> userMetaFolders = new ArrayList<String>();
		for (File file : parent.listFiles()) {
			if (! file.canRead()) {
				// see Bugzilla 461749
				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
				logger.error("Cannot read folder, check directory permissions of " + file.getAbsolutePath()); //$NON-NLS-1$
				continue;
			} else if (file.getName().equals(".metadata")) {
				// skip the eclipse workspace metadata folder
				continue;
			} else if (file.getName().equals("orion.conf")) {
				// skip the orion.conf configuration file.
				continue;
			} else if (file.getName().equals(ARCHIVE)) {
				// skip the archive folder
				continue;
			} else if (file.isFile() && file.getName().endsWith(METAFILE_EXTENSION) && file.getName().startsWith(SimpleMetaStore.ROOT)) {
				// skip the root meta file (metastore.json)
				continue;
			} else if (file.isDirectory() && file.getName().length() <= 2) {
				// organizational folder directory, folder an in serverworkspace/an/anthony
				File orgFolder = file;
				if (file.list().length == 0) {
					// organizational folder directory is empty, archive it.
					archive(parent, orgFolder);
				} else {
					for (File userFolder : orgFolder.listFiles()) {
						if (isMetaUserFolder(parent, userFolder.getName())) {
							// user folder directory, folder anthony in serverworkspace/an/anthony
							userMetaFolders.add(userFolder.getName());
							continue;
						} else if (! userFolder.canRead()) {
							// see Bugzilla 461749
							Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
							logger.error("Cannot read folder, check directory permissions of " + userFolder.getAbsolutePath()); //$NON-NLS-1$
							continue;
						} else {
							// archive the invalid metadata
							archive(parent, userFolder);
						}
					}
				}
				continue;
			}
			// archive the invalid metadata
			archive(parent, file);
		}
		return userMetaFolders;
	}

	/**
	 * Move the MetaFile in the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param oldName
	 *            The old name of the MetaFile
	 * @param newName
	 *            The new name of the MetaFile
	 * @return true if the move was successful.
	 */
	public static boolean moveMetaFile(File parent, String oldName, String newName) {
		if (!isMetaFile(parent, oldName)) {
			return false;
		}
		File oldFile = retrieveMetaFile(parent, oldName);
		File newFile = retrieveMetaFile(parent, newName);
		// don't lock because behaviour is unclear
		return oldFile.renameTo(newFile);
	}

	/**
	 * Move the MetaFolder in the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param oldName
	 *            The old name of the MetaFile
	 * @param newName
	 *            The new name of the MetaFile
	 * @return true if the move was successful.
	 */
	public static boolean moveMetaFolder(File parent, String oldName, String newName) {
		if (!isMetaFolder(parent, oldName)) {
			return false;
		}
		File oldFolder = retrieveMetaFolder(parent, oldName);
		File newFolder = retrieveMetaFolder(parent, newName);
		// don't lock because behaviour is unclear
		return oldFolder.renameTo(newFolder);
	}

	/**
	 * Move the MetaFolder to the new named MetaFolder.
	 * 
	 * @param oldUserMetaFolder
	 *            The old MetaFolder.
	 * @param newUserMetaFolder
	 *            The new MetaFolder.
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
		// the user-tree layout organises projects by the user who created it: metastore/an/anthony
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
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the MetaFile
	 * @return The JSON containing the data in the MetaFile.
	 */
	public static JSONObject readMetaFile(File parent, String name) {
		JSONObject jsonObject;
		try {
			if (!isMetaFile(parent, name)) {
				return null;
			}
			File savedFile = retrieveMetaFile(parent, name);
			char[] chars = new char[(int) savedFile.length()];
			FileLocker locker = new FileLocker(savedFile);
			locker.lock();
			try {
				FileInputStream fileInputStream = new FileInputStream(savedFile);
				Charset utf8 = Charset.forName("UTF-8");
				InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, utf8);
				inputStreamReader.read(chars);
				inputStreamReader.close();
				fileInputStream.close();
			} finally {
				locker.release();
			}
			jsonObject = new JSONObject(new String(chars));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Meta File Error, file not found", e);
		} catch (IOException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error("Meta File Error, file IO error", e); //$NON-NLS-1$
			throw new RuntimeException("Meta File Error, file IO error", e);
		} catch (JSONException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error("Meta File Error, cannot read JSON file " + parent.toString() + File.separator + name + METAFILE_EXTENSION + " from disk, reason: " + e.getLocalizedMessage()); //$NON-NLS-1$
			return null;
		}
		return jsonObject;
	}

	/**
	 * Get the MetaFolder in the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the MetaFolder
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
	 * 
	 * @param parent
	 *            the parent folder.
	 * @param userName
	 *            the user name.
	 * @return the folder.
	 */
	public static File readMetaUserFolder(File parent, String userName) {
		// the user-tree layout organises projects by the user who created it: metastore/an/anthony
		String userPrefix = userName.substring(0, Math.min(2, userName.length()));
		File orgFolder = new File(parent, userPrefix);
		return new File(orgFolder, userName);
	}

	/**
	 * Retrieve the MetaFile with the provided name under the parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the MetaFile
	 * @return The MetaFile.
	 */
	public static File retrieveMetaFile(File parent, String name) {
		return new File(parent, name + METAFILE_EXTENSION);
	}

	/**
	 * Retrieve the folder with the provided name under the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the folder.
	 * @return The folder.
	 */
	public static File retrieveMetaFolder(File parent, String name) {
		return new File(parent, name);
	}

	/**
	 * Update the existing MetaFile with the provided name under the provided parent folder.
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param name
	 *            The name of the MetaFile
	 * @param jsonObject
	 *            The JSON containing the data to update in the MetaFile.
	 * @return true if the update was successful.
	 */
	public static boolean updateMetaFile(File parent, String name, JSONObject jsonObject) {
		try {
			if (!isMetaFile(parent, name)) {
				throw new RuntimeException("Meta File Error, cannot update, does not exist.");
			}
			File savedFile = retrieveMetaFile(parent, name);
			FileLocker locker = new FileLocker(savedFile);
			locker.lock();
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(savedFile);
				Charset utf8 = Charset.forName("UTF-8");
				OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, utf8);
				outputStreamWriter.write(jsonObject.toString(4));
				outputStreamWriter.write("\n");
				outputStreamWriter.flush();
				outputStreamWriter.close();
				fileOutputStream.close();
			} finally {
				locker.release();
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Meta File Error, file not found", e);
		} catch (IOException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error("Meta File Error, file IO error", e); //$NON-NLS-1$
			throw new RuntimeException("Meta File Error, file IO error", e);
		} catch (JSONException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error("Meta File Error, cannot update JSON file " + parent.toString() + File.separator + name + METAFILE_EXTENSION + " from disk, reason: " + e.getLocalizedMessage()); //$NON-NLS-1$
			throw new RuntimeException("Meta File Error, JSON error", e);
		}
		return true;
	}

	/**
	 * Move the MetaFolder in the provided parent folder to a new parent folder
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param oldName
	 *            The old name of the MetaFolder
	 * @param newParent
	 *            The new name of parent folder
	 * @param newName
	 *            The new name of the MetaFolder
	 * @return true if the move was successful.
	 */
	public static boolean moveMetaFolder(File parent, String oldName, File newParent, String newName) {
		if (!isMetaFolder(parent, oldName)) {
			throw new RuntimeException("Meta File Error, folder " + oldName + " not found in folder " + parent.getAbsolutePath(), null);
		}
		if (!newParent.exists() || !newParent.isDirectory()) {
			throw new RuntimeException("Meta File Error, folder does not exist " + newParent.getAbsolutePath(), null);
		}
		File oldFolder = retrieveMetaFolder(parent, oldName);
		File newFolder = retrieveMetaFolder(newParent, newName);
		return oldFolder.renameTo(newFolder);
	}

	/**
	 * Move the MetaFile in the provided parent folder to a new parent folder
	 * 
	 * @param parent
	 *            The parent folder.
	 * @param oldName
	 *            The old name of the MetaFile
	 * @param newParent
	 *            The new name of parent folder
	 * @param newName
	 *            The new name of the MetaFile
	 * @return true if the move was successful.
	 */
	public static boolean moveMetaFile(File parent, String oldName, File newParent, String newName) {
		if (!isMetaFile(parent, oldName)) {
			throw new RuntimeException("Meta File Error, file " + oldName + " not found in folder " + parent.toString(), null);
		}
		if (!newParent.exists() || !newParent.isDirectory()) {
			throw new RuntimeException("Meta File Error, folder does not exist " + newParent.toString(), null);
		}
		if (isMetaFile(newParent, newName)) {
			throw new RuntimeException("Meta File Error, cannot move, file " + newName + " already exists in folder " + newParent.toString(), null);
		}
		File oldMetaFile = retrieveMetaFile(parent, oldName);
		File newMetaFile = retrieveMetaFile(newParent, newName);
		return oldMetaFile.renameTo(newMetaFile);
	}

}
