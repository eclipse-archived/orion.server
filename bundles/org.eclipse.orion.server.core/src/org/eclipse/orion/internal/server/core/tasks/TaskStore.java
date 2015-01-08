/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.core.tasks;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.core.resources.Base64;

/**
 * A facility for reading/writing information about long running tasks. This store
 * will need to be reimplemented by different server implementations if they do
 * not support bare file access. This class intentionally does not understand
 * representations of tasks, to make it more easily pluggable in the future.
 */
public class TaskStore {
	private final File root;
	private static final String tempDirectory = "temp"; //$NON-NLS-1$

	public TaskStore(File root) {
		this.root = root;
		this.root.mkdirs();
	}

	private String getUserDirectory(String userId) {
		return new String(Base64.encode(userId.getBytes()));
	}

	private String getUserName(String userDirectoryName) {
		try {
			return new String(Base64.decode(userDirectoryName.getBytes()));
		} catch (Exception e) {
			return null; //if this is not encoded user name than return null
		}
	}

	/**
	 * Returns a string representation of the task with the given id, or <code>null</code>
	 * if no such task exists.
	 * 
	 * @param td description of the task to read
	 */
	public synchronized String readTask(TaskDescription td) {
		File directory = new File(root, getUserDirectory(td.getUserId()));
		if (!directory.exists())
			return null;
		if (!td.isKeep()) {
			directory = new File(directory, tempDirectory);
			if (!directory.exists())
				return null;
		}
		File taskFile = new File(directory, td.getTaskId());
		if (!taskFile.exists())
			return null;
		StringWriter writer;
		FileReader reader = null;
		try {
			reader = new FileReader(taskFile);
			writer = new StringWriter();
			IOUtilities.pipe(reader, writer, true, false);
			return writer.toString();
		} catch (IOException e) {
			LogHelper.log(e);
			return null;
		} finally {
			if (reader != null)
				try {
					reader.close();
				} catch (IOException e) {
					LogHelper.log(e);
					return null;
				}
		}
	}

	/**
	 * Writes task representation
	 * 
	 * @param td description of the task to write
	 * @param representation string representation or the task
	 */
	public synchronized void writeTask(TaskDescription td, String representation) {
		try {
			File directory = new File(root, getUserDirectory(td.getUserId()));
			if (!directory.exists()) {
				directory.mkdir();
			}
			if (!td.isKeep()) {
				directory = new File(directory, tempDirectory);
				if (!directory.exists())
					directory.mkdir();
			}
			File taskFile = new File(directory, td.getTaskId());
			FileWriter writer = new FileWriter(taskFile);
			StringReader reader = new StringReader(representation);
			IOUtilities.pipe(reader, writer, true, true);
		} catch (IOException e) {
			LogHelper.log(e);
		}
	}

	/**
	 * Removes given task from the list. This doesn't consider task status, it is caller's
	 * responsibility to make sure if task tracking can be stopped. This function does not stop the task.
	 * 
	 * @param td description of the task to remove
	 * @return <code>true</code> if task was removed, <code>false</code> otherwise. 
	 */
	public synchronized boolean removeTask(TaskDescription td) {
		File directory = new File(root, getUserDirectory(td.getUserId()));
		if (!directory.exists())
			return false;
		if (!td.isKeep()) {
			directory = new File(directory, tempDirectory);
			if (!directory.exists())
				return false;
		}
		File taskFile = new File(directory, td.getTaskId());
		if (!taskFile.exists())
			return false;
		return taskFile.delete();
	}

	private void delete(File f) throws IOException {
		if (f.isDirectory()) {
			for (File c : f.listFiles())
				delete(c);
		}
		if (!f.delete())
			LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Cannot delete file " + f.getName())); //$NON-NLS-1$

	}

	public synchronized void removeAllTempTasks() {
		File[] children = root.listFiles();
		//listFiles returns null in case of IO exception
		if (children == null)
			return;
		for (File userDirectory : children) {
			if (userDirectory.isDirectory()) {
				removeAllTempTasks(userDirectory);
			}
		}
	}

	private synchronized void removeAllTempTasks(File userDirectory) {
		if (!userDirectory.exists())
			return;
		File directory = new File(userDirectory, tempDirectory);
		if (!directory.exists())
			return;
		try {
			delete(directory);
		} catch (IOException e) {
			LogHelper.log(e);
		}
	}

	private List<TaskDescription> internalReadAllTasksDescriptions(File userDirectory) {
		List<TaskDescription> result = new ArrayList<TaskDescription>();
		String userId = getUserName(userDirectory.getName());
		if (userId == null) {
			return result; // this is not a user directory
		}
		for (File taskFile : userDirectory.listFiles()) {
			if (!taskFile.isFile())
				continue;
			result.add(new TaskDescription(userId, taskFile.getName(), true));
		}
		return result;
	}

	/**
	 * Returns all tasks owned by a given user.
	 * 
	 * @param userId id of a user that is an owner of tasks
	 * @return a list of tasks tracked for this user
	 */
	public synchronized List<TaskDescription> readAllTasks(String userId) {
		File userDirectory = new File(root, getUserDirectory(userId));
		if (!userDirectory.exists())
			return new ArrayList<TaskDescription>();

		return internalReadAllTasksDescriptions(userDirectory);
	}

	public synchronized List<TaskDescription> readAllTasks() {
		List<TaskDescription> result = new ArrayList<TaskDescription>();
		for (File userDirectory : root.listFiles()) {
			if (userDirectory.isDirectory()) {
				result.addAll(internalReadAllTasksDescriptions(userDirectory));
			}
		}
		return result;
	}
}
