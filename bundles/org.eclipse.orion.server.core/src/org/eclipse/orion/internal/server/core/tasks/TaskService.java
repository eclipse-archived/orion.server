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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.core.tasks.ITaskCanceler;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.core.tasks.TaskOperationException;

/**
 * A concrete implementation of the {@link ITaskService}.
 */
public class TaskService implements ITaskService {

	TaskStore store;
	private Map<String, ITaskCanceler> taskCancelers = new HashMap<String, ITaskCanceler>();

	public TaskService(IPath baseLocation) {
		store = new TaskStore(baseLocation.toFile());
	}

	public TaskInfo createTask(String userId) {
		return createTask(userId, null);
	}
	
	public void removeTask(String userId, String id) throws TaskOperationException{
		TaskInfo task = getTask(userId, id);
		if(task==null)
			throw new TaskOperationException("Cannot remove a task that does not exists");
		if(task.isRunning())
			throw new TaskOperationException("Cannot remove a task that is running. Try to cancel first");
		if(!store.removeTask(userId, id))
			throw new TaskOperationException("Task could not be removed");
		taskCancelers.remove(id);
	}
	
	public void removeCompletedTasks(String userId){
		for(TaskInfo task: getTasks(userId)){
			if(!task.isRunning()){
				try {
					removeTask(userId, task.getTaskId());
				} catch (TaskOperationException e) {
					LogHelper.log(e);
				}
			}
		}
	}

	public TaskInfo createTask(String userId, ITaskCanceler taskCanceler) {
		TaskInfo task = new TaskInfo(userId, new UniversalUniqueIdentifier().toBase64String(), taskCanceler);
		taskCancelers.put(task.getTaskId(), taskCanceler);
		store.writeTask(userId, task.getTaskId(), task.toJSON().toString());
		return task;
	}

	public TaskInfo getTask(String userId, String id) {
		String taskString = store.readTask(userId, id);
		if (taskString == null)
			return null;
		return TaskInfo.fromJSON(taskString, taskCancelers.get(id));
	}

	public void updateTask(TaskInfo task) {
		store.writeTask(task.getUserId(), task.getTaskId(), task.toJSON().toString());
	}

	public List<TaskInfo> getTasks(String userId) {
		List<TaskInfo> tasks = new ArrayList<TaskInfo>();
		for (String taskString : store.readAllTasks(userId)) {
			TaskInfo info = TaskInfo.fromJSON(taskString);
			tasks.add(TaskInfo.fromJSON(taskString, taskCancelers.get(info.getTaskId())));
		}
		return tasks;
	}

}
