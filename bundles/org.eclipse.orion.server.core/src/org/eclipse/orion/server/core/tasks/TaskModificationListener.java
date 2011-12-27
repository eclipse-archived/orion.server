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

import java.util.Collection;
import java.util.Date;

/**
 * 
 *This listener can be registered to {@link ITaskService} to notify whenever tasks are changed.
 */
public interface TaskModificationListener {

	/**
	 * Called by {@link ITaskService} when tasks are modified.
	 * @param userId id of a user which tasks have been modified.
	 * @param modificationDate the latest task modification date
	 */
	public void tasksModified(String userId, Date modificationDate);
	
	/**
	 * Called {@link ITaskService} when tasks are deleted.
	 * @param tasks a list of tasks that where deleted
	 */
	public void tasksDeleted(String userId, Collection<String> taskIds, Date deletedDate);

}
