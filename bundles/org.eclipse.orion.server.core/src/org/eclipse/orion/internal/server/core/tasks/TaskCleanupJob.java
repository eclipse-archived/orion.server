/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.core.tasks;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.tasks.CorruptedTaskException;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A job used to detect and delete left-over tasks.  Left-over temporary tasks are
 * deleted two days after their creation timestamp, and non-temporary tasks are
 * deleted if their expiry date has passed.
 */
public class TaskCleanupJob extends Job {
	private TaskStore store;
	private Logger logger;

	private static final int RESCHEDULE_INTERVAL = 86400 * 2 * 1000; /* run every two days */
	private static final int TEMPTASK_DELETE_THRESHOLD = 86400 * 2 * 1000; /* two days */
	
	public TaskCleanupJob(TaskStore store) {
		super("Orion Task Cleanup"); //$NON-NLS-1$
		this.store = store;
		this.logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		if (logger.isInfoEnabled()) {
			logger.info("Orion task cleanup job started"); //$NON-NLS-1$
		}

		List<TaskDescription> allTasks = store.readAllTasks(true);
		long currentTime = System.currentTimeMillis();
		int removedCount = 0;
		for (TaskDescription taskDescription : allTasks) {
			try {
				TaskInfo task = TaskInfo.fromJSON(taskDescription, store.readTask(taskDescription));
				if (task != null) {
					Long expires = task.getExpires();
					if (expires != null) {
						if (expires < currentTime) {
							store.removeTask(taskDescription);
							removedCount++;
						}
					} else {
						if (!task.isKeep()) {
							Long timestamp = task.getTimestamp();
							if (timestamp != null && TEMPTASK_DELETE_THRESHOLD < currentTime - timestamp) {
								store.removeTask(taskDescription);
								removedCount++;
							}
						}
					}
				}
			} catch (CorruptedTaskException e) {
				LogHelper.log(e);
				store.removeTask(taskDescription);
			}
		}

		logger.info("Orion task cleanup job completed, removed task count: " + removedCount); //$NON-NLS-1$

		schedule(RESCHEDULE_INTERVAL);
		return Status.OK_STATUS;
	}
}
