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

import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;

/**
 * A concrete implementation of the {@link ITaskService}.
 */
public class TaskService implements ITaskService {
	TaskStore store;

	public TaskService(IPath baseLocation) {
		store = new TaskStore(baseLocation.toFile());
	}

	public TaskInfo createTask(String userId) {
		TaskInfo task = new TaskInfo(userId, new UniversalUniqueIdentifier().toBase64String());
		store.writeTask(userId, task.getTaskId(), task.toJSON().toString());
		return task;
	}

	public TaskInfo getTask(String userId, String id) {
		String taskString = store.readTask(userId, id);
		if (taskString == null)
			return null;
		return TaskInfo.fromJSON(taskString);
	}

	public void updateTask(TaskInfo task) {
		store.writeTask(task.getUserId(), task.getTaskId(), task.toJSON().toString());
	}

	public List<TaskInfo> getTasks(String userId) {
		// TODO Auto-generated method stub
		return null;
	}

}
