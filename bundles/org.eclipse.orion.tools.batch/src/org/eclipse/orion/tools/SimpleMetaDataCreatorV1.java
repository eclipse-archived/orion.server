/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A simple metadata serverworkspace creator for {@code SimpleMetaStoreV1}. Used to reverse engineer
 * version one of the simple metadata storage to be used for migration tests. Tell the user to run:
 * <pre>
 * % cd serverworkspace
 * % find . -type d > directories.txt
 * % find . -type d > files.txt
 * </pre>
 * Once you get these files back, you can copy directories.txt, files.txt and simple_metadata_creator.sh
 * into a folder and run:
 * <pre>
 * % ./simple_metadata_creator.sh
 * </pre>
 * Once you have the framework of the serverworkspace, you can run this SimpleMetaDataCreatorV1 which
 * will fill in the metadata based on the files that exist. You can then start the server and see the projects,
 * and then run the migration.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaDataCreatorV1 {
	
	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("SimpleMetaDataCreator: usage: SimpleMetaDataCreator [serverworkspace]");
		}
		File rootLocation = new File(args[0]);
		if (!rootLocation.exists() || !rootLocation.isDirectory()) {
			System.err.println("SimpleMetaDataCreator: provided serverworkspace does not exist " + args[0]);
		}
		SimpleMetaDataCreatorV1 simpleMetaDataCreator = new SimpleMetaDataCreatorV1();
		simpleMetaDataCreator.doMetaDataCreation(rootLocation);
	}

	private PrintStream creatorLog;

	/**
	 * Close the creator log.
	 */
	private void creatorLogClose() {
		creatorLog.close();
	}

	/** 
	 * Open the creator log, the log is stored at the user provided workspace root.
	 * @param workspaceRoot the workspace root.
	 * @throws FileNotFoundException
	 */
	private void creatorLogOpen(File workspaceRoot) {
		try {
			File metadataFolder = new File(workspaceRoot, ".metadata");
			File logFile = new File(metadataFolder, "creator.log");
			FileOutputStream stream = new FileOutputStream(logFile);
			creatorLog = new PrintStream(stream);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("SimpleMetaStorecreator.creatorLogOpen: could not create creator log.");
		}
	}

	/**
	 * Print a message to the creator log.
	 * @param message the message.
	 */
	private void creatorLogPrint(String message) {
		creatorLog.println(message);
	}

	private void doMetaDataCreation(File rootLocation) {
		creatorLogOpen(rootLocation);
		
		JSONObject rootMetaData = getRootMetaData();
		SimpleMetaStoreUtil.updateMetaFile(rootLocation, SimpleMetaStore.ROOT, rootMetaData);
		File rootMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(rootLocation, SimpleMetaStore.ROOT);
		creatorLogPrint("Updated root metadata file " + rootMetaFile.toString());

		File[] files = rootLocation.listFiles();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.getName().equals(rootMetaFile.getName())) {
				continue;
			} else if (next.getName().equals(".metadata")) {
				// skip the eclipse workspace metadata folder
				continue;
			} else if (next.isDirectory() && next.getName().length() == 2) {
				// process organizational folder "an" in /serverworkspace/an/anthony
				processOrganizationalFolder(next);
			} else {
				creatorLogPrint("ERROR: workspace root contains invalid metadata: orphan folder " + next.toString());
			}
		}
		
		creatorLogClose();
	}

	private JSONObject getProjectMetaData(File folder, String projectName, String workspaceId) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
			jsonObject.put("UniqueId", projectName);
			jsonObject.put("WorkspaceId", workspaceId);
			jsonObject.put("FullName", projectName);
			File contentLocation = new File(folder, projectName);
			jsonObject.put("ContentLocation", contentLocation.toURI().toString());
			jsonObject.put("Properties", new JSONObject());
		} catch (JSONException e) {
			creatorLogPrint("SimpleMetaDataCreator: could not create root metadata: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
		return jsonObject;
	}

	private JSONObject getRootMetaData() {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
		} catch (JSONException e) {
			creatorLogPrint("SimpleMetaDataCreator: could not create root metadata: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
		return jsonObject;
	}

	private JSONObject getUserMetaData(String userId, List<String> workspaceIds) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
			jsonObject.put("UniqueId", userId);
			jsonObject.put("UserName", userId);
			JSONArray array = new JSONArray();
			for (String workspaceId : workspaceIds) {
				array.put(workspaceId);
			}
			jsonObject.put("WorkspaceIds", array);
			jsonObject.put("password", "aF_5df6ZWW0=,x_B1ZiY59Og=");
			jsonObject.put("Properties", new JSONObject());
		} catch (JSONException e) {
			creatorLogPrint("SimpleMetaDataCreator: could not create root metadata: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
		return jsonObject;
	}

	private JSONObject getWorkspaceMetaData(String userId, String workspaceId, String name, List<String> projectNames) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(SimpleMetaStore.ORION_VERSION, SimpleMetaStore.VERSION);
			jsonObject.put("UniqueId", workspaceId);
			jsonObject.put("UserId", userId);
			jsonObject.put("FullName", name);
			JSONArray array = new JSONArray();
			for (String projectName : projectNames) {
				array.put(projectName);
			}
			jsonObject.put("ProjectNames", array);
			jsonObject.put("Properties", new JSONObject());
		} catch (JSONException e) {
			creatorLogPrint("SimpleMetaDataCreator: could not create root metadata: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
		return jsonObject;
	}

	private void processOrganizationalFolder(File folder) {
		File[] files = folder.listFiles();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.isDirectory()) {
				// process user folder "anthony" in /serverworkspace/an/anthony
				processUserFolder(next);
			} else {
				creatorLogPrint("ERROR: organizational folder contains invalid metadata: orphan file " + next.toString()); //$NON-NLS-1$
			}
		}
	}

	private void processProjectFolder(File folder, String projectName, String workspaceId) {
		if (!SimpleMetaStoreUtil.isMetaFile(folder, projectName)) {
			creatorLogPrint("ERROR: project folder contains invalid metadata: orphan folder " + folder.toString()); //$NON-NLS-1$
			return;
		}
		File projectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, projectName);

		JSONObject projectMetaData = getProjectMetaData(folder, projectName, workspaceId);
		SimpleMetaStoreUtil.updateMetaFile(folder, projectName, projectMetaData);
		creatorLogPrint("Updated project metadata file: " + projectMetaFile.toString());
	}

	private void processUserFolder(File folder) {
		if (!SimpleMetaStoreUtil.isMetaFile(folder, SimpleMetaStore.USER)) {
			creatorLogPrint("ERROR: user folder contains invalid metadata: orphan folder " + folder.toString()); //$NON-NLS-1$
			return;
		}

		String userId = folder.getName();
		List<String> workspaceIds = new ArrayList<String>();
		File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, SimpleMetaStore.USER);

		File[] files = folder.listFiles();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.getName().equals(userMetaFile.getName())) {
				continue;
			} else if (next.isDirectory()) {
				// process workspace folder in /serverworkspace/an/anthony
				String workspaceId = processWorkspaceFolder(userId, next);
				if (workspaceId != null) {
					workspaceIds.add(workspaceId);
				}
			} else {
				creatorLogPrint("ERROR: user folder contains invalid metadata: orphan file " + next.toString()); //$NON-NLS-1$
			}
		}

		JSONObject userMetaData = getUserMetaData(userId, workspaceIds);
		SimpleMetaStoreUtil.updateMetaFile(folder, SimpleMetaStore.USER, userMetaData);
		creatorLogPrint("Updated user metadata file: " + userMetaFile.toString());
	}

	private String processWorkspaceFolder(String userId, File folder) {
		if (!SimpleMetaStoreUtil.isMetaFile(folder, SimpleMetaStore.WORKSPACE)) {
			creatorLogPrint("ERROR: workspace folder contains invalid metadata: orphan folder " + folder.toString()); //$NON-NLS-1$
			return null;
		}

		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, folder.getName());
		List<String> foundProjectNames = new ArrayList<String>();
		File workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(folder, SimpleMetaStore.WORKSPACE);

		// process the files looking for project names (project.json)
		File[] files = folder.listFiles();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.isFile()) {
				if (next.getName().equals(workspaceMetaFile.getName())) {
					continue;
				} else if (next.getName().endsWith(SimpleMetaStoreUtil.METAFILE_EXTENSION)) {
					String projectName = next.getName().substring(0, next.getName().length() - SimpleMetaStoreUtil.METAFILE_EXTENSION.length());
					foundProjectNames.add(projectName);
				} else {
					creatorLogPrint("ERROR: workspace folder contains invalid metadata: orphan file " + next.toString()); //$NON-NLS-1$
				}
			}
		}

		// process the folder confirming the project names
		List<String> confirmedProjectNames = new ArrayList<String>();
		for (int i = 0; i < files.length; i++) {
			File next = files[i];
			if (next.isDirectory()) {
				if (foundProjectNames.contains(next.getName())) {
					confirmedProjectNames.add(next.getName());
					foundProjectNames.remove(next.getName());
					processProjectFolder(folder, next.getName(), workspaceId);
				} else {
					creatorLogPrint("ERROR: workspace folder contains invalid metadata: orphan folder " + next.toString()); //$NON-NLS-1$
				}
			}
		}

		for (String projectName : foundProjectNames) {
			creatorLogPrint("ERROR: workspace folder contains invalid metadata: orphan file " + projectName.toString() + SimpleMetaStoreUtil.METAFILE_EXTENSION); //$NON-NLS-1$
		}

		JSONObject workspaceMetaData = getWorkspaceMetaData(userId, workspaceId, folder.getName(), confirmedProjectNames);
		SimpleMetaStoreUtil.updateMetaFile(folder, SimpleMetaStore.WORKSPACE, workspaceMetaData);
		creatorLogPrint("Updated workspace metadata file: " + workspaceMetaFile.toString());
		return workspaceId;
	}

}
