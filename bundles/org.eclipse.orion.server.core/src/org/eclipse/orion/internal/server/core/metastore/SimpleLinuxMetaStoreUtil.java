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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.orion.server.core.resources.Base64;
import org.json.JSONException;
import org.json.JSONObject;

public class SimpleLinuxMetaStoreUtil {

	private static final String REGEX = "^[a-z][a-z0-9_-]*$";
	private static final Pattern PATTERN = Pattern.compile(REGEX);
	private static final int MINIMUM_LENGTH = 1;
	private static final int MAXIMUM_LENGTH = 31;
	public static final String ROOT = "metastore";

	public static boolean createMetaFile(URI parent, String name, JSONObject jsonObject) {
		try {
			if (!isNameValid(name)) {
				throw new RuntimeException("Meta File Error, name does not follow naming rules " + name);
			}
			if (isMetaFile(parent, name)) {
				throw new RuntimeException("Meta File Error, already exists, use update");
			}
			File parentFile = new File(parent);
			if (!parentFile.exists()) {
				throw new RuntimeException("Meta File Error, parent folder does not exist");
			}
			if (!parentFile.isDirectory()) {
				throw new RuntimeException("Meta File Error, parent is not a folder");
			}
			URI fileURI = retrieveMetaFileURI(parent, name);
			File newFile = new File(fileURI);
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

	public static boolean createMetaFolder(URI parent, String name) {
		if (!isNameValid(name)) {
			throw new RuntimeException("Meta File Error, name does not follow naming rules " + name);
		}
		File parentFolder = new File(parent);
		if (!parentFolder.exists()) {
			throw new RuntimeException("Meta File Error, parent folder does not exist");
		}
		if (!parentFolder.isDirectory()) {
			throw new RuntimeException("Meta File Error, parent is not a folder");
		}
		File newFolder = new File(parentFolder, name);
		if (newFolder.exists()) {
			throw new RuntimeException("Meta File Error, folder already exists");
		}
		if (!newFolder.mkdir()) {
			throw new RuntimeException("Meta File Error, cannot create folder");
		}
		return true;
	}

	public static boolean createMetaStoreRoot(URI parent, JSONObject jsonObject) {
		return createMetaFile(parent, ROOT, jsonObject);
	}

	public static String decodeProjectNameFromProjectId(String workspaceId) {
		byte[] decoded = Base64.decode(workspaceId.getBytes());
		String decodedString = new String(decoded);
		return decodedString.substring(decodedString.lastIndexOf(':') + 1);
	}

	public static String decodeUserNameFromProjectId(String workspaceId) {
		byte[] decoded = Base64.decode(workspaceId.getBytes());
		String decodedString = new String(decoded);
		return decodedString.substring(0, decodedString.indexOf(':'));
	}

	public static String decodeUserNameFromUserId(String userId) {
		byte[] decoded = Base64.decode(userId.getBytes());
		String decodedString = new String(decoded);
		return decodedString.substring(0, decodedString.indexOf(':'));
	}

	public static String decodeUserNameFromWorkspaceId(String workspaceId) {
		byte[] decoded = Base64.decode(workspaceId.getBytes());
		String decodedString = new String(decoded);
		return decodedString.substring(0, decodedString.indexOf(':'));
	}

	public static String decodeWorkspaceNameFromProjectId(String workspaceId) {
		byte[] decoded = Base64.decode(workspaceId.getBytes());
		String decodedString = new String(decoded);
		return decodedString.substring(decodedString.indexOf(':') + 1, decodedString.lastIndexOf(':'));
	}

	public static String decodeWorkspaceNameFromWorkspaceId(String workspaceId) {
		byte[] decoded = Base64.decode(workspaceId.getBytes());
		String decodedString = new String(decoded);
		return decodedString.substring(decodedString.indexOf(':') + 1);
	}

	public static boolean deleteMetaFile(URI parent, String name) {
		if (!isMetaFile(parent, name)) {
			throw new RuntimeException("Meta File Error, cannot delete, does not exist.");
		}
		URI fileURI = retrieveMetaFileURI(parent, name);
		File savedFile = new File(fileURI);
		if (!savedFile.delete()) {
			throw new RuntimeException("Meta File Error, cannot delete file.");
		}
		return true;
	}

	public static boolean deleteMetaFolder(URI parent) {
		File parentFolder = new File(parent);
		String[] files = parentFolder.list();
		if (files.length != 0) {
			throw new RuntimeException("Meta File Error, cannot delete, not empty.");
		}
		if (!parentFolder.delete()) {
			throw new RuntimeException("Meta File Error, cannot delete folder.");
		}
		return true;

	}

	public static boolean deleteMetaStoreRoot(URI parent) {
		return deleteMetaFile(parent, ROOT);
	}

	public static String encodeProjectId(String userName, String workspaceName, String projectName) {
		String id = userName + ":" + workspaceName + ":" + projectName;
		byte[] encoded = Base64.encode(id.getBytes());
		return new String(encoded);
	}

	public static String encodeUserId(String userName) {
		String id = userName + ":";
		byte[] encoded = Base64.encode(id.getBytes());
		return new String(encoded);
	}

	public static String encodeWorkspaceId(String userName, String workspaceName) {
		String id = userName + ":" + workspaceName;
		byte[] encoded = Base64.encode(id.getBytes());
		return new String(encoded);
	}

	public static boolean isMetaFile(URI parent, String name) {
		File parentFolder = new File(parent);
		if (!parentFolder.exists()) {
			return false;
		}
		if (!parentFolder.isDirectory()) {
			return false;
		}
		URI metaFileURI = retrieveMetaFileURI(parent, name);
		File savedFile = new File(metaFileURI);
		if (!savedFile.exists()) {
			return false;
		}
		if (!savedFile.isFile()) {
			return false;
		}
		return true;
	}

	public static boolean isMetaFolder(URI parent) {
		File parentFolder = new File(parent);
		if (!parentFolder.exists()) {
			return false;
		}
		if (!parentFolder.isDirectory()) {
			return false;
		}
		String metaDataName = ".json";
		for (File file : parentFolder.listFiles()) {
			if (file.isDirectory()) {
				// directory, so continue
				continue;
			}
			if (file.isFile() && file.getName().endsWith(metaDataName)) {
				// meta file, so continue
				continue;
			}
			throw new RuntimeException("Meta File Error, contains invalid metadata:" + parent.toString() + " at " + file.getName());
		}
		return true;
	}

	public static boolean isMetaStoreRoot(URI parent) {
		return isMetaFile(parent, ROOT);
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

	public static List<String> listMetaFiles(URI parent) {
		File parentFile = new File(parent);
		String metaDataName = ".json";
		List<String> savedFiles = new ArrayList<String>();
		for (File file : parentFile.listFiles()) {
			if (file.isDirectory()) {
				// directory, so add to list and continue
				savedFiles.add(file.getName());
				continue;
			}
			if (file.isFile() && file.getName().endsWith(metaDataName)) {
				// meta file, so continue
				continue;
			}
			throw new RuntimeException("Meta File Error, contains invalid metadata:" + parent.toString() + " at " + file.getName());
		}
		return savedFiles;
	}

	public static JSONObject retrieveMetaFile(URI parent, String name) {
		JSONObject jsonObject;
		try {
			if (!isMetaFile(parent, name)) {
				return null;
			}
			URI fileURI = retrieveMetaFileURI(parent, name);
			File savedFile = new File(fileURI);

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

	public static URI retrieveMetaFileURI(URI parent, String name) {
		URI metaFileURI;
		try {
			metaFileURI = new URI(parent.toString() + "/" + name + ".json");
		} catch (URISyntaxException e) {
			throw new RuntimeException("Meta File Error, could not build URI", e);
		}
		return metaFileURI;
	}

	public static URI retrieveMetaFolderURI(URI parent, String name) {
		URI metaFileURI;
		try {
			metaFileURI = new URI(parent.toString() + "/" + name);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Meta File Error, could not build URI", e);
		}
		return metaFileURI;
	}

	public static JSONObject retrieveMetaStoreRoot(URI parent) {
		if (isMetaStoreRoot(parent)) {
			return retrieveMetaFile(parent, ROOT);
		}
		return null;
	}

	public static boolean updateMetaFile(URI parent, String name, JSONObject jsonObject) {
		try {
			if (!isMetaFile(parent, name)) {
				throw new RuntimeException("Meta File Error, cannot delete, does not exist.");
			}
			URI fileURI = retrieveMetaFileURI(parent, name);
			File savedFile = new File(fileURI);

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
