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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command line tool for cleaning up unused projects
 */
public class ProjectCleanup {
	private static final String METADATA_DIR = ".metadata/.plugins/org.eclipse.orion.server.core/.settings/";
	private static final String PROJECT_PREFS = METADATA_DIR + "Projects.prefs";
	private static final String WORKSPACE_PREFS = METADATA_DIR + "Workspaces.prefs";
	private static final String USER_PREFS = METADATA_DIR + "Users.prefs";
	private boolean help = false;
	private boolean purge = false;
	private boolean permissions = false;

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

	private Map<String, List<String>> findUsersInMetadata() throws IOException {
		//map of users to list of workspaces
		Map<String, List<String>> allUsers = new HashMap<String, List<String>>();
		Properties userProps = new Properties();
		BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(new File(USER_PREFS)));
		try {
			userProps.load(inStream);
		} finally {
			safeClose(inStream);
		}
		// workspace list is of the form [{"Id":"<workspaceId>","LastModified":<number>},...]
		Pattern workspacePattern = Pattern.compile("(\\{\"Id\":\")(\\w)+(\",\"LastModified\":)(\\d)+(\\})");
		for (Object key : userProps.keySet()) {
			//each entry is of the form <userId>/<attributeKey>
			String keyString = (String) key;
			String[] splits = keyString.split("/");
			if (splits.length == 2) {
				String id = splits[0];
				List<String> workspaces = allUsers.get(id);
				if (workspaces == null) {
					workspaces = new ArrayList<String>();
					allUsers.put(id, workspaces);
				}
				if ("Workspaces".equals(splits[1])) {
					Matcher matcher = workspacePattern.matcher(userProps.getProperty(keyString));
					String workspaceList = matcher.replaceAll("$2");
					//remove surrounding square brackets
					workspaceList = workspaceList.substring(1, workspaceList.length() - 1);
					workspaces.addAll(Arrays.asList(workspaceList.split(",")));
				}
			}
		}
		return allUsers;
	}

	private Map<String, ProjectInfo> findProjectsInMetadata() throws IOException, FileNotFoundException {
		Map<String, ProjectInfo> allProjects = new HashMap<String, ProjectInfo>();
		Properties projects = new Properties();
		BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(new File(PROJECT_PREFS)));
		try {
			projects.load(inStream);
		} finally {
			safeClose(inStream);
		}
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
	 * Returns a mapping of workspaces to projects as specified in the workspace metadata.
	 */
	private Map<String, List<String>> findWorkspacesInMetadata() throws FileNotFoundException, IOException {
		Map<String, List<String>> workspacesToProjects = new HashMap<String, List<String>>();
		Properties props = new Properties();
		File workspacePrefs = new File(WORKSPACE_PREFS).getAbsoluteFile();
		props.load(new BufferedInputStream(new FileInputStream(workspacePrefs)));
		Set<Object> keys = props.keySet();
		for (Object key : keys) {
			//keys are of form Id/Attribute
			String keyString = (String) key;
			String[] splits = keyString.split("/");
			if (splits.length == 2 && "Projects".equals(splits[1])) {
				String projectArray = props.getProperty(keyString);
				//break up JSON array into objects
				String[] projects = projectArray.split(",");
				String workspace = splits[0];
				List<String> projectList = new ArrayList<String>();
				for (String each : projects) {
					//break up JSON object into attributes
					String[] attrs = each.split("\"");
					if (attrs.length > 2)
						projectList.add(attrs[3]);
				}
				workspacesToProjects.put(workspace, projectList);
			}
		}
		return workspacesToProjects;
	}

	/**
	 * Returns a list of projects that are currently in user workspaces.
	 */
	private Set<String> markProjects() throws FileNotFoundException, IOException {
		Set<String> allProjects = new HashSet<String>();
		Map<String, List<String>> workspacesToProjects = findWorkspacesInMetadata();
		for (List<String> projectList : workspacesToProjects.values())
			allProjects.addAll(projectList);
		return allProjects;
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
			else if ("-perm".equals(arg)) {
				permissions = true;
			}
		}
	}

	/**
	 * Executes the project cleanup utility.
	 */
	public void run() {
		if (help) {
			System.out.println("Usage: ProjectCleanup [-help] [-purge] [-perm]");
			return;
		}
		try {
			Set<String> markSet = markProjects();
			sweepProjects(markSet);

			Map<String, List<String>> usersToPermissions = markPermissions();
			if (permissions)
				sweepPermissions(usersToPermissions);

		} catch (IOException e) {
			System.out.println("Failed to cleanup projects due to I/O exception:");
			e.printStackTrace();
		}
	}

	private void sweepPermissions(Map<String, List<String>> usersToPermissions) throws IOException {
		Properties userProps = new Properties();
		BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(new File(USER_PREFS)));
		try {
			userProps.load(inStream);
		} finally {
			safeClose(inStream);
		}
		for (String user : usersToPermissions.keySet()) {
			List<String> userPermissions = usersToPermissions.get(user);
			String permissionJSON = "[";
			for (String perm : userPermissions) {
				permissionJSON += "{\"Method\":15,\"Uri\":\"" + perm + "\"},";
			}
			//remove trailing comma
			permissionJSON = permissionJSON.substring(0, permissionJSON.length() - 1) + "]";
			userProps.put(user + "/UserRights", permissionJSON);
		}
		BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(new File(USER_PREFS)));
		try {
			userProps.store(outStream, "Cleaned up properties");
		} finally {
			safeClose(outStream);
		}
	}

	/**
	 * Returns a mapping from users to the permissions they should have based
	 * on their current workspaces and projects.
	 */
	private Map<String, List<String>> markPermissions() throws IOException {
		//map of user name to list of permissions for that user
		Map<String, List<String>> usersToPermissions = new HashMap<String, List<String>>();
		Map<String, List<String>> usersToWorkspaces = findUsersInMetadata();
		for (String user : usersToWorkspaces.keySet()) {
			//don't mess with permissions of administrator
			if (user.equals("admin"))
				continue;
			List<String> newPermissions = new ArrayList<String>();
			//each user has access to their own profile page
			newPermissions.add("/users/" + user);
			//do for each workspace owned by current user
			List<String> workspaceList = usersToWorkspaces.get(user);
			if (workspaceList == null)
				continue;
			for (String workspace : workspaceList) {
				//user has access to their own workspace
				newPermissions.add("/workspace/" + workspace);
				newPermissions.add("/workspace/" + workspace + "/*");
				//access to project contents
				newPermissions.add("/file/" + workspace);
				newPermissions.add("/file/" + workspace + "/*");
			}
			usersToPermissions.put(user, newPermissions);
			System.out.println("New permissions for " + user + ": " + newPermissions);
		}
		return usersToPermissions;
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
		System.out.println("All projects: " + allProjects.keySet());
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