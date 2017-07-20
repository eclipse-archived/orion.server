/*******************************************************************************
 * Copyright (c) 2011, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.core.tasks;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.core.resources.FileLocker;

/**
 * A facility for reading/writing information about long running tasks. This store will need to be reimplemented by
 * different server implementations if they do not support bare file access. This class intentionally does not
 * understand representations of tasks, to make it more easily pluggable in the future.
 */
public class TaskStore {
	private final File root;
	private final IMetaStore metastore;

	private static final String FILENAME_LOCK = ".lock"; //$NON-NLS-1$
	private static final String FILENAME_TEMP = "temp"; //$NON-NLS-1$

	public TaskStore(File root, IMetaStore metastore) {
		this.root = root;
		this.metastore = metastore;
		LogHelper.log(new Status(IStatus.INFO, ServerConstants.PI_SERVER_CORE, "Tasks metadata location is " + root.toString())); //$NON-NLS-1$
		if (!root.exists()) {
			LogHelper.log(new Status(IStatus.INFO, ServerConstants.PI_SERVER_CORE, "Creating tasks folder " + root.toString())); //$NON-NLS-1$
			if (!this.root.mkdirs()) {
				LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Problem creating tasks folder " + root.toString())); //$NON-NLS-1$
			}
		}
	}

	private String getUserTasksDirectory(String userId) {
		return new String(Base64.encode(userId.getBytes()));
	}

	private String getUserName(String userDirectoryName) {
		try {
			return new String(Base64.decode(userDirectoryName.getBytes()));
		} catch (Exception e) {
			return null; // if this is not encoded user name than return null
		}
	}

	/**
	 * Returns a string representation of the task with the given id, or <code>null</code> if no such task exists.
	 * 
	 * @param td
	 *            description of the task to read
	 */
	public String readTask(TaskDescription td) {
		String userId = td.getUserId();
		FileLocker.Lock lock = null;
		try {
			lock = metastore.getUserLock(userId).lock(true);

			File directory = new File(root, getUserTasksDirectory(userId));
			if (!directory.exists())
				return null;

			if (!td.isKeep()) {
				directory = new File(directory, FILENAME_TEMP);
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
			} finally {
				if (reader != null)
					try {
						reader.close();
					} catch (IOException e) {
						LogHelper.log(e);
						return null;
					}
			}
		} catch (IOException e) {
			LogHelper.log(e);
			return null;
		} finally {
			if (lock != null) {
				lock.release();
			}
		}
	}

	/**
	 * Writes task representation
	 * 
	 * @param td
	 *            description of the task to write
	 * @param representation
	 *            string representation or the task
	 */
	public void writeTask(TaskDescription td, String representation) {
		String userId = td.getUserId();

		FileLocker.Lock lock = null;
		try {
			lock = metastore.getUserLock(userId).lock(false);

			File directory = new File(root, getUserTasksDirectory(userId));
			if (!directory.exists()) {
				directory.mkdir();
			}

			if (!td.isKeep()) {
				directory = new File(directory, FILENAME_TEMP);
				if (!directory.exists())
					directory.mkdir();
			}
			File taskFile = new File(directory, td.getTaskId());
			FileWriter writer = new FileWriter(taskFile);
			StringReader reader = new StringReader(representation);
			IOUtilities.pipe(reader, writer, true, true);
		} catch (IOException e) {
			LogHelper.log(e);
		} finally {
			if (lock != null) {
				lock.release();
			}
		}
	}

	/**
	 * Removes given task from the list. This doesn't consider task status, it is caller's responsibility to make sure
	 * if task tracking can be stopped. This function does not stop the task.
	 * 
	 * @param td
	 *            description of the task to remove
	 * @return <code>true</code> if task was removed, <code>false</code> otherwise.
	 */
	public boolean removeTask(TaskDescription td) {
		String userId = td.getUserId();

		FileLocker.Lock lock = null;
		try {
			lock = metastore.getUserLock(userId).lock(false);

			File directory = new File(root, getUserTasksDirectory(userId));
			if (!directory.exists())
				return false;

			if (!td.isKeep()) {
				directory = new File(directory, FILENAME_TEMP);
				if (!directory.exists())
					return false;
			}
			File taskFile = new File(directory, td.getTaskId());
			if (!taskFile.exists())
				return false;

			return taskFile.delete();
		} catch (IOException e) {
			LogHelper.log(e);
			return false;
		} finally {
			if (lock != null) {
				lock.release();
			}
		}
	}

	private void delete(File f) throws IOException {
		if (f.isDirectory()) {
			for (File c : f.listFiles())
				delete(c);
		}

		if (!f.delete())
			LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Cannot delete file " + f.getName())); //$NON-NLS-1$
	}

	public void removeAllTempTasks() {
		File[] children = root.listFiles();
		// listFiles returns null in case of IO exception
		if (children == null)
			return;
		for (File userDirectory : children) {
			if (userDirectory.isDirectory()) {
				removeAllTempTasks(userDirectory);
			}
		}
	}

	private void removeAllTempTasks(File userDirectory) {
		FileLocker.Lock lock = null;
		try {
			String userId = getUserName(userDirectory.getName());
			lock = metastore.getUserLock(userId).lock(false);

			if (!userDirectory.isDirectory())
				return;

			File directory = new File(userDirectory, FILENAME_TEMP);
			if (!directory.exists())
				return;
			delete(directory);
		} catch (IOException e) {
			LogHelper.log(e);
		} finally {
			if (lock != null) {
				lock.release();
			}
		}
	}

	private List<TaskDescription> internalReadAllTasksDescriptions(File userDirectory, boolean includeTempTasks) {
		List<TaskDescription> result = new ArrayList<TaskDescription>();
		String userId = getUserName(userDirectory.getName());
		if (userId == null) {
			return result; // this is not a user directory
		}
		for (File taskFile : userDirectory.listFiles()) {
			if (!taskFile.isFile() || taskFile.getName().equals(FILENAME_LOCK)) {
				continue;
			}
			result.add(new TaskDescription(userId, taskFile.getName(), true));
		}
		if (includeTempTasks) {
			File tempDir = new File(userDirectory, FILENAME_TEMP);
			if (tempDir.exists() && tempDir.isDirectory()) {
				for (File taskFile : tempDir.listFiles()) {
					if (!taskFile.isFile() || taskFile.getName().equals(FILENAME_LOCK)) {
						continue;
					}
					result.add(new TaskDescription(userId, taskFile.getName(), false));
				}
			}
		}
		return result;
	}

	/**
	 * Returns all tasks owned by a given user.
	 * 
	 * @param userId
	 *            id of a user that is an owner of tasks
	 * @return a list of tasks tracked for this user
	 */
	public List<TaskDescription> readAllTasks(String userId) {
		FileLocker.Lock lock = null;
		try {
			lock = metastore.getUserLock(userId).lock(true);
			File userDirectory = new File(root, getUserTasksDirectory(userId));
			if (!userDirectory.exists())
				return new ArrayList<TaskDescription>();

			return internalReadAllTasksDescriptions(userDirectory, false);
		} catch (IOException e) {
			LogHelper.log(e);
			return new ArrayList<TaskDescription>();
		} finally {
			if (lock != null) {
				lock.release();
			}
		}
	}

	public List<TaskDescription> readAllTasks() {
		return readAllTasks(false);
	}

	public List<TaskDescription> readAllTasks(boolean includeTempTasks) {
		List<TaskDescription> result = new ArrayList<TaskDescription>();
		if (root.exists() && root.isDirectory()) {
			for (File userDirectory : root.listFiles()) {
				String userId = getUserName(userDirectory.getName());
				FileLocker.Lock lock = null;
				try {
					lock = metastore.getUserLock(userId).lock(true);
					if (userDirectory.isDirectory()) {
						result.addAll(internalReadAllTasksDescriptions(userDirectory, includeTempTasks));
					}
				} catch (IOException e) {
					LogHelper.log(e);
				} finally {
					if (lock != null) {
						lock.release();
					}
				}
			}
		} else {
			LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Tasks folder is not a directory " + root.toString())); //$NON-NLS-1$
		}
		return result;
	}
}
