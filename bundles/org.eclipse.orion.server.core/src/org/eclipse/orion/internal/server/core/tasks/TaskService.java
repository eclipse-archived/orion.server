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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.core.tasks.CorruptedTaskException;
import org.eclipse.orion.server.core.tasks.ITaskCanceller;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskDoesNotExistException;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.core.tasks.TaskInfo.TaskStatus;
import org.eclipse.orion.server.core.tasks.TaskOperationException;

/**
 * A concrete implementation of the {@link ITaskService}.
 */
public class TaskService implements ITaskService {

	private TaskStore store;
	private Timer timer;
	private static long TEMP_TASK_LIFE = 15 * 60 * 1000; // 15 minutes in milliseconds
	private Map<TaskDescription, ITaskCanceller> taskCancellers = new HashMap<TaskDescription, ITaskCanceller>();

	private class RemoveTask extends TimerTask {

		private TaskDescription taskDescription;
		private ITaskService taskService;

		public RemoveTask(TaskDescription taskDescription, ITaskService taskService) {
			super();
			this.taskDescription = taskDescription;
			this.taskService = taskService;
		}

		@Override
		public void run() {
			try {
				taskService.removeTask(taskDescription.getUserId(), taskDescription.getTaskId(), taskDescription.isKeep());
			} catch (TaskDoesNotExistException e) {
				// ignore, task was already removed
			} catch (TaskOperationException e) {
				LogHelper.log(e);
			}
		}

	}

	public TaskService(IPath baseLocation) {
		store = new TaskStore(baseLocation.toFile());
		timer = new Timer();
		cleanUpTasks();
	}

	private void cleanUpTasks() {
		store.removeAllTempTasks();
		List<TaskDescription> allTasks = store.readAllTasks();
		for (TaskDescription taskDescription : allTasks) {
			TaskInfo task;
			try {
				task = TaskInfo.fromJSON(taskDescription, store.readTask(taskDescription));
				if (task == null) {
					continue;
				}
				if (task.isRunning()) {// mark all running tasks as failed due to server restart
					task.done(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Task could not be completed due to server restart",
							null));
					updateTask(task);
				} else if (task.getExpires() != null) {
					timer.schedule(new RemoveTask(taskDescription, this), new Date(task.getExpires()));
				}
			} catch (CorruptedTaskException e) {
				LogHelper.log(e);
				store.removeTask(taskDescription);
			}
		}
	}

	private TaskInfo internalRemoveTask(String userId, String id, boolean keep, Date dateRemoved) throws TaskOperationException {
		TaskInfo task = getTask(userId, id, keep);
		if (task == null)
			throw new TaskDoesNotExistException(id);
		if (task.isRunning())
			throw new TaskOperationException("Cannot remove a task that is running. Try to cancel first");
		if (!store.removeTask(new TaskDescription(userId, id, keep)))
			throw new TaskOperationException("Task could not be removed");
		return task;
	}

	public void removeTask(String userId, String id, boolean keep) throws TaskOperationException {
		Date date = new Date();
		internalRemoveTask(userId, id, keep, date);
	}

	public void removeCompletedTasks(String userId) {
		Date date = new Date();
		for (TaskInfo task : getTasks(userId)) {
			if (!task.isRunning()) {
				try {
					internalRemoveTask(userId, task.getId(), task.isKeep(), date);
				} catch (TaskOperationException e) {
					LogHelper.log(e);
				}
			}
		}
	}

	public TaskInfo createTask(String userId, boolean keep) {
		TaskInfo task = new TaskInfo(userId, new UniversalUniqueIdentifier().toBase64String(), keep);
		store.writeTask(new TaskDescription(userId, task.getId(), keep), task.toJSON().toString());
		return task;
	}

	public TaskInfo getTask(String userId, String id, boolean keep) {
		TaskDescription taskDescr = new TaskDescription(userId, id, keep);
		String taskString = store.readTask(taskDescr);
		if (taskString == null)
			return null;
		TaskInfo info;
		try {
			info = TaskInfo.fromJSON(taskDescr, taskString);
			if (taskCancellers.containsKey(taskDescr)) {
				info.setCancelable(true);
			}
			return info;
		} catch (CorruptedTaskException e) {
			LogHelper.log(e);
			store.removeTask(new TaskDescription(userId, id, keep));
		}
		return null;
	}

	public void updateTask(TaskInfo task) {
		TaskDescription taskDescription = new TaskDescription(task.getUserId(), task.getId(), task.isKeep());
		store.writeTask(taskDescription, task.toJSON().toString());
		if (!task.isRunning()) {
			if (task.isKeep()) {
				if (task.getExpires() != null) {
					timer.schedule(new RemoveTask(taskDescription, this), new Date(task.getExpires()));
				}
			} else {
				timer.schedule(new RemoveTask(taskDescription, this), TEMP_TASK_LIFE);
			}
			taskCancellers.remove(taskDescription);
		}
	}

	public List<TaskInfo> getTasks(String userId) {
		List<TaskInfo> tasks = new ArrayList<TaskInfo>();
		for (TaskDescription taskDescr : store.readAllTasks(userId)) {
			TaskInfo info;
			try {
				String taskString = store.readTask(taskDescr);
				if (taskString == null) {
					continue; // Task removed in between
				}
				info = TaskInfo.fromJSON(taskDescr, taskString);
				if (taskCancellers.containsKey(taskDescr)) {
					info.setCancelable(true);
				}

				tasks.add(info);
			} catch (CorruptedTaskException e) {
				LogHelper.log(e);
				store.removeTask(taskDescr);
			}
		}
		return tasks;
	}

	public synchronized TaskInfo createTask(String userId, boolean keep, ITaskCanceller taskCanceller) {
		TaskInfo info = createTask(userId, keep);
		taskCancellers.put(new TaskDescription(info.getUserId(), info.getId(), info.isKeep()), taskCanceller);
		info.setCancelable(true);
		return info;
	}

	public synchronized void cancelTask(String userId, String id, boolean keep) throws TaskOperationException {
		TaskDescription taskDescription = new TaskDescription(userId, id, keep);
		ITaskCanceller taskCanceller = taskCancellers.get(taskDescription);
		if (taskCanceller == null) {
			TaskInfo task = getTask(userId, id, keep);
			if (task == null || task.isRunning() == false) {
				return;
			}
			throw new TaskOperationException("Task does not support cancelling");
		}
		if (!taskCanceller.cancelTask()) {
			throw new TaskOperationException("Cancelling task failed");
		}
		TaskInfo task = getTask(userId, id, keep);
		task.setStatus(TaskStatus.ABORT);
		updateTask(task);
		taskCancellers.remove(taskDescription);
	}
}
