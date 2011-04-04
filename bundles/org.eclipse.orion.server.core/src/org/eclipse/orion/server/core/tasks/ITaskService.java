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
	TaskInfo createTask();

	TaskInfo getTask(String id);

	void updateTask(TaskInfo task);
}
