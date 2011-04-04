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
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.server.core.LogHelper;

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

	/**
	 * Returns a string representation of the task with the given id, or <code>null</code>
	 * if no such task exists.
	 */
	public String readTask(String id) {
		File taskFile = new File(root, id);
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

	public void writeTask(String id, String representation) {
		try {
			File taskFile = new File(root, id);
			FileWriter writer = new FileWriter(taskFile);
			StringReader reader = new StringReader(representation);
			IOUtilities.pipe(reader, writer, true, true);
		} catch (IOException e) {
			LogHelper.log(e);
		}
	}
}
