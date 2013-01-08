/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.core.tasks.CorruptedTaskException;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskDoesNotExistException;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.core.tasks.TaskModificationListener;
import org.eclipse.orion.server.core.tasks.TaskOperationException;

/**
 * A concrete implementation of the {@link ITaskService}.
 */
public class TaskService implements ITaskService {

	TaskStore store;
	private Set<TaskModificationListener> taskListeners = new HashSet<TaskModificationListener>();

	private class TaskDeletion {
		public final Date deletionDate;
		public final String taskId;

		public TaskDeletion(Date deletionDate, String taskId) {
			super();
			this.deletionDate = deletionDate;
			this.taskId = taskId;
		}
	}

	private class TasksNotificationJob extends Job {

		private String userId;
		private Date modificationDate;

		public TasksNotificationJob(String userId, Date modificationDate) {
			super("Notyfing task listeners");
			this.userId = userId;
			this.modificationDate = modificationDate;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			synchronized (taskListeners) {
				for (TaskModificationListener listener : taskListeners) {
					listener.tasksModified(userId, modificationDate);
				}
			}
			return Status.OK_STATUS;
		}

	}

	private class DeletedTasksNotificationJob extends Job {

		private String userId;
		private Date deletionDate;

		public DeletedTasksNotificationJob(String userId, Date deletionDate) {
			super("Notyfing task listeners");
			this.userId = userId;
			this.deletionDate = deletionDate;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			synchronized (taskListeners) {
				for (TaskModificationListener listener : taskListeners) {
					listener.tasksDeleted(userId, deletionDate);
				}
			}
			return Status.OK_STATUS;
		}
	}

	public TaskService(IPath baseLocation) {
		store = new TaskStore(baseLocation.toFile());
		cleanUpTasks();
	}

	private void cleanUpTasks() {
		List<TaskDescription> allTasks = store.readAllTasks();
		Calendar monthAgo = Calendar.getInstance();
		monthAgo.add(Calendar.MONTH, -1);
		for (TaskDescription taskDescription : allTasks) {
			TaskInfo task;
			try {
				task = TaskInfo.fromJSON(taskDescription, store.readTask(taskDescription));
				if (task == null) {
					continue;
				}
				if (task.isRunning()) {//mark all running tasks as failed due to server restart
					task.done(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Task could not be completed due to server restart", null));
					updateTask(task);
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
		notifyDeletionListeners(userId, date);
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
		notifyDeletionListeners(userId, date);
	}

	public TaskInfo createTask(String userId, boolean keep) {
		TaskInfo task = new TaskInfo(userId, new UniversalUniqueIdentifier().toBase64String(), keep);
		store.writeTask(new TaskDescription(userId, task.getId(), keep), task.toJSON().toString());
		notifyListeners(userId);
		return task;
	}

	private void notifyListeners(String userId) {
		new TasksNotificationJob(userId, new Date()).schedule();
	}

	private void notifyDeletionListeners(String userId, Date deletionDate) {
		new DeletedTasksNotificationJob(userId, deletionDate).schedule();
	}

	public TaskInfo getTask(String userId, String id, boolean keep) {
		TaskDescription taskDescr = new TaskDescription(userId, id, keep);
		String taskString = store.readTask(taskDescr);
		if (taskString == null)
			return null;
		TaskInfo info;
		try {
			info = TaskInfo.fromJSON(taskDescr, taskString);
			return info;
		} catch (CorruptedTaskException e) {
			LogHelper.log(e);
			store.removeTask(new TaskDescription(userId, id, keep));
		}
		return null;
	}

	public void updateTask(TaskInfo task) {
		store.writeTask(new TaskDescription(task.getUserId(), task.getId(), task.isKeep()), task.toJSON().toString());
		notifyListeners(task.getUserId());
	}

	public List<TaskInfo> getTasks(String userId) {
		List<TaskInfo> tasks = new ArrayList<TaskInfo>();
		for (TaskDescription taskDescr : store.readAllTasks(userId)) {
			TaskInfo info;
			try {
				String taskString = store.readTask(taskDescr);
				if(taskString==null){
					continue; //Task removed in between
				}
				info = TaskInfo.fromJSON(taskDescr, taskString);
				
				tasks.add(info);
			} catch (CorruptedTaskException e) {
				LogHelper.log(e);
				store.removeTask(taskDescr);
			}
		}
		return tasks;
	}

	public void addTaskModyficationListener(TaskModificationListener listener) {
		synchronized (taskListeners) {
			this.taskListeners.add(listener);
		}
	}

	public synchronized void removeTaskModyficationListener(TaskModificationListener listener) {
		synchronized (taskListeners) {
			this.taskListeners.remove(listener);
		}
	}
}
