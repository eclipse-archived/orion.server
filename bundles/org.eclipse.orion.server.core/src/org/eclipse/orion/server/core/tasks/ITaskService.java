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
package org.eclipse.orion.server.core.tasks;

import java.util.Date;
import java.util.List;

/**
 * A service that server side components use for registering long running
 * operations. This service provides an HTTP resource representing the current
 * state of the task.
 */
public interface ITaskService {
	/**
	 * Creates a new task. In its initial state the task is running and 0% complete.
	 * Further changes to the task will not be reflected in the task service until
	 * {@link #updateTask(TaskInfo)} is invoked.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @return A new task
	 */
	TaskInfo createTask(String userId);

	/**
	 * Returns the task with the given task id, or <code>null</code> if no such task exists.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @param id The task id
	 * @return The task, or <code>null</code>
	 */
	TaskInfo getTask(String userId, String id);

	/**
	 * Returns a list of tasks tracked for given user.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @return a list of tasks owned by the user
	 */
	List<TaskInfo> getTasks(String userId);
	
	/**
	 * Returns a list of tasks tracked for given user that have been modified since <code>modifiedSince</code> date.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @param modifiedSince a starting date since which modified tasks will be returned
	 * @return a list of tasks owned by the user
	 */
	List<TaskInfo> getTasks(String userId, Date modifiedSince);

	/**
	 * Updates the state of the given task within the task service. Any changes
	 * to a task object are only observed by other clients of the task service
	 * after this method has been called.
	 * @param task The task to update
	 */
	void updateTask(TaskInfo task);

	/**
	 * Removed all completed tasks that belong to given user.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 */
	public void removeCompletedTasks(String userId);

	/**
	 * Remove task from the list. Only completed tasks can be removed.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @param id The task id
	 * @throws TaskOperationException thrown when task cannot be removed, for instance task is running 
	 */
	public void removeTask(String userId, String id) throws TaskOperationException;
	
	/**
	 * Cancels the task.
	 * @throws TaskOperationException if task does not support canceling
	 */
	public void cancelTask(TaskInfo task) throws TaskOperationException;
	
	/**
	 * Registers a listener that is notified when task is updated
	 * @param listener
	 */
	public void addTaskModyficationListener(TaskModificationListener listener);
	
	/**
	 * Unregisters a listener that is notified when task is updated
	 * @param listener
	 */
	public void removeTaskModyficationListener(TaskModificationListener listener);
}
