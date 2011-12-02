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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.core.tasks.ITaskCanceler;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.core.tasks.TaskModificationListener;
import org.eclipse.orion.server.core.tasks.TaskOperationException;

/**
 * A concrete implementation of the {@link ITaskService}.
 */
public class TaskService implements ITaskService {

	TaskStore store;
	private Map<String, ITaskCanceler> taskCancelers = new HashMap<String, ITaskCanceler>();
	private Set<TaskModificationListener> taskListeners = new HashSet<TaskModificationListener>();

	public TaskService(IPath baseLocation) {
		store = new TaskStore(baseLocation.toFile());
	}

	public TaskInfo createTask(String taskName, String userId) {
		return createTask(taskName, userId, null);
	}

	public void removeTask(String userId, String id) throws TaskOperationException {
		TaskInfo task = getTask(userId, id);
		if (task == null)
			throw new TaskOperationException("Cannot remove a task that does not exists");
		if (task.isRunning())
			throw new TaskOperationException("Cannot remove a task that is running. Try to cancel first");
		if (!store.removeTask(userId, id))
			throw new TaskOperationException("Task could not be removed");
		taskCancelers.remove(id);
	}

	public void removeCompletedTasks(String userId) {
		for (TaskInfo task : getTasks(userId)) {
			if (!task.isRunning()) {
				try {
					removeTask(userId, task.getTaskId());
				} catch (TaskOperationException e) {
					LogHelper.log(e);
				}
			}
		}
	}

	public TaskInfo createTask(String taskName, String userId, ITaskCanceler taskCanceler) {
		TaskInfo task = new TaskInfo(userId, new UniversalUniqueIdentifier().toBase64String());
		task.setName(taskName);
		if (taskCanceler != null) {
			taskCancelers.put(task.getTaskId(), taskCanceler);
			task.setCanBeCanceled(true);
		}
		store.writeTask(userId, task.getTaskId(), task.toJSON().toString());
		notifyListeners(userId, task.getModified());
		return task;
	}
	
	private synchronized void notifyListeners(String userId, Date modificationDate){
		for(TaskModificationListener listener: taskListeners){
			listener.tasksModified(userId, modificationDate);
		}
	}

	public TaskInfo getTask(String userId, String id) {
		String taskString = store.readTask(userId, id);
		if (taskString == null)
			return null;
		TaskInfo info = TaskInfo.fromJSON(taskString);
		if (taskCancelers.get(id) != null)
			info.setCanBeCanceled(true);
		return info;
	}

	public void updateTask(TaskInfo task) {
		task.setModified(new Date());
		store.writeTask(task.getUserId(), task.getTaskId(), task.toJSON().toString());
		notifyListeners(task.getUserId(), task.getModified());
	}

	public List<TaskInfo> getTasks(String userId) {
		return getTasks(userId, null);
	}

	public void cancelTask(TaskInfo task) throws TaskOperationException {
		ITaskCanceler taskCanceler = taskCancelers.get(task.getTaskId());
		if (taskCanceler == null) {
			throw new TaskOperationException("Task does not support canceling.");
		}
		taskCanceler.cancelTask();
	}

	public List<TaskInfo> getTasks(String userId, Date modifiedSince) {
		List<TaskInfo> tasks = new ArrayList<TaskInfo>();
		for (String taskString : store.readAllTasks(userId)) {
			TaskInfo info = TaskInfo.fromJSON(taskString);
			if (modifiedSince != null) {
				if (info.getModified().getTime() < modifiedSince.getTime()) {
					continue;
				}
			}

			ITaskCanceler taskCanceler = taskCancelers.get(info.getTaskId());
			if (taskCanceler != null)
				info.setCanBeCanceled(true);
			tasks.add(info);
		}
		return tasks;
	}
	
	public synchronized void addTaskModyficationListener(TaskModificationListener listener){
		this.taskListeners.add(listener);
	}
	
	public synchronized void removeTaskModyficationListener(TaskModificationListener listener){
		this.taskListeners.remove(listener);
	}

}
