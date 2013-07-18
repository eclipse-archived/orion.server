package org.eclipse.orion.internal.server.core.metastore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

	public static boolean createMetaFile(URI parent, String name, JSONObject jsonObject) {
		try {
			if (! isNameValid(name)) {
				throw new IllegalArgumentException("Meta File Error, name does not follow naming rules " + name);
			}
			if (isMetaFile(parent, name)) {
				throw new IllegalArgumentException("Meta File Error, already exists, use update");
			}
			File parentFile = new File(parent);
			if (!parentFile.exists()) {
				throw new IllegalArgumentException("Meta File Error, parent does not exist");
			}
			if (!parentFile.isDirectory()) {
				throw new IllegalArgumentException("Meta File Error, parent is not a directory");
			}
			File directoryFile = new File(parentFile, name);
			if (directoryFile.exists()) {
				throw new IllegalArgumentException("Meta File Error, directory already exists");
			}
			if (!directoryFile.mkdirs()) {
				throw new IllegalArgumentException("Meta File Error, directory create failed");
			}
			URI fileURI = createMetaFileURI(parent, name);
			File newFile = new File(fileURI);
			FileWriter fileWriter = new FileWriter(newFile);
			fileWriter.write(jsonObject.toString());
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Meta File Error, file IO error", e);
		}
		return true;
	}

	public static boolean createMetaStoreRoot(URI parent, JSONObject jsonObject) {
		return createMetaFile(parent, ROOT, jsonObject);
	}

	public static boolean deleteMetaStoreRoot(URI parent) {
		return deleteMetaFile(parent, ROOT);
	}
	
	public static boolean deleteMetaFile(URI parent, String name) {
		if (! isMetaFile(parent, name)) {
			throw new IllegalArgumentException("Meta File Error, cannot delete, does not exist.");
		}
		File parentFile = new File(parent);
		File directoryFile = new File(parentFile, name);
		String[] files = directoryFile.list();
		if (files.length != 1 || ! files[0].equals(name + ".json")) {
			throw new IllegalArgumentException("Meta File Error, cannot delete, not empty.");
		}
		URI fileURI = createMetaFileURI(parent, name);
		File savedFile = new File(fileURI);
		if (!savedFile.delete()) {
			throw new IllegalArgumentException("Meta File Error, cannot delete file.");
		}
		if (!directoryFile.delete()) {
			throw new IllegalArgumentException("Meta File Error, cannot delete directory.");
		}
		return true;
	}

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

	public static URI createMetaFileURI(URI parent, String name) {
		URI metaFileURI;
		try {
			metaFileURI = new URI(parent.toString() + "/" + name + "/" + name + ".json");
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Meta File Error, could not build URI", e);
		}
		return metaFileURI;
	}

	public static boolean isMetaStoreRoot(URI parent) {
		return isMetaFile(parent, ROOT);
	}
	
	public static boolean isMetaFile(URI parent, String name) {
		File parentFile = new File(parent);
		if (!parentFile.exists()) {
			return false;
		}
		if (!parentFile.isDirectory()) {
			return false;
		}
		File directoryFile = new File(parentFile, name);
		if (!directoryFile.exists()) {
			return false;
		}
		if (!directoryFile.isDirectory()) {
			return false;
		}
		URI metaFileURI = createMetaFileURI(parent, name);
		File savedFile = new File(metaFileURI);
		if (!savedFile.exists()) {
			return false;
		}
		if (!savedFile.isFile()) {
			return false;
		}
		return true;
	}

	public static JSONObject retrieveMetaFile(URI parent, String name) {
		JSONObject jsonObject;
		try {
			if (!isMetaFile(parent, name)) {
				return null;
			}
			URI fileURI = createMetaFileURI(parent, name);
			File savedFile = new File(fileURI);

			FileReader fileReader = new FileReader(savedFile);
			char[] chars = new char[(int) savedFile.length()];
			fileReader.read(chars);
			fileReader.close();
			jsonObject = new JSONObject(new String(chars));
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Meta File Error, file IO error", e);
		} catch (JSONException e) {
			throw new IllegalArgumentException("Meta File Error, could not build JSON", e);
		}
		return jsonObject;
	}

	public static JSONObject retrieveMetaStoreRoot(URI parent) {
		if (isMetaStoreRoot(parent)) {
			return retrieveMetaFile(parent, ROOT);
		}
		return null;
	}

	public static boolean updateMetaFile(URI parent, String name, JSONObject jsonObject) {
		try {
			if (! isMetaFile(parent, name)) {
				throw new IllegalArgumentException("Meta File Error, cannot delete, does not exist.");
			}
			URI fileURI = createMetaFileURI(parent, name);
			File savedFile = new File(fileURI);

			FileWriter fileWriter = new FileWriter(savedFile);
			fileWriter.write(jsonObject.toString());
			fileWriter.write("\n");
			fileWriter.flush();
			fileWriter.close();
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("Meta File Error, file not found", e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Meta File Error, file IO error", e);
		}
		return true;
	}
}
