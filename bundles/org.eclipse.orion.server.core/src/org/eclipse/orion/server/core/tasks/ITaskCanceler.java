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
 * This interface should be passed to the task info if task can be cancelled. It should be implemented by task provider.
 * {@link ITaskCanceler#cancelTask()} will be called when user requests task cancel. It is provider's responsibility
 * to stop the task execution and change the task status to not running.
 *
 */
public interface ITaskCanceler {

	/**
	 * Cancels the task. 
	 */
	void cancelTask();

}
