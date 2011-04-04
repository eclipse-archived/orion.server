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

import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;

import org.eclipse.orion.server.core.tasks.TaskInfo;

import org.eclipse.orion.server.core.tasks.ITaskService;

/**
 * A concrete implementation of the {@link ITaskService}.
 */
public class TaskService implements ITaskService {
	TaskStore store;
	public TaskService() {
		store = new TaskStore(null);
	}

	public TaskInfo createTask() {
		TaskInfo task = new TaskInfo(new UniversalUniqueIdentifier().toBase64String());
		store.writeTask(task.getTaskId(), task.toJSON());
		return task;
	}

	public TaskInfo getTask(String id) {
		String taskString = store.readTask(id);
		if (taskString == null)
		return null;
		return TaskInfo.fromJSON(taskString);
	}

	public void updateTask(TaskInfo task) {
	}

}
