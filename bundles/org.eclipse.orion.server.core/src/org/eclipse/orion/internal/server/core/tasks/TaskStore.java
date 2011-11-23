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
package org.eclipse.orion.internal.server.core.tasks;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64;

/**
 * A facility for reading/writing information about long running tasks. This store
 * will need to be reimplemented by different server implementations if they do
 * not support bare file access. This class intentionally does not understand
 * representations of tasks, to make it more easily pluggable in the future.
 */
public class TaskStore {
	private final File root;

	public TaskStore(File root) {
		this.root = root;
	}
	
	private String getUserDirectory(String userId){
		return new String(Base64.encode(userId.getBytes()));
	}
	
	public synchronized List<String> readTasks(String userId){
		List<String> result = new ArrayList<String>();
		File userDirectory = new File(root, getUserDirectory(userId));
		if(!userDirectory.exists() || !userDirectory.isDirectory())
			return result;
		for(File taskFile : userDirectory.listFiles()){
			if(taskFile.isFile()){
				try {
					FileReader reader = new FileReader(taskFile);
					StringWriter writer = new StringWriter();
					IOUtilities.pipe(reader, writer, true, false);
					result.add(writer.toString());
				} catch (IOException e) {
					LogHelper.log(e);
				}
			}
		}
		return result;
	}

	/**
	 * Returns a string representation of the task with the given id, or <code>null</code>
	 * if no such task exists.
	 */
	public synchronized String readTask(String userId, String id) {
		File userDirectory = new File(root, getUserDirectory(userId));
		if(!userDirectory.exists())
			return null;
		File taskFile = new File(userDirectory, id);
		if (!taskFile.exists())
			return null;
		StringWriter writer;
		try {
			FileReader reader = new FileReader(taskFile);
			writer = new StringWriter();
			IOUtilities.pipe(reader, writer, true, false);
			return writer.toString();
		} catch (IOException e) {
			LogHelper.log(e);
			return null;
		}
	}

	public synchronized void writeTask(String userId, String id, String representation) {
		root.mkdirs();
		try {
			File userDirectory = new File(root, getUserDirectory(userId));
			if(!userDirectory.exists()){
				userDirectory.mkdir();
			}
			File taskFile = new File(userDirectory, id);
			FileWriter writer = new FileWriter(taskFile);
			StringReader reader = new StringReader(representation);
			IOUtilities.pipe(reader, writer, true, true);
		} catch (IOException e) {
			LogHelper.log(e);
		}
	}
}
