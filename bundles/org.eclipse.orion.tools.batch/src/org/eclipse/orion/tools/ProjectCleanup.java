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
	private boolean computeSize = false;
	private boolean help = false;
	private boolean purge = false;

	public static void main(String[] arguments) {
		new ProjectCleanup(arguments).run();
	}

	public ProjectCleanup(String[] arguments) {
		parseArgs(arguments);
	}

	/**
	 * Returns the recursive size of a project location.
	 */
	private int computeSize(File projectLocation, int i) {
		return 0;
	}

	/**
	 * Deletes all files at a given location.
	 * @return <code>true</code> if the files were deleted, and <code>false</code> otherwise.
	 */
	private boolean deleteFiles(File location) {
		return false;
		//		File[] children = location.listFiles();
		//		if (children != null)
		//			for (File child : children)
		//				deleteFiles(child);
		//		return location.delete();

	}

	/**
	 * Returns a set of all known projects.
	 */
	private Set<String> findAllProjects() throws IOException {
		Set<String> allProjects = new HashSet<String>();
		Properties projects = new Properties();
		projects.load(new BufferedInputStream(new FileInputStream(new File(PROJECT_PREFS))));
		for (Object key : projects.keySet()) {
			String keyString = (String) key;
			String[] splits = keyString.split("/");
			if (splits.length == 2) {
				String id = splits[0];
				allProjects.add(id);
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
		props.load(new BufferedInputStream(new FileInputStream(new File(WORKSPACE_PREFS))));
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

	private void parseArgs(String[] arguments) {
		for (String arg : arguments) {
			if ("-purge".equals(arg))
				purge = true;
			else if ("-size".equals(arg))
				computeSize = true;
			else if ("-help".equals(arg) || "-?".equals(arg))
				help = true;
		}
	}

	public void run() {
		if (help) {
			System.out.println("Usage: ProjectCleanup [-help] [-size] [-purge]");
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
	 * List and/or delete projects that are not in user workspaces.
	 */
	private void sweepProjects(Set<String> markSet) throws IOException {
		Set<String> allProjects = findAllProjects();
		System.out.println("Mark set: " + markSet);
		System.out.println("All projects: " + allProjects);
		Set<String> deletedProjects = new HashSet<String>();
		for (String project : allProjects) {
			if (!markSet.contains(project)) {
				File projectLocation = new File(project).getAbsoluteFile();
				System.out.print("Found unused project: " + projectLocation);
				if (computeSize) {
					int size = computeSize(projectLocation, 0);
					System.out.print(" size: " + size + " bytes");
				}
				if (purge) {
					System.out.print(" Deleting... ");
					boolean success = deleteFiles(projectLocation);
					System.out.print(success ? "Failed!" : "Done!");
					deletedProjects.add(project);
				}
				//start a new line for the next project
				System.out.println();
			}
		}
	}
}
