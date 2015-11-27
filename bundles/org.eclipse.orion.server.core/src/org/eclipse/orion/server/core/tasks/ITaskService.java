/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.tasks;

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
	 * @param keep <code>false</code> if task is an operation that should be stored for further result checking. Clients will use this value to decrease task persistence
	 * @return A new task
	 */
	TaskInfo createTask(String userId, boolean keep);

	/**
	 * Creates a new task. In its initial state the task is running and 0% complete.
	 * Further changes to the task will not be reflected in the task service until
	 * {@link #updateTask(TaskInfo)} is invoked.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @param keep <code>false</code> if task is an operation that should be stored for further result checking. Clients will use this value to decrease task persistence
	 * @param taskCanceller will be called when task is requested to be cancelled
	 * @return A new task
	 */
	TaskInfo createTask(String userId, boolean keep, ITaskCanceller taskCanceller);

	/**
	 * Returns the task with the given task id, or <code>null</code> if no such task exists.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @param id The task id
	 * @return The task, or <code>null</code>
	 */
	TaskInfo getTask(String userId, String id, boolean keep);

	/**
	 * Returns a list of tasks tracked for given user.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @return a list of tasks owned by the user
	 */
	List<TaskInfo> getTasks(String userId);

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
	public void removeTask(String userId, String id, boolean keep) throws TaskOperationException;
	
	/**
	 * Cancel task.
	 * @param userId id of the user starting the task or if not logged in temporary identifier, for instance a session id
	 * @param id The task id
	 * @throws TaskOperationException thrown when task cannot be removed, for instance task is running 
	 */
	public void cancelTask(String userId, String id, boolean keep) throws TaskOperationException;

}
