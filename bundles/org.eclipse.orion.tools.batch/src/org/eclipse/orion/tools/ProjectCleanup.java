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

import java.io.*;
import java.util.*;

/**
 * A command line tool for cleaning up unused projects
 */
public class ProjectCleanup {
	private static final String METADATA_DIR = ".metadata/.plugins/org.eclipse.orion.server.core/.settings/";
	private static final String PROJECT_PREFS = METADATA_DIR + "Projects.prefs";
	private static final String WORKSPACE_PREFS = METADATA_DIR + "Workspaces.prefs";
	private boolean help = false;
	private boolean purge = false;

	public static void main(String[] arguments) {
		new ProjectCleanup(arguments).run();
	}

	public ProjectCleanup(String[] arguments) {
		parseArgs(arguments);
	}

	/**
	 * Returns the recursive size of a file system location.
	 */
	private long computeSize(File file) {
		long size = 0;
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null)
				for (File child : children)
					size += computeSize(child);
		} else {
			size += file.length();
		}
		return size;
	}

	/**
	 * Deletes all files at a given location.
	 * @return <code>true</code> if the files were deleted, and <code>false</code> otherwise.
	 */
	private boolean deleteFiles(File location) {
		File[] children = location.listFiles();
		if (children != null)
			for (File child : children)
				deleteFiles(child);
		//this will fail if any child deletion failed
		return location.delete();
	}

	/**
	 * Remove all metadata for deleted projects from the project metadata properties file.
	 */
	private boolean deleteProjectMetadata(Set<String> deletedProjects) throws IOException {
		Properties projects = new Properties();
		File prefFile = new File(PROJECT_PREFS).getAbsoluteFile();
		BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(prefFile));
		try {
			projects.load(inStream);
		} finally {
			safeClose(inStream);
		}
		for (Iterator<Object> it = projects.keySet().iterator(); it.hasNext();) {
			//each entry is of the form <projectId>/<attributeKey>
			String key = (String) it.next();
			String[] splits = key.split("/");
			if (splits.length == 2) {
				String projectId = splits[0];
				if (deletedProjects.contains(projectId))
					it.remove();
			}
		}
		BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(prefFile));
		try {
			projects.store(outStream, null);
		} finally {
			safeClose(outStream);
		}
		return true;
	}

	/**
	 * Returns a set of all known projects.
	 */
	private Map<String, ProjectInfo> findAllProjects() throws IOException {
		Map<String, ProjectInfo> allProjects = findProjectsInMetadata();
		findProjectsInContentLocation(allProjects);
		return allProjects;
	}

	/**
	 * Finds all projects by scanning the default project content location.
	 */
	private void findProjectsInContentLocation(Map<String, ProjectInfo> allProjects) {
		File root = new File("").getAbsoluteFile();
		//first level is abbreviated user name
		File[] shortNames = root.listFiles();
		if (shortNames == null)
			return;
		for (File shortName : shortNames) {
			if (".metadata".equals(shortName.getName()))
				continue;
			//second level is full user name
			File[] userNames = shortName.listFiles();
			if (userNames == null)
				continue;
			for (File user : userNames) {
				//third level is project id
				File[] projects = user.listFiles();
				if (projects == null)
					continue;
				//add an entry for each project using file name as project id
				for (File project : projects) {
					String id = project.getName();
					if (".metadata".equals(id))
						continue;
					ProjectInfo info = new ProjectInfo(id);
					info.setLocation(project);
					allProjects.put(id, info);
				}
			}
		}
	}

	private Map<String, ProjectInfo> findProjectsInMetadata() throws IOException, FileNotFoundException {
		Map<String, ProjectInfo> allProjects = new HashMap<String, ProjectInfo>();
		Properties projects = new Properties();
		projects.load(new BufferedInputStream(new FileInputStream(new File(PROJECT_PREFS))));
		for (Object key : projects.keySet()) {
			//each entry is of the form <projectId>/<attributeKey>
			String keyString = (String) key;
			String[] splits = keyString.split("/");
			if (splits.length == 2) {
				String id = splits[0];
				ProjectInfo info = allProjects.get(id);
				if (info == null) {
					info = new ProjectInfo(id);
					allProjects.put(id, info);
				}
				if ("ContentLocation".equals(splits[1])) {
					File location;
					String locationString = projects.getProperty(keyString);
					if (locationString.startsWith("file:")) {
						location = new File(locationString.substring(5));
					} else {
						location = new File(locationString).getAbsoluteFile();
					}
					info.setLocation(location);
				}
			}
		}
		return allProjects;
	}

	/**
	 * Returns a list of projects that are currently in user workspaces.
	 */
	private Set<String> markProjects() throws FileNotFoundException, IOException {
		Set<String> markSet = new HashSet<String>();
		Properties props = new Properties();
		File workspacePrefs = new File(WORKSPACE_PREFS).getAbsoluteFile();
		props.load(new BufferedInputStream(new FileInputStream(workspacePrefs)));
		Set<Object> keys = props.keySet();
		for (Object key : keys) {
			//keys are of form Id/Attribute
			String keyString = (String) key;
			String[] splits = keyString.split("/");
			if (splits.length == 2 && "Projects".equals(splits[1])) {
				String projectList = props.getProperty(keyString);
				//break up JSON array into objects
				String[] projects = projectList.split(",");
				for (String each : projects) {
					//break up JSON object into attributes
					String[] attrs = each.split("\"");
					if (attrs.length > 2)
						markSet.add(attrs[3]);
				}
			}
		}
		return markSet;
	}

	/**
	 * Parse the project cleanup application command line arguments.
	 */
	private void parseArgs(String[] arguments) {
		for (String arg : arguments) {
			if ("-purge".equals(arg))
				purge = true;
			else if ("-help".equals(arg) || "-?".equals(arg))
				help = true;
		}
	}

	/**
	 * Executes the project cleanup utility.
	 */
	public void run() {
		if (help) {
			System.out.println("Usage: ProjectCleanup [-help] [-purge]");
			return;
		}
		try {
			Set<String> markSet = markProjects();
			sweepProjects(markSet);
		} catch (IOException e) {
			System.out.println("Failed to cleanup projects due to I/O exception:");
			e.printStackTrace();
		}
	}

	/**
	 * Close a stream, suppressing any secondary exception.
	 */
	private void safeClose(Closeable stream) {
		try {
			stream.close();
		} catch (IOException e) {
			//ignore
		}
	}

	/**
	 * List and/or delete projects that are not in user workspaces.
	 */
	private void sweepProjects(Set<String> markSet) throws IOException {
		Map<String, ProjectInfo> allProjects = findAllProjects();
		System.out.println("Used projects: " + markSet);
		System.out.println("All projects: " + allProjects);
		Set<String> deletedProjects = new HashSet<String>();
		long totalSize = 0;
		for (ProjectInfo project : allProjects.values()) {
			if (!markSet.contains(project.getId())) {
				File projectLocation = project.getLocation();
				System.out.println("Found unused project: " + project.getId() + " Location: " + projectLocation);
				if (projectLocation == null || !projectLocation.exists()) {
					System.out.println("\tNot found on disk");
					deletedProjects.add(project.getId());
					continue;
				}
				long size = computeSize(projectLocation) / 1024L;
				totalSize += size;
				System.out.println("\tSize: " + size + "KB");
				if (purge) {
					System.out.print("\tDeleting files... ");
					boolean success = deleteFiles(projectLocation);
					System.out.println(success ? "Done!" : "Failed!");
					if (success)
						deletedProjects.add(project.getId());
				}
			}
		}
		if (purge && !deletedProjects.isEmpty()) {
			System.out.print("Deleting metadata for removed projects...");
			boolean success = deleteProjectMetadata(deletedProjects);
			System.out.println(success ? "Done!" : "Failed!");
		}
		System.out.println("Total unused projects: " + totalSize + "KB");
	}
}