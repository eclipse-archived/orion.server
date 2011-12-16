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
package org.eclipse.orion.internal.server.servlets.task;

import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.*;

/**
 * This registry creates a job per every long-polling status request. If an update to user's task comes the response is send and job is finished.
 * When we no longer want to wait for updates than the job should be cancelled. Canceling the job writes empty updates list to the response.
 *
 */
public class TaskNonotificationRegistry {

	private TaskServlet servlet;
	private ITaskService service;

	private Map<String, TaskListenerJob> listeners = new HashMap<String, TaskListenerJob>();
	private Map<String, Date> lastNodifications = new HashMap<String, Date>();

	private class TaskListenerJob extends Job implements TaskModificationListener {

		private HttpServletRequest req;
		private HttpServletResponse resp;
		private String userId;
		private boolean wasNotified = false;

		public TaskListenerJob(String userId, String longpollingId, HttpServletRequest req, HttpServletResponse resp) {
			super(longpollingId);
			this.req = req;
			this.resp = resp;
			this.userId = userId;
		}

		public String getUserId() {
			return this.userId;
		}

		@Override
		protected synchronized void canceling() {
			wasNotified = true;
			try {
				TaskServlet.writeJSONResponse(req, resp, servlet.getTasksList(new ArrayList<TaskInfo>(), new Date(), req, resp));
			} catch (Exception e) {
				this.done(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
				return;
			}
			listeners.remove(getName());
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			while (!wasNotified)
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
				}
			service.removeTaskModyficationListener(this);
			return Status.OK_STATUS;
		}

		public synchronized void notify(Date timestamp, List<TaskInfo> tasks) {

			wasNotified = true;
			try {
				TaskServlet.writeJSONResponse(req, resp, servlet.getTasksList(tasks, timestamp, req, resp));
				lastNodifications.put(getName(), timestamp);
				listeners.remove(getName());
			} catch (Exception e) {
				this.done(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
				return;
			}

			this.done(Status.OK_STATUS);
		}

		public synchronized void tasksModified(String userId, Date modificationDate) {
			if (!this.userId.equals(userId))
				return;
			notify(modificationDate, service.getTasks(userId, lastNodifications.get(getName()), false));

		}
	}

	public TaskNonotificationRegistry(TaskServlet servlet, ITaskService service) {
		super();
		this.servlet = servlet;
		this.service = service;
	}

	/**
	 * Changes the last update date for the long-polling action.
	 * @param longpollingId must be an id of existing long-polling action. Cannot be <code>null</code>.
	 * @param lastNofication
	 */
	public synchronized void setLastNotification(String longpollingId, Date lastNofication) {
		lastNodifications.put(longpollingId, lastNofication);
	}

	/**
	 * Creates a long-polling job that listens for task changes and when tasks are updated it writes the changes to the response.
	 * @param longpollingId Cannot be <code>null</code>.
	 * @param req
	 * @param resp
	 * @param userId user to tack changes for
	 * @return
	 * @throws ServletException
	 */
	public synchronized Job addListener(String longpollingId, HttpServletRequest req, HttpServletResponse resp, String userId) throws ServletException {
		TaskListenerJob listenerJob = new TaskListenerJob(userId, longpollingId, req, resp);
		boolean notifyNow = true;

		if (listeners.containsKey(longpollingId)) {
			TaskListenerJob currentJob = listeners.get(longpollingId);
			currentJob.cancel();
			if (currentJob.getUserId().equals(userId)) {
				notifyNow = false;
			}
		}
		listeners.put(longpollingId, listenerJob);

		if (notifyNow) {
			Date timestamp = new Date();
			List<TaskInfo> tasks = service.getTasks(userId, lastNodifications.get(longpollingId), false);
			if (!tasks.isEmpty()) {
				listenerJob.notify(timestamp, tasks);
			}
		}

		service.addTaskModyficationListener(listenerJob);
		listenerJob.schedule();
		return listenerJob;
	}
}
