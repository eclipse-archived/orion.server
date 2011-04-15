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
	 * @return A new task
	 */
	TaskInfo createTask();

	/**
	 * Returns the task with the given task id, or <code>null</code> if no such task exists.
	 * @param id The task id
	 * @return The task, or <code>null</code>
	 */
	TaskInfo getTask(String id);

	/**
	 * Updates the state of the given task within the task service. Any changes
	 * to a task object are only observed by other clients of the task service
	 * after this method has been called.
	 * @param task The task to update
	 */
	void updateTask(TaskInfo task);
}
