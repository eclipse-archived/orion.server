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

public class SimpleLinuxMetaStoreUtil {

	private static final String REGEX = "^[a-z][a-z0-9_-]*$";
	private static final Pattern PATTERN = Pattern.compile(REGEX);
	private static final int MINIMUM_LENGTH = 1;
	private static final int MAXIMUM_LENGTH = 31;
	public static final String ROOT = "metastore";
	public static final String SEPARATOR = "-";
	public static final String METAFILE_EXTENSION = ".json";

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

	public static boolean createMetaStoreRoot(File parent, JSONObject jsonObject) {
		if (!createMetaFolder(parent, ROOT)) {
			throw new RuntimeException("Meta File Error, cannot create root folder " + ROOT + " under " + parent.getAbsolutePath());
		}
		File metaStoreRootFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(parent, ROOT);
		return createMetaFile(metaStoreRootFolder, ROOT, jsonObject);
	}

	public static String decodeProjectNameFromProjectId(String projectId) {
		return projectId;
	}

	public static String decodeUserNameFromWorkspaceId(String workspaceId) {
		return workspaceId.substring(0, workspaceId.indexOf(SEPARATOR));
	}

	public static String decodeWorkspaceNameFromWorkspaceId(String workspaceId) {
		return workspaceId.substring(workspaceId.indexOf(SEPARATOR) + 1);
	}

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

	public static boolean deleteMetaStoreRoot(File parent) {
		File metaStoreRootFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(parent, ROOT);
		return deleteMetaFile(metaStoreRootFolder, ROOT);
	}

	public static String encodeProjectId(String projectName) {
		return projectName;
	}

	public static String encodeWorkspaceId(String userName, String workspaceName) {
		return userName + SEPARATOR + workspaceName;
	}

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

	public static boolean isMetaStoreRoot(File parent) {
		File metaStoreRootFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(parent, ROOT);
		return isMetaFile(metaStoreRootFolder, ROOT);
	}

	/**
	 * Determines if the name is a valid Linux username.
	 * @param name The name to validate.
	 * @return true if the if the name is a valid Linux username.
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

	public static File retrieveMetaFile(File parent, String name) {
		return new File(parent, name + ".json");
	}

	public static File retrieveMetaFolder(File parent, String name) {
		return new File(parent, name);
	}

	public static JSONObject retrieveMetaStoreRootJSON(File parent) {
		if (isMetaStoreRoot(parent)) {
			File metaStoreRootFolder = SimpleLinuxMetaStoreUtil.retrieveMetaFolder(parent, ROOT);
			return retrieveMetaFileJSON(metaStoreRootFolder, ROOT);
		}
		return null;
	}

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
